package br.com.empresa.pdv.cupom;

public enum TipoDesconto {
    /** {@code valor_desconto} é um percentual (0,01 a 100) aplicado sobre o subtotal. */
    PERCENTUAL,
    /** {@code valor_desconto} é um valor em R$, limitado ao subtotal. */
    VALOR_FIXO
}
