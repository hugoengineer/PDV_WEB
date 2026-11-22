package br.com.empresa.pdv.comum;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import br.com.empresa.pdv.comum.erro.ApiException;

/** Converte os parâmetros {@code pagina} (1-based) e {@code limite} do contrato em {@link Pageable}. */
public final class Paginacao {

    public static final int LIMITE_PADRAO = 20;
    public static final int LIMITE_MAXIMO = 100;

    private Paginacao() {
    }

    public static Pageable de(Integer pagina, Integer limite, Sort ordenacao) {
        int p = pagina == null ? 1 : pagina;
        int l = limite == null ? LIMITE_PADRAO : limite;
        if (p < 1) {
            throw ApiException.validacao("pagina", "Deve ser maior ou igual a 1.");
        }
        if (l < 1 || l > LIMITE_MAXIMO) {
            throw ApiException.validacao("limite", "Deve estar entre 1 e " + LIMITE_MAXIMO + ".");
        }
        return PageRequest.of(p - 1, l, ordenacao);
    }
}
