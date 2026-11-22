package br.com.empresa.pdv.caixa;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaixaRepository extends JpaRepository<Caixa, Long> {

    Optional<Caixa> findByUsuarioIdAndStatus(Long usuarioId, StatusCaixa status);

    boolean existsByUsuarioIdAndStatus(Long usuarioId, StatusCaixa status);

    /** Serializa sangrias, vendas e fechamento do mesmo caixa (evita sangria maior que o saldo). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Caixa c where c.id = :id")
    Optional<Caixa> bloquear(@Param("id") Long id);
}
