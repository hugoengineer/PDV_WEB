package br.com.empresa.pdv.venda;

/**
 * Sobre qual valor a comissão do vendedor é calculada (configurável em {@code pdv.comissao.base}).
 * O padrão segue o exemplo do contrato da API v2.3 (ADR-001, D-04); a Tabela de Regras 1.1.1.11
 * fala em valor bruto. A equipe escolhe sem alterar código.
 */
public enum BaseComissao {
    /** Valor efetivamente pago (subtotal − descontos). Ex.: R$ 215,00 × 5% = R$ 10,75. */
    LIQUIDO,
    /** Subtotal antes dos descontos. Ex.: R$ 250,00 × 5% = R$ 12,50. */
    BRUTO
}
