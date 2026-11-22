package br.com.empresa.pdv.suporte;

import java.io.IOException;
import java.io.UncheckedIOException;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * PostgreSQL real (binários oficiais) iniciado uma única vez para toda a execução dos testes.
 * Isola os testes do banco de desenvolvimento e dispensa Docker na máquina do desenvolvedor.
 */
public final class BancoDeTeste {

    private static final EmbeddedPostgres POSTGRES = iniciar();

    private BancoDeTeste() {
    }

    private static EmbeddedPostgres iniciar() {
        try {
            EmbeddedPostgres pg = EmbeddedPostgres.builder()
                    .setServerConfig("max_connections", "200")
                    .start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    pg.close();
                } catch (IOException ignorado) {
                    // encerramento da JVM
                }
            }));
            return pg;
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível iniciar o PostgreSQL embarcado", e);
        }
    }

    public static String url() {
        return POSTGRES.getJdbcUrl("postgres", "postgres");
    }

    /** URL sem usuário embutido, para conectar a outro banco do mesmo servidor com outro papel. */
    public static String urlSemUsuario(String banco) {
        return "jdbc:postgresql://localhost:" + POSTGRES.getPort() + "/" + banco;
    }
}
