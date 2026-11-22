package br.com.empresa.pdv.venda;

import java.math.BigDecimal;

/** Resultado do cálculo de uma venda (valores já arredondados em centavos). */
public record ResumoFinanceiro(BigDecimal subtotal, BigDecimal descontoCupom, BigDecimal descontoGerente,
                               BigDecimal total, BigDecimal percentualComissao, BigDecimal comissao) {
}
