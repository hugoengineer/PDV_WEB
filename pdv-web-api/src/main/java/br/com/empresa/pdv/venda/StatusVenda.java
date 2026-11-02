package br.com.empresa.pdv.venda;

/** Ciclo de vida da venda: CONCLUIDA → CANCELADA (somente Gerente). Vendas são imutáveis fora disso. */
public enum StatusVenda {
    CONCLUIDA,
    CANCELADA
}
