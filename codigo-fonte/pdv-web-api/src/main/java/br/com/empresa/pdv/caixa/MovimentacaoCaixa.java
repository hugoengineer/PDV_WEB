package br.com.empresa.pdv.caixa;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "movimentacoes_caixa")
public class MovimentacaoCaixa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "caixa_id", nullable = false)
    private Long caixaId;

    @Column(name = "usuario_gerente_id", nullable = false)
    private Long usuarioGerenteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoMovimentacao tipo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false, length = 255)
    private String observacao;

    @Column(name = "data_hora", nullable = false)
    private OffsetDateTime dataHora;

    protected MovimentacaoCaixa() {
    }

    public MovimentacaoCaixa(Long caixaId, Long usuarioGerenteId, TipoMovimentacao tipo, BigDecimal valor,
                             String observacao, OffsetDateTime dataHora) {
        this.caixaId = caixaId;
        this.usuarioGerenteId = usuarioGerenteId;
        this.tipo = tipo;
        this.valor = valor;
        this.observacao = observacao;
        this.dataHora = dataHora;
    }

    public Long getId() {
        return id;
    }

    public Long getCaixaId() {
        return caixaId;
    }

    public Long getUsuarioGerenteId() {
        return usuarioGerenteId;
    }

    public TipoMovimentacao getTipo() {
        return tipo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public String getObservacao() {
        return observacao;
    }

    public OffsetDateTime getDataHora() {
        return dataHora;
    }
}
