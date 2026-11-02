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

/** Sessão de caixa de um operador (turno). Estados: ABERTO → FECHADO (sem reabertura). */
@Entity
@Table(name = "caixas")
public class Caixa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 7)
    private StatusCaixa status = StatusCaixa.ABERTO;

    @Column(name = "suprimento_inicial", nullable = false, precision = 10, scale = 2)
    private BigDecimal suprimentoInicial;

    @Column(name = "data_abertura", nullable = false)
    private OffsetDateTime dataAbertura;

    @Column(name = "data_fechamento")
    private OffsetDateTime dataFechamento;

    @Column(name = "valor_final_informado", precision = 10, scale = 2)
    private BigDecimal valorFinalInformado;

    @Column(name = "saldo_esperado", precision = 10, scale = 2)
    private BigDecimal saldoEsperado;

    @Column(precision = 10, scale = 2)
    private BigDecimal diferenca;

    protected Caixa() {
    }

    public Caixa(Long usuarioId, BigDecimal suprimentoInicial, OffsetDateTime dataAbertura) {
        this.usuarioId = usuarioId;
        this.suprimentoInicial = suprimentoInicial;
        this.dataAbertura = dataAbertura;
    }

    public boolean isAberto() {
        return status == StatusCaixa.ABERTO;
    }

    public boolean pertenceA(Long idUsuario) {
        return usuarioId.equals(idUsuario);
    }

    /** UC03: registra a contagem do operador e a diferença (positiva = sobra, negativa = falta). */
    public void fechar(BigDecimal valorInformado, BigDecimal saldoEsperadoDinheiro, OffsetDateTime quando) {
        if (!isAberto()) {
            throw new IllegalStateException("Caixa " + id + " já está fechado.");
        }
        this.status = StatusCaixa.FECHADO;
        this.valorFinalInformado = valorInformado;
        this.saldoEsperado = saldoEsperadoDinheiro;
        this.diferenca = valorInformado.subtract(saldoEsperadoDinheiro);
        this.dataFechamento = quando;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public StatusCaixa getStatus() {
        return status;
    }

    public BigDecimal getSuprimentoInicial() {
        return suprimentoInicial;
    }

    public OffsetDateTime getDataAbertura() {
        return dataAbertura;
    }

    public OffsetDateTime getDataFechamento() {
        return dataFechamento;
    }

    public BigDecimal getValorFinalInformado() {
        return valorFinalInformado;
    }

    public BigDecimal getSaldoEsperado() {
        return saldoEsperado;
    }

    public BigDecimal getDiferenca() {
        return diferenca;
    }
}
