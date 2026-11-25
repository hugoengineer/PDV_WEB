package br.com.empresa.pdv.venda;

/** Modalidades simuladas de recebimento (sem integração TEF nesta fase — Plano de Projeto 1.6). */
public enum ModalidadePagamento {
    DINHEIRO,
    PIX,
    CARTAO_CREDITO,
    CARTAO_DEBITO,
    CREDIARIO,
    /** Só no resumo da venda: o pagamento foi dividido em mais de uma forma (ver venda_pagamentos). */
    MULTIPLA;

    /** Crédito e crediário podem ser parcelados (sem juros); as demais formas são sempre à vista. */
    public boolean permiteParcelamento() {
        return this == CARTAO_CREDITO || this == CREDIARIO;
    }
}
