package br.com.empresa.pdv.comum;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.com.empresa.pdv.comum.erro.ApiException;

/**
 * Fonte única de data/hora da aplicação, no fuso da loja (America/Sao_Paulo).
 * Injetar o {@link Clock} permite que os testes fixem o "agora" (ex.: vigência de cupons).
 */
@Component
public class Relogio {

    private final Clock clock;
    private final ZoneId fuso;

    public Relogio(Clock clock, @Value("${pdv.fuso-horario}") String fuso) {
        this.clock = clock;
        this.fuso = ZoneId.of(fuso);
    }

    public OffsetDateTime agora() {
        return OffsetDateTime.now(clock.withZone(fuso));
    }

    /** Converte para o fuso da loja (o banco devolve os instantes em UTC). */
    public OffsetDateTime noFuso(OffsetDateTime instante) {
        return instante == null ? null : instante.atZoneSameInstant(fuso).toOffsetDateTime();
    }

    public OffsetDateTime inicioDoDia(LocalDate data) {
        return data == null ? null : data.atStartOfDay(fuso).toOffsetDateTime();
    }

    /** Fim exclusivo: início do dia seguinte. */
    public OffsetDateTime fimDoDiaExclusivo(LocalDate data) {
        return data == null ? null : data.plusDays(1).atStartOfDay(fuso).toOffsetDateTime();
    }

    public static void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio != null && fim != null && fim.isBefore(inicio)) {
            throw ApiException.validacao("data_fim", "Deve ser igual ou posterior a data_inicio.");
        }
    }
}
