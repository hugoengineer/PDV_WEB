package br.com.empresa.pdv.auditoria;

import java.time.LocalDate;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.empresa.pdv.comum.Pagina;
import br.com.empresa.pdv.comum.Paginacao;
import br.com.empresa.pdv.comum.Relogio;

@Service
public class AuditoriaService {

    private final LogAuditoriaRepository repository;
    private final Relogio relogio;

    public AuditoriaService(LogAuditoriaRepository repository, Relogio relogio) {
        this.repository = repository;
        this.relogio = relogio;
    }

    /**
     * Participa da transação da operação auditada: se a operação falhar, o log também é desfeito,
     * evitando registros de ações que não aconteceram.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(Long usuarioId, TipoAcao tipo, String entidade, Long entidadeId, String detalhes,
                          String valorAnterior, String valorNovo) {
        repository.save(new LogAuditoria(usuarioId, relogio.agora(), tipo, entidade, entidadeId, detalhes,
                valorAnterior, valorNovo));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(Long usuarioId, TipoAcao tipo, String entidade, Long entidadeId, String detalhes) {
        registrar(usuarioId, tipo, entidade, entidadeId, detalhes, null, null);
    }

    /** Para eventos registrados fora de uma operação de negócio (ex.: tentativa de login que falhou). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarIsolado(Long usuarioId, TipoAcao tipo, String entidade, Long entidadeId, String detalhes) {
        registrar(usuarioId, tipo, entidade, entidadeId, detalhes, null, null);
    }

    @Transactional(readOnly = true)
    public Pagina<LogAuditoriaResposta> consultar(Integer pagina, Integer limite, LocalDate inicio, LocalDate fim,
                                                  TipoAcao tipo) {
        Relogio.validarPeriodo(inicio, fim);
        Specification<LogAuditoria> filtro = (raiz, consulta, cb) -> cb.conjunction();
        if (inicio != null) {
            filtro = filtro.and((raiz, consulta, cb) ->
                    cb.greaterThanOrEqualTo(raiz.get("dataHora"), relogio.inicioDoDia(inicio)));
        }
        if (fim != null) {
            filtro = filtro.and((raiz, consulta, cb) ->
                    cb.lessThan(raiz.get("dataHora"), relogio.fimDoDiaExclusivo(fim)));
        }
        if (tipo != null) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("tipoAcao"), tipo));
        }
        var paginacao = Paginacao.de(pagina, limite, Sort.by(Sort.Direction.DESC, "dataHora", "id"));
        return Pagina.de(repository.findAll(filtro, paginacao), log -> new LogAuditoriaResposta(
                log.getId(), log.getUsuarioId(), relogio.noFuso(log.getDataHora()), log.getTipoAcao(),
                log.getEntidade(), log.getEntidadeId(), log.getDetalhes(), log.getValorAnterior(), log.getValorNovo()));
    }
}
