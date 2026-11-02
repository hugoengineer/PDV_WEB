package br.com.empresa.pdv.cupom;

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

import br.com.empresa.pdv.comum.Dinheiro;

@Entity
@Table(name = "cupons_desconto")
public class Cupom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_desconto", nullable = false, length = 12)
    private TipoDesconto tipoDesconto;

    @Column(name = "valor_desconto", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorDesconto;

    @Column(name = "data_inicio_validade", nullable = false)
    private OffsetDateTime dataInicioValidade;

    @Column(name = "data_termino_validade", nullable = false)
    private OffsetDateTime dataTerminoValidade;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;

    protected Cupom() {
    }

    public Cupom(String codigo, OffsetDateTime criadoEm) {
        this.codigo = codigo;
        this.criadoEm = criadoEm;
    }

    /** Vigente = ativo e {@code inicio <= agora <= termino} (os dois limites são inclusivos). */
    public boolean isVigente(OffsetDateTime agora) {
        return ativo && !agora.isBefore(dataInicioValidade) && !agora.isAfter(dataTerminoValidade);
    }

    /** UC06: desconto do cupom sobre o subtotal. Nunca maior que o próprio subtotal. */
    public BigDecimal calcularDesconto(BigDecimal subtotal) {
        BigDecimal desconto = tipoDesconto == TipoDesconto.PERCENTUAL
                ? Dinheiro.percentual(subtotal, valorDesconto)
                : Dinheiro.arredondar(valorDesconto);
        return desconto.min(subtotal);
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public TipoDesconto getTipoDesconto() {
        return tipoDesconto;
    }

    public void setTipoDesconto(TipoDesconto tipoDesconto) {
        this.tipoDesconto = tipoDesconto;
    }

    public BigDecimal getValorDesconto() {
        return valorDesconto;
    }

    public void setValorDesconto(BigDecimal valorDesconto) {
        this.valorDesconto = valorDesconto;
    }

    public OffsetDateTime getDataInicioValidade() {
        return dataInicioValidade;
    }

    public void setDataInicioValidade(OffsetDateTime dataInicioValidade) {
        this.dataInicioValidade = dataInicioValidade;
    }

    public OffsetDateTime getDataTerminoValidade() {
        return dataTerminoValidade;
    }

    public void setDataTerminoValidade(OffsetDateTime dataTerminoValidade) {
        this.dataTerminoValidade = dataTerminoValidade;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(OffsetDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }
}
