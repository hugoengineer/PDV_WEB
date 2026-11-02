package br.com.empresa.pdv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import br.com.empresa.pdv.suporte.TesteIntegracao;

/**
 * VAL-07 — consultas de estoque, inclusão de itens e finalização de vendas em menos de 3 segundos.
 * Mede no ambiente de teste (PostgreSQL local) e grava as medições em target/desempenho.csv para o relatório.
 * Não substitui o teste de carga no ambiente Docker/AWS (JMeter), previsto no Plano de Testes.
 */
@Tag("desempenho")
@DisplayName("Desempenho - tempo de resposta (VAL-07)")
class DesempenhoIT extends TesteIntegracao {

    private static final long LIMITE_MS = 3_000;
    private static final Path RELATORIO = Path.of("target", "desempenho.csv");

    @BeforeEach
    void massaDeDados() {
        jdbc.update("""
                INSERT INTO produtos (sku, nome, preco_venda, estoque_atual, estoque_minimo)
                SELECT 'PERF-' || g, 'Produto de carga ' || g, 10.00 + (g % 50), 1000, (g % 7)
                FROM generate_series(1, 1000) AS g""");
    }

    @Test
    @DisplayName("CT-PERF-001 Consulta paginada sobre 1.000 produtos responde em menos de 3 s (p95 de 20 execuções)")
    void consultaPaginada() throws Exception {
        List<Long> tempos = medir(20, () -> obter("/produtos?limite=100&pagina=5", tokenGerente)
                .andExpect(status().isOk()).andExpect(jsonPath("$.total_registros").value(1000)));
        registrar("CT-PERF-001", "GET /produtos (1.000 itens, limite 100)", tempos);
        assertThat(p95(tempos)).isLessThan(LIMITE_MS);
    }

    @Test
    @DisplayName("CT-PERF-002 Busca por nome e filtro de estoque crítico em menos de 3 s")
    void buscaPorNome() throws Exception {
        List<Long> tempos = medir(20, () -> obter("/produtos?nome=carga 99&estoque_critico=false", tokenGerente)
                .andExpect(status().isOk()));
        registrar("CT-PERF-002", "GET /produtos?nome=...", tempos);
        assertThat(p95(tempos)).isLessThan(LIMITE_MS);
    }

    @Test
    @DisplayName("CT-PERF-003 Finalização de venda com 10 itens em menos de 3 s (p95 de 30 vendas)")
    void finalizacaoDeVenda() throws Exception {
        Operador vendedor = criarVendedor("Carga", "5");
        long caixa = abrirCaixa(vendedor.token(), "0");
        StringBuilder itens = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            itens.append(i > 1 ? "," : "").append("{\"produto_id\": ").append(i * 37).append(", \"quantidade\": 2}");
        }
        String corpo = "{\"caixa_id\": " + caixa + ", \"modalidade_pagamento\": \"PIX\", \"itens\": [" + itens + "]}";

