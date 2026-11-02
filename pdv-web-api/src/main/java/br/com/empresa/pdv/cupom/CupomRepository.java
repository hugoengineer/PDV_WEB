package br.com.empresa.pdv.cupom;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CupomRepository extends JpaRepository<Cupom, Long>, JpaSpecificationExecutor<Cupom> {

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);

    Optional<Cupom> findByCodigo(String codigo);
}
