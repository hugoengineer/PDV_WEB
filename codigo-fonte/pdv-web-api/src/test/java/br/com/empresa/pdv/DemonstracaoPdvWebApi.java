package br.com.empresa.pdv;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

import javax.sql.DataSource;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * MODO DEMONSTRAÇÃO — sobe a API de verdade (servidor HTTP na porta 8080) com um PostgreSQL embarcado,
 * sem instalar banco nem Docker. Os dados existem só enquanto a aplicação estiver rodando.
 *
 * <pre>
 *   ./mvnw spring-boot:test-run "-Dspring-boot.run.main-class=br.com.empresa.pdv.DemonstracaoPdvWebApi"
 * </pre>
 *
 * Também pode ser executado direto pela IDE (Run nesta classe ou a configuração "PDV Web (demo)").
 * Quando a aplicação fica pronta, o navegador abre na tela de login do front-end (http://localhost:8080).
 * Para não abrir o navegador: PDV_DEMO_ABRIR_NAVEGADOR=false (ou -Dpdv.demo.abrir-navegador=false).
 *
 * Swagger UI: http://localhost:8080/swagger-ui.html — credenciais em src/test/resources/application-demo.yml.
 * Fica no código de teste para nunca ir para o pacote de produção.
 */
public final class DemonstracaoPdvWebApi {

    private DemonstracaoPdvWebApi() {
    }

    public static void main(String[] args) {
        SpringApplication.from(PdvWebApiApplication::main)
                .with(ConfiguracaoDemonstracao.class)
                .withAdditionalProfiles("demo")
                .run(args);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracaoDemonstracao {

        private static final Logger log = LoggerFactory.getLogger(ConfiguracaoDemonstracao.class);

        @Bean(destroyMethod = "close")
        EmbeddedPostgres postgresEmbarcado() {
            try {
                return EmbeddedPostgres.builder().start();
            } catch (IOException e) {
                throw new UncheckedIOException("Não foi possível iniciar o PostgreSQL embarcado", e);
            }
        }

        @Bean
        DataSource dataSource(EmbeddedPostgres postgres) {
            return postgres.getPostgresDatabase();
        }

        /** Massa de exemplo para a apresentação: produtos, um vendedor e um cupom vigente. */
        @Bean
        ApplicationRunner dadosDeDemonstracao(JdbcTemplate jdbc, PasswordEncoder encoder) {
            return args -> {
                jdbc.update("""
                        INSERT INTO produtos (sku, nome, unidade_medida, codigo_barras, preco_venda, estoque_atual, estoque_minimo)
                        VALUES ('PROD-01', 'Camiseta P', 'UN', '7891000000011', 49.90, 10, 2),
                               ('PROD-02', 'Tênis 41', 'UN', '7891000000028', 299.90, 1, 0),
                               ('PROD-03', 'Meia', 'UN', '7891000000035', 15.00, 3, 2),
                               ('PROD-ELE-001', 'Teclado Mecânico RGB', 'UN', '7891234567890', 250.00, 15, 3)
                        ON CONFLICT (sku) DO NOTHING""");
                jdbc.update("""
                        INSERT INTO usuarios (cpf, nome, senha_hash, perfil, percentual_comissao)
                        VALUES ('52998224725', 'Vendedor Demonstração', ?, 'VENDEDOR', 5.00)
                        ON CONFLICT (cpf) DO NOTHING""", encoder.encode("Vendedor@123"));
                jdbc.update("""
                        INSERT INTO cupons_desconto (codigo, tipo_desconto, valor_desconto, data_inicio_validade, data_termino_validade)
                        VALUES ('DESCONTO10', 'PERCENTUAL', ?, ?, ?) ON CONFLICT (codigo) DO NOTHING""",
                        new BigDecimal("10.00"), OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(30));
                log.info("""

                        ============================================================
                         PDV Web em MODO DEMONSTRAÇÃO
                         Front-end  : http://localhost:8080
                         Swagger UI : http://localhost:8080/swagger-ui.html
                         Gerente    : CPF 111.444.777-35   senha Gerente@Demo1
                         Vendedor   : CPF 529.982.247-25   senha Vendedor@123
                         Cupom      : DESCONTO10 (10%)
                         Dados apagados ao encerrar (Ctrl+C)
                        ============================================================""");
            };
        }

        /** Abre o navegador na tela de login assim que a aplicação estiver pronta para receber requisições. */
        @Bean
        ApplicationListener<ApplicationReadyEvent> abrirNavegador(Environment ambiente) {
            return evento -> {
                if (!ambiente.getProperty("pdv.demo.abrir-navegador", Boolean.class, true)) {
                    return;
                }
                String url = "http://localhost:" + ambiente.getProperty("local.server.port", "8080") + "/";
                String sistema = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
                List<String> comando = sistema.contains("win") ? List.of("rundll32", "url.dll,FileProtocolHandler", url)
                        : sistema.contains("mac") ? List.of("open", url)
                        : List.of("xdg-open", url);
                try {
                    new ProcessBuilder(comando).start();
                    log.info("Navegador aberto em {}", url);
                } catch (IOException e) {
                    log.info("Abra no navegador: {}", url);
                }
            };
        }
    }
}
