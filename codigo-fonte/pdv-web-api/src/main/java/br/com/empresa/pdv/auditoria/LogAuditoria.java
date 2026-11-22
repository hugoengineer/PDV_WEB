package br.com.empresa.pdv.auditoria;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Registro imutável de auditoria: quem, quando, o quê e valores anterior/novo. */
@Entity
@Table(name = "logs_auditoria")
public class LogAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "data_hora", nullable = false)
    private OffsetDateTime dataHora;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_acao", nullable = false, length = 40)
    private TipoAcao tipoAcao;

    @Column(length = 40)
    private String entidade;

    @Column(name = "entidade_id")
    private Long entidadeId;

    @Column(nullable = false, length = 1000)
    private String detalhes;

    @Column(name = "valor_anterior")
    private String valorAnterior;

    @Column(name = "valor_novo")
    private String valorNovo;

    protected LogAuditoria() {
    }

    LogAuditoria(Long usuarioId, OffsetDateTime dataHora, TipoAcao tipoAcao, String entidade, Long entidadeId,
                 String detalhes, String valorAnterior, String valorNovo) {
        this.usuarioId = usuarioId;
        this.dataHora = dataHora;
        this.tipoAcao = tipoAcao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.detalhes = detalhes.length() > 1000 ? detalhes.substring(0, 1000) : detalhes;
        this.valorAnterior = valorAnterior;
        this.valorNovo = valorNovo;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public OffsetDateTime getDataHora() {
        return dataHora;
    }

    public TipoAcao getTipoAcao() {
        return tipoAcao;
    }

    public String getEntidade() {
        return entidade;
    }

    public Long getEntidadeId() {
        return entidadeId;
    }

    public String getDetalhes() {
        return detalhes;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public String getValorNovo() {
        return valorNovo;
    }
}
