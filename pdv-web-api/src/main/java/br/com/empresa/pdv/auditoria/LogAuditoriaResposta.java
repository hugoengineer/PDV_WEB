package br.com.empresa.pdv.auditoria;

import java.time.OffsetDateTime;

public record LogAuditoriaResposta(Long id, Long usuarioId, OffsetDateTime dataHora, TipoAcao tipoAcao,
                                   String entidade, Long entidadeId, String detalhes, String valorAnterior,
                                   String valorNovo) {
}