        List<Long> tempos = medir(30, () -> postar("/vendas", vendedor.token(), corpo).andExpect(status().isCreated()));
        registrar("CT-PERF-003", "POST /vendas (10 itens)", tempos);
        assertThat(p95(tempos)).isLessThan(LIMITE_MS);
    }

    @Test
    @DisplayName("CT-PERF-004 Consulta de estoque de um produto (leitura do vendedor) em menos de 3 s")
    void consultaUnitaria() throws Exception {
        Operador vendedor = criarVendedor("Leitura", "5");
        List<Long> tempos = medir(30, () -> obter("/produtos/500", vendedor.token()).andExpect(status().isOk()));
        registrar("CT-PERF-004", "GET /produtos/{id}", tempos);
        assertThat(p95(tempos)).isLessThan(LIMITE_MS);
    }

    @Test
    @DisplayName("CT-PERF-005 Carga local: 20 operadores simultâneos — primeira rajada e 400 requisições em regime, todas < 3 s e 0 erros")
    void cargaConcorrente() throws Exception {
        int operadores = 20;
        List<Operador> vendedores = new ArrayList<>();
        List<Long> caixas = new ArrayList<>();
        for (int i = 0; i < operadores; i++) {
            Operador v = criarVendedor("Carga " + i, "5");
            vendedores.add(v);
            caixas.add(abrirCaixa(v.token(), "0"));
        }
        // 1ª rajada simultânea: mede o pior caso logo após a subida (com o aquecimento da inicialização)
        Rodada primeira = rodadaSimultanea(vendedores, caixas, 1);
        registrar("CT-PERF-005a", "Carga: primeira rajada, 20 simultâneos", primeira.tempos());
        // Regime: 10 ciclos por operador
        Rodada regime = rodadaSimultanea(vendedores, caixas, 10);
        registrar("CT-PERF-005b", String.format(java.util.Locale.ROOT, "Carga: regime, 20 simultâneos, 400 req, %.0f req/s",
                regime.tempos().size() / regime.segundos()), regime.tempos());

        assertThat(primeira.erros()).isEmpty();
        assertThat(regime.erros()).isEmpty();
        assertThat(regime.tempos()).hasSize(operadores * 10 * 2);
        assertThat(Collections.max(primeira.tempos())).as("pior requisição da primeira rajada").isLessThan(LIMITE_MS);
        assertThat(Collections.max(regime.tempos())).as("pior requisição em regime").isLessThan(LIMITE_MS);
        assertThat(contar("vendas")).isEqualTo(operadores * 11);
    }

    private record Rodada(List<Long> tempos, List<Integer> erros, double segundos) {
    }

    private Rodada rodadaSimultanea(List<Operador> vendedores, List<Long> caixas, int ciclos) throws Exception {
        int operadores = vendedores.size();
        List<Long> tempos = Collections.synchronizedList(new ArrayList<>());
        List<Integer> erros = Collections.synchronizedList(new ArrayList<>());
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(operadores);
        java.util.concurrent.CountDownLatch largada = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<?>> tarefas = new ArrayList<>();
        long inicioTotal = System.nanoTime();
        for (int i = 0; i < operadores; i++) {
            Operador v = vendedores.get(i);
            long caixa = caixas.get(i);
            long produto = 1 + (i % 1000);
            tarefas.add(pool.submit(() -> {
                largada.await();
                for (int c = 0; c < ciclos; c++) {
                    long t0 = System.nanoTime();
                    int s1 = obter("/produtos?limite=50&nome=carga", v.token()).andReturn().getResponse().getStatus();
                    tempos.add((System.nanoTime() - t0) / 1_000_000);
                    long t1 = System.nanoTime();
                    int s2 = postar("/vendas", v.token(), vendaSimples(caixa, produto, 1)).andReturn().getResponse().getStatus();
                    tempos.add((System.nanoTime() - t1) / 1_000_000);
                    if (s1 != 200) {
                        erros.add(s1);
                    }
                    if (s2 != 201) {
                        erros.add(s2);
                    }
                }
                return null;
            }));
        }
        largada.countDown();
        for (var t : tarefas) {
            t.get();
        }
        pool.shutdown();
        return new Rodada(tempos, erros, (System.nanoTime() - inicioTotal) / 1e9);
    }

    private static List<Long> medir(int vezes, Acao acao) throws Exception {
        acao.executar(); // aquecimento (JIT, cache de planos do PostgreSQL)
        List<Long> tempos = new ArrayList<>();
        for (int i = 0; i < vezes; i++) {
            long inicio = System.nanoTime();
            acao.executar();
            tempos.add((System.nanoTime() - inicio) / 1_000_000);
        }
        return tempos;
    }

    private static long p95(List<Long> tempos) {
        List<Long> ordenados = new ArrayList<>(tempos);
        Collections.sort(ordenados);
        return ordenados.get((int) Math.ceil(0.95 * ordenados.size()) - 1);
    }

    private static void registrar(String id, String operacao, List<Long> tempos) throws IOException {
        List<Long> ordenados = new ArrayList<>(tempos);
        Collections.sort(ordenados);
        double media = tempos.stream().mapToLong(Long::longValue).average().orElse(0);
        String linha = String.format(java.util.Locale.ROOT, "%s;%s;%d;%.1f;%d;%d;%d%n", id, operacao, tempos.size(),
                media, ordenados.get(ordenados.size() / 2), p95(tempos), ordenados.getLast());
        Files.createDirectories(RELATORIO.getParent());
        if (Files.notExists(RELATORIO)) {
            Files.writeString(RELATORIO, "id;operacao;amostras;media_ms;mediana_ms;p95_ms;max_ms\n", StandardCharsets.UTF_8);
        }
        Files.writeString(RELATORIO, linha, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    @FunctionalInterface
    private interface Acao {
        void executar() throws Exception;
    }
}
