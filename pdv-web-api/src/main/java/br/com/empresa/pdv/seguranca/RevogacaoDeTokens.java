package br.com.empresa.pdv.seguranca;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tokens encerrados por logout ou renovação, guardados no banco até a expiração original de cada um.
 * Por ficarem no banco (e não em memória), a revogação vale após reinícios e entre várias instâncias.
 */
@Component
public class RevogacaoDeTokens {

    private static final Logger log = LoggerFactory.getLogger(RevogacaoDeTokens.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public RevogacaoDeTokens(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void revogar(String jti, Instant expiraEm) {
        if (jti == null || expiraEm == null) {
            return;
        }
        jdbc.update("INSERT INTO tokens_revogados (jti, expira_em, revogado_em) VALUES (?, ?, ?) "
                + "ON CONFLICT (jti) DO NOTHING", jti, Timestamp.from(expiraEm), Timestamp.from(clock.instant()));
    }

    public boolean estaRevogado(String jti) {
        if (jti == null) {
            return false;
        }
        Boolean existe = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM tokens_revogados WHERE jti = ?)",
                Boolean.class, jti);
        return Boolean.TRUE.equals(existe);
    }

    /** Token expirado já é recusado pela validação de data; o registro de revogação deixa de ser necessário. */
    @Scheduled(fixedDelayString = "${pdv.seguranca.intervalo-limpeza:PT1H}",
            initialDelayString = "${pdv.seguranca.intervalo-limpeza:PT1H}")
    public int limparExpirados() {
        int removidos = jdbc.update("DELETE FROM tokens_revogados WHERE expira_em < ?", Timestamp.from(clock.instant()));
        if (removidos > 0) {
            log.info("{} registro(s) de token revogado expirado(s) removido(s).", removidos);
        }
        return removidos;
    }
}
