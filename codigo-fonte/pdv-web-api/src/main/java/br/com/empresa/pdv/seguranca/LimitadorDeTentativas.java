package br.com.empresa.pdv.seguranca;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conta falhas por chave ("cpf:…", "ip:…", "pin:…") dentro de uma janela de tempo e bloqueia a chave
 * ao atingir o limite. O estado fica no banco: vale após reinícios e entre várias instâncias.
 * A contagem usa um único comando atômico (upsert), então tentativas simultâneas não escapam do limite.
 */
@Service
public class LimitadorDeTentativas {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final PropriedadesTentativas propriedades;

    public LimitadorDeTentativas(JdbcTemplate jdbc, Clock clock, PropriedadesTentativas propriedades) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.propriedades = propriedades;
    }

    public static String chaveCpf(String cpf) {
        return "cpf:" + (cpf == null ? "" : cpf);
    }

    public static String chaveIp(String ip) {
        return "ip:" + (ip == null ? "desconhecido" : ip);
    }

    public static String chaveSenhaGerente(Long operadorId) {
        return "pin:" + operadorId;
    }

    /** Lança 429 se a chave estiver bloqueada. */
    public void verificar(String chave) {
        Instant agora = clock.instant();
        List<Timestamp> bloqueio = jdbc.queryForList(
                "SELECT bloqueado_ate FROM tentativas_acesso WHERE chave = ? AND bloqueado_ate > ?",
                Timestamp.class, chave, Timestamp.from(agora));
        if (!bloqueio.isEmpty()) {
            long segundos = Math.max(1, Duration.between(agora, bloqueio.getFirst().toInstant()).toSeconds());
            throw new TentativasExcedidasException(segundos);
        }
    }

    /**
     * Registra uma falha numa transação própria: a contagem permanece mesmo que a operação que falhou
     * seja desfeita. Devolve {@code true} se esta falha provocou o bloqueio.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registrarFalha(String chave, int maximo) {
        Instant agora = clock.instant();
        Timestamp agoraTs = Timestamp.from(agora);
        Timestamp inicioJanelaValida = Timestamp.from(agora.minus(propriedades.janela()));
        Integer falhas = jdbc.queryForObject("""
                INSERT INTO tentativas_acesso (chave, falhas, janela_inicio) VALUES (?, 1, ?)
                ON CONFLICT (chave) DO UPDATE SET
                    falhas = CASE WHEN tentativas_acesso.janela_inicio < ? THEN 1 ELSE tentativas_acesso.falhas + 1 END,
                    janela_inicio = CASE WHEN tentativas_acesso.janela_inicio < ? THEN EXCLUDED.janela_inicio
                                         ELSE tentativas_acesso.janela_inicio END
                RETURNING falhas""", Integer.class, chave, agoraTs, inicioJanelaValida, inicioJanelaValida);
        if (falhas != null && falhas >= maximo) {
            jdbc.update("UPDATE tentativas_acesso SET bloqueado_ate = ?, falhas = 0, janela_inicio = ? WHERE chave = ?",
                    Timestamp.from(agora.plus(propriedades.bloqueio())), agoraTs, chave);
            return true;
        }
        return false;
    }

    /** Sucesso zera a contagem da chave. */
    public void limpar(String chave) {
        jdbc.update("DELETE FROM tentativas_acesso WHERE chave = ?", chave);
    }

    public PropriedadesTentativas propriedades() {
        return propriedades;
    }

    /** Remove contagens antigas sem bloqueio ativo, para a tabela não crescer indefinidamente. */
    @Scheduled(fixedDelayString = "${pdv.seguranca.intervalo-limpeza:PT1H}",
            initialDelayString = "${pdv.seguranca.intervalo-limpeza:PT1H}")
    public int limparAntigas() {
        Instant agora = clock.instant();
        return jdbc.update("DELETE FROM tentativas_acesso WHERE (bloqueado_ate IS NULL OR bloqueado_ate < ?) "
                + "AND janela_inicio < ?", Timestamp.from(agora), Timestamp.from(agora.minus(propriedades.janela())));
    }
}
