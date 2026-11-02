package br.com.empresa.pdv.auditoria;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LogAuditoriaRepository extends JpaRepository<LogAuditoria, Long>,
        JpaSpecificationExecutor<LogAuditoria> {

    List<LogAuditoria> findByTipoAcaoAndEntidadeId(TipoAcao tipoAcao, Long entidadeId);
}
