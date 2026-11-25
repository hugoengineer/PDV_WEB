package br.com.empresa.pdv.venda;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Uma das formas de pagamento usadas na venda. A soma dos valores é igual ao total da venda. */
@Entity
@Table(name = "venda_pagamentos")
public class PagamentoVenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venda_id", nullable = false)
    private Venda venda;

    @Enumerated(EnumType.STRING)
    @Column(name = "modalidade_pagamento", nullable = false, length = 15)
    private ModalidadePagamento modalidadePagamento;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private short parcelas;

    protected PagamentoVenda() {
    }

    PagamentoVenda(Venda venda, ModalidadePagamento modalidade, BigDecimal valor, int parcelas) {
        this.venda = venda;
        this.modalidadePagamento = modalidade;
        this.valor = valor;
        this.parcelas = (short) parcelas;
    }

    /** Valor de cada parcela (sem juros), arredondado para baixo; a diferença de centavos vai na última. */
    public BigDecimal getValorParcela() {
        return valor.divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.DOWN);
    }

    public BigDecimal getValorUltimaParcela() {
        return valor.subtract(getValorParcela().multiply(BigDecimal.valueOf(parcelas - 1L)));
    }

    public ModalidadePagamento getModalidadePagamento() {
        return modalidadePagamento;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public int getParcelas() {
        return parcelas;
    }
}
