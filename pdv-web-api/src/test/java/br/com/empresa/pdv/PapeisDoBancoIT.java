package br.com.empresa.pdv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.empresa.pdv.suporte.BancoDeTeste;

/**
 * Reproduz o banco do Docker (docker/postgres/papeis.sql + docker-compose.yml) no PostgreSQL embarcado:
 * a API sobe com o papel restrito pdv_app e o Flyway com o dono do esquema pdv_migracao.
 * Comprova que a aplicação funciona com o menor privilégio e que pdv_app não consegue alterar a estrutura.
 * (O PostgreSQL embarcado aceita conexões locais sem senha; a autenticação SCRAM é do servidor do Docker.)
 */
@Tag("integracao")
@DisplayName("Papéis do banco de produção (integração)")
class PapeisDoBancoIT {

    private static final String BANCO = "pdv_web";
    private static final String SENHA_MIGRACAO = "senha-migracao-de-teste";
    private static final String SENHA_APP = "senha-app-de-teste";

    private static ConfigurableApplicationContext api;

    @BeforeAll
    static void criarBancoComoNoDocker() throws Exception {
        try (Connection admin = DriverManager.getConnection(BancoDeTeste.url()); Statement st = admin.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + BANCO);
            st.execute("DROP ROLE IF EXISTS pdv_app");
            st.execute("DROP ROLE IF EXISTS pdv_migracao");
            st.execute("CREATE DATABASE " + BANCO);
        }
        // Mesmo arquivo executado pelo 01-papeis.sh; as variáveis do psql viram literais
        String papeis = Files.readString(Path.of("docker", "postgres", "papeis.sql"), StandardCharsets.UTF_8)
                .replace(":'senha_migracao'", "'" + SENHA_MIGRACAO + "'")
                .replace(":'senha_app'", "'" + SENHA_APP + "'");
        try (Connection admin = conectar("postgres"); Statement st = admin.createStatement()) {
            st.execute(papeis);
        }

        String url = BancoDeTeste.urlSemUsuario(BANCO);
        api = new SpringApplicationBuilder(PdvWebApiApplication.class)
                .profiles("teste")
                // Argumentos de linha de comando: mesma precedência das variáveis do docker-compose
                .run("--spring.datasource.url=" + url,
                        "--spring.datasource.username=pdv_app",
                        "--spring.datasource.password=" + SENHA_APP,
                        "--spring.flyway.url=" + url,
                        "--spring.flyway.user=pdv_migracao",
                        "--spring.flyway.password=" + SENHA_MIGRACAO,
                        "--server.port=0");
    }

    @AfterAll
    static void removerBanco() throws SQLException {
        if (api != null) {
            api.close();
        }
        try (Connection admin = DriverManager.getConnection(BancoDeTeste.url()); Statement st = admin.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + BANCO + " WITH (FORCE)");
            st.execute("DROP ROLE IF EXISTS pdv_app");
            st.execute("DROP ROLE IF EXISTS pdv_migracao");
        }
    }

    @Test
    @DisplayName("CT-DB-011 Com os papéis do Docker, o Flyway (pdv_migracao) cria o esquema e a API (pdv_app) sobe e cria o gerente inicial")
    void apiSobeComMenorPrivilegio() {
        JdbcTemplate jdbc = api.getBean(JdbcTemplate.class);
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("pdv_app");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT tableowner FROM pg_tables WHERE tablename = 'vendas'", String.class))
                .isEqualTo("pdv_migracao");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE perfil = 'GERENTE'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SHOW statement_timeout", String.class)).isEqualTo("30s");
    }

    @Test
    @DisplayName("CT-DB-012 pdv_app lê e grava dados, mas não cria, apaga nem esvazia tabelas")
    void appSemPermissaoDeEstrutura() throws SQLException {
        try (Connection app = conectarComo("pdv_app", SENHA_APP); Statement st = app.createStatement()) {
            st.executeUpdate("INSERT INTO produtos (sku, nome, preco_venda, estoque_atual) VALUES ('PAPEL-1', 'Item', 5.00, 3)");
            st.executeUpdate("UPDATE produtos SET estoque_atual = 2 WHERE sku = 'PAPEL-1'");

            semPrivilegio(() -> st.execute("CREATE TABLE invasora (id INT)"));
            semPrivilegio(() -> st.execute("DROP TABLE produtos"));
            semPrivilegio(() -> st.execute("TRUNCATE logs_auditoria"));
            semPrivilegio(() -> st.execute("ALTER TABLE vendas DISABLE TRIGGER tg_vendas_protegida"));
        }
    }

    @Test
    @DisplayName("CT-DB-013 Usuários sem papel do sistema não conectam ao banco pdv_web")
    void publicoSemAcesso() throws SQLException {
        try (Connection admin = conectar("postgres"); Statement st = admin.createStatement()) {
            st.execute("DROP ROLE IF EXISTS curioso");
            st.execute("CREATE ROLE curioso LOGIN");
        }
        try {
            semPrivilegio(() -> conectarComo("curioso", "").close());
        } finally {
            try (Connection admin = conectar("postgres"); Statement st = admin.createStatement()) {
                st.execute("DROP ROLE curioso");
            }
        }
    }

    private static Connection conectar(String usuario) throws SQLException {
        return conectarComo(usuario, "");
    }

    private static Connection conectarComo(String usuario, String senha) throws SQLException {
        return DriverManager.getConnection(BancoDeTeste.urlSemUsuario(BANCO), usuario, senha);
    }

    /** 42501 = insufficient_privilege (independe do idioma das mensagens do servidor). */
    private static void semPrivilegio(org.assertj.core.api.ThrowableAssert.ThrowingCallable acao) {
        assertThatThrownBy(acao).isInstanceOfSatisfying(SQLException.class,
                e -> assertThat(e.getSQLState()).isEqualTo("42501"));
    }
}
