package br.com.empresa.pdv.venda;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** Venda (cupom). Imutável após o registro, exceto pela transição CONCLUIDA → CANCELADA. */
@Entity
@Table(name = "vendas")
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(name = "caixa_id", nullable = false)
    private Long caixaId;

    @Column(name = "cupom_id")
    private Long cupomId;

    @Column(name = "gerente_autorizador_id")
    private Long gerenteAutorizadorId;

    @Column(name = "valor_subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorSubtotal;

    @Column(name = "desconto_cupom", nullable = false, precision = 10, scale = 2)
    private BigDecimal descontoCupom;

    @Column(name = "desconto_gerente", nullable = false, precision = 10, scale = 2)
    private BigDecimal descontoGerente;

    @Column(name = "valor_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "modalidade_pagamento", nullable = false, length = 15)
    private ModalidadePagamento modalidadePagamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusVenda status = StatusVenda.CONCLUIDA;

    @Column(name = "percentual_comissao", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentualComissao;

    @Column(name = "valor_comissao", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorComissao;

    @Column(name = "data_venda", nullable = false)
    private OffsetDateTime dataVenda;

    @Column(name = "motivo_cancelamento", length = 255)
    private String motivoCancelamento;

    @Column(name = "data_cancelamento")
    private OffsetDateTime dataCancelamento;

    @Column(name = "usuario_cancelamento_id")
    private Long usuarioCancelamentoId;

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ItemVenda> itens = new ArrayList<>();

    protected Venda() {
    }

    Venda(Long usuarioId, Long clienteId, Long caixaId, ModalidadePagamento modalidade, OffsetDateTime dataVenda) {
        this.usuarioId = usuarioId;
        this.clienteId = clienteId;
        this.caixaId = caixaId;
        this.modalidadePagamento = modalidade;
        this.dataVenda = dataVenda;
    }

    void adicionarItem(Long produtoId, int quantidade, BigDecimal precoUnitario) {
        itens.add(new ItemVenda(this, produtoId, quantidade, precoUnitario));
    }

    void aplicarValores(ResumoFinanceiro resumo, Long cupomId, Long gerenteAutorizadorId) {
        this.valorSubtotal = resumo.subtotal();
        this.descontoCupom = resumo.descontoCupom();
        this.descontoGerente = resumo.descontoGerente();
        this.valorTotal = resumo.total();
        this.percentualComissao = resumo.percentualComissao();
        this.valorComissao = resumo.comissao();
        this.cupomId = cupomId;
        this.gerenteAutorizadorId = gerenteAutorizadorId;
    }

    /** UC11: transição de estado. Uma venda cancelada não pode ser cancelada de novo. */
    void cancelar(String motivo, Long gerenteId, OffsetDateTime quando) {
        if (status == StatusVenda.CANCELADA) {
            throw new IllegalStateException("Venda " + id + " já está cancelada.");
        }
        this.status = StatusVenda.CANCELADA;
        this.motivoCancelamento = motivo;
        this.usuarioCancelamentoId = gerenteId;
        this.dataCancelamento = quando;
    }

    public boolean isCancelada() {
        return status == StatusVenda.CANCELADA;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public Long getCaixaId() {
        return caixaId;
    }

    public Long getCupomId() {
        return cupomId;
    }

    public Long getGerenteAutorizadorId() {
        return gerenteAutorizadorId;
    }

    public BigDecimal getValorSubtotal() {
        return valorSubtotal;
    }

    public BigDecimal getDescontoCupom() {
        return descontoCupom;
    }

    public BigDecimal getDescontoGerente() {
        return descontoGerente;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public ModalidadePagamento getModalidadePagamento() {
        return modalidadePagamento;
    }

    public StatusVenda getStatus() {
        return status;
    }

    public BigDecimal getPercentualComissao() {
        return percentualComissao;
    }

    public BigDecimal getValorComissao() {
        return valorComissao;
    }

    public OffsetDateTime getDataVenda() {
        return dataVenda;
    }

    public String getMotivoCancelamento() {
        return motivoCancelamento;
    }

    public OffsetDateTime getDataCancelamento() {
        return dataCancelamento;
    }

    public List<ItemVenda> getItens() {
        return itens;
    }
}
