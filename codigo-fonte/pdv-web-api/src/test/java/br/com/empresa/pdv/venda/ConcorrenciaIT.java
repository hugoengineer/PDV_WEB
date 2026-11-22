package br.com.empresa.pdv.venda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import br.com.empresa.pdv.suporte.TesteIntegracao;

/**
 * Risco "Consistência de Estoque" do Plano de Projeto (1.9): vendas simultâneas do último item.
 * As requisições são disparadas ao mesmo tempo (CountDownLatch) contra o PostgreSQL real.
 */
@DisplayName("Concorrência de vendas e cancelamentos (integração)")
class ConcorrenciaIT extends TesteIntegracao {

    @RepeatedTest(value = 10, name = "{displayName} [{currentRepetition}/{totalRepetitions}]")
    @DisplayName("CT-CONC-001 20 vendas simultâneas de 1 unidade com estoque 5: exatamente 5 concluídas e estoque 0 (10 repetições)")
    void vendasSimultaneas() throws Exception {
        long produto = criarProduto("CONC-1", "Último lote", "10.00", 5, 0);
        int operadores = 10;
        int requisicoes = 20;
        List<Operador> vendedores = new ArrayList<>();
        List<Long> caixas = new ArrayList<>();
        for (int i = 0; i < operadores; i++) {
            Operador v = criarVendedor("Vendedor " + i, "1");
            vendedores.add(v);
            caixas.add(abrirCaixa(v.token(), "0"));
        }

        List<Integer> statusHttp = executarAoMesmoTempo(requisicoes, i -> () -> {
            int idx = i % operadores;
            return postar("/vendas", vendedores.get(idx).token(), vendaSimples(caixas.get(idx), produto, 1))
                    .andReturn().getResponse().getStatus();
        });

        assertThat(statusHttp).filteredOn(s -> s == 201).hasSize(5);
        assertThat(statusHttp).filteredOn(s -> s == 422).hasSize(15);
        assertThat(estoqueDe(produto)).isZero();
        assertThat(contar("vendas")).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT coalesce(sum(quantidade), 0) FROM itens_venda", Integer.class)).isEqualTo(5);
    }

    @Test
    @DisplayName("CT-CONC-002 Dois cancelamentos simultâneos da mesma venda: um sucesso e o estoque estornado uma única vez")
    void cancelamentosSimultaneos() throws Exception {
        Operador vendedor = criarVendedor("Jean", "1");
        long caixa = abrirCaixa(vendedor.token(), "0");
        long produto = criarProduto("CONC-2", "Item", "10.00", 10, 0);
        long venda = lerId(postar("/vendas", vendedor.token(), vendaSimples(caixa, produto, 4))
                .andExpect(status().isCreated()), "$.venda_id");
        String gerente2 = tokenGerente;

        List<Integer> statusHttp = executarAoMesmoTempo(2, i -> () ->
                postar("/vendas/" + venda + "/cancelar", gerente2, "{\"motivo_cancelamento\": \"Corrida " + i + "\"}")
                        .andReturn().getResponse().getStatus());

        assertThat(statusHttp).containsExactlyInAnyOrder(200, 422);
        assertThat(estoqueDe(produto)).isEqualTo(10);
        assertThat(contarAuditoria("CANCELAMENTO_VENDA")).isEqualTo(1);
    }

    @Test
    @DisplayName("CT-CONC-003 Sangrias simultâneas não deixam o saldo em dinheiro negativo")
    void sangriasSimultaneas() throws Exception {
        Operador vendedor = criarVendedor("Jean", "1");
        long caixa = abrirCaixa(vendedor.token(), "100.00");

        List<Integer> statusHttp = executarAoMesmoTempo(5, i -> () ->
                postar("/caixas/" + caixa + "/sangria", tokenGerente, "{\"valor\": 30.00, \"observacao\": \"Cofre\"}")
                        .andReturn().getResponse().getStatus());

        assertThat(statusHttp).filteredOn(s -> s == 200).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT sum(valor) FROM movimentacoes_caixa", java.math.BigDecimal.class))
                .isEqualByComparingTo("90.00");
    }

    @Test
    @DisplayName("CT-CONC-004 Aberturas de caixa simultâneas pelo mesmo operador: só uma é criada")
    void aberturasSimultaneas() throws Exception {
        Operador vendedor = criarVendedor("Jean", "1");
        List<Integer> statusHttp = executarAoMesmoTempo(4, i -> () ->
                postar("/caixas/abertura", vendedor.token(), "{\"suprimento_inicial\": 10.00}")
                        .andReturn().getResponse().getStatus());

        assertThat(statusHttp).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statusHttp).allMatch(s -> s == 201 || s == 409);
        assertThat(contar("caixas")).isEqualTo(1);
    }

    private static List<Integer> executarAoMesmoTempo(int quantidade, Tarefa tarefa) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(quantidade);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Integer>> futuros = new ArrayList<>();
            for (int i = 0; i < quantidade; i++) {
                Callable<Integer> chamada = tarefa.criar(i);
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return chamada.call();
                }));
            }
            largada.countDown();
            List<Integer> resultados = new ArrayList<>();
            for (Future<Integer> f : futuros) {
                resultados.add(f.get());
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface Tarefa {
        Callable<Integer> criar(int indice);
    }
}
