package br.com.empresa.pdv.venda;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Limite de parcelas sem juros por forma de pagamento, definido pelo gerente (UC17). */
@Entity
@Table(name = "configuracao_parcelamento")
public class ConfiguracaoParcelamento {

    public static final int LIMITE_DO_SISTEMA = 12;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "modalidade_pagamento", length = 15)
    private ModalidadePagamento modalidadePagamento;

    @Column(name = "max_parcelas", nullable = false)
    private short maxParcelas;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    @Column(name = "atualizado_por")
    private Long atualizadoPor;

    protected ConfiguracaoParcelamento() {
    }

    void alterar(int maxParcelas, Long gerenteId, OffsetDateTime quando) {
        this.maxParcelas = (short) maxParcelas;
        this.atualizadoPor = gerenteId;
        this.atualizadoEm = quando;
    }

    public ModalidadePagamento getModalidadePagamento() {
        return modalidadePagamento;
    }

    public int getMaxParcelas() {
        return maxParcelas;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public Long getAtualizadoPor() {
        return atualizadoPor;
    }
}
