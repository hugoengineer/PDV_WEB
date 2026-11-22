package br.com.empresa.pdv.comum;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import br.com.empresa.pdv.produto.ProdutoRepository;
import br.com.empresa.pdv.venda.VendaRepository;

/**
 * Aquece a aplicação antes de ela aceitar tráfego: abre as conexões do pool em paralelo e executa as
 * consultas mais usadas. Os ApplicationRunner terminam antes de a prontidão (/actuator/health/readiness)
 * virar ACCEPTING_TRAFFIC, então o balanceador só envia vendas para uma instância já aquecida.
 * Motivo: no teste de carga, a primeira rajada após a subida levava até ~3,4 s (pool vazio e código frio).
 */
@Component
@Order(0)
public class AquecimentoNaInicializacao implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AquecimentoNaInicializacao.class);

    private final DataSource dataSource;
    private final ProdutoRepository produtos;
    private final VendaRepository vendas;
    private final int conexoes;

    public AquecimentoNaInicializacao(DataSource dataSource, ProdutoRepository produtos, VendaRepository vendas,
                                      @Value("${pdv.aquecimento.conexoes:10}") int conexoes) {
        this.dataSource = dataSource;
        this.produtos = produtos;
        this.vendas = vendas;
        this.conexoes = conexoes;
    }

    @Override
    public void run(ApplicationArguments args) {
        long inicio = System.nanoTime();
        try {
            abrirConexoesEmParalelo();
            for (int i = 0; i < 3; i++) {
                produtos.findAll(PageRequest.of(0, 20)).getContent();
                vendas.somarConcluidasDoCaixa(0L);
            }
            log.info("Aplicação aquecida em {} ms ({} conexões abertas).", (System.nanoTime() - inicio) / 1_000_000,
                    conexoes);
        } catch (Exception e) {
            // O aquecimento é uma otimização: nunca impede a aplicação de subir
            log.warn("Aquecimento incompleto: {}", e.getMessage());
        }
    }

    private void abrirConexoesEmParalelo() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(conexoes);
        try {
            List<Future<Connection>> abertas = new ArrayList<>();
            for (int i = 0; i < conexoes; i++) {
                Callable<Connection> abrir = dataSource::getConnection;
                abertas.add(pool.submit(abrir));
            }
            for (Future<Connection> conexao : abertas) {
                conexao.get().close(); // devolve ao pool, que a mantém aberta
            }
        } finally {
            pool.shutdown();
        }
    }
}
