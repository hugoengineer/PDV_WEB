package br.com.empresa.pdv.comum;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Aritmética monetária: sempre {@link BigDecimal} com 2 casas e arredondamento comercial (HALF_UP). */
public final class Dinheiro {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal CEM = new BigDecimal("100");

    private Dinheiro() {
    }

    public static BigDecimal arredondar(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    /** {@code base × percentual / 100}, arredondado em centavos. */
    public static BigDecimal percentual(BigDecimal base, BigDecimal percentual) {
        return arredondar(base.multiply(percentual).divide(CEM, 10, RoundingMode.HALF_UP));
    }

    public static BigDecimal valorOuZero(BigDecimal valor) {
        return valor == null ? ZERO : arredondar(valor);
    }
}
