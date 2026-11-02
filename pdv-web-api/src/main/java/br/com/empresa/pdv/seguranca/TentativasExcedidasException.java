package br.com.empresa.pdv.seguranca;

import org.springframework.http.HttpStatus;

import br.com.empresa.pdv.comum.erro.ApiException;

/** 429 Too Many Requests, com o tempo de espera devolvido no cabeçalho Retry-After. */
public class TentativasExcedidasException extends ApiException {

    private final long segundosParaNovaTentativa;

    public TentativasExcedidasException(long segundosParaNovaTentativa) {
        super(HttpStatus.TOO_MANY_REQUESTS, "muitas-tentativas", "Muitas Tentativas",
                "Muitas tentativas sem sucesso. Aguarde " + Math.max(1, (segundosParaNovaTentativa + 59) / 60)
                        + " minuto(s) e tente novamente.");
        this.segundosParaNovaTentativa = segundosParaNovaTentativa;
    }

    public long getSegundosParaNovaTentativa() {
        return segundosParaNovaTentativa;
    }
}
