package br.com.empresa.pdv.venda;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VendaRepository extends JpaRepository<Venda, Long>, JpaSpecificationExecutor<Venda> {

    @Query("""
            select coalesce(sum(v.valorTotal), 0) from Venda v
            where v.caixaId = :caixaId and v.status = br.com.empresa.pdv.venda.StatusVenda.CONCLUIDA
            """)
    BigDecimal somarConcluidasDoCaixa(@Param("caixaId") Long caixaId);

    @Query("""
            select coalesce(sum(v.valorTotal), 0) from Venda v
            where v.caixaId = :caixaId and v.modalidadePagamento = :modalidade
              and v.status = br.com.empresa.pdv.venda.StatusVenda.CONCLUIDA
            """)
    BigDecimal somarConcluidasDoCaixaPorModalidade(@Param("caixaId") Long caixaId,
                                                   @Param("modalidade") ModalidadePagamento modalidade);

    /** UC10: consolida as vendas concluídas por vendedor no período [inicio, fim). */
    @Query("""
            select new br.com.empresa.pdv.venda.ConsolidadoVendedor(
                       v.usuarioId, count(v), coalesce(sum(v.valorTotal), 0), coalesce(sum(v.valorComissao), 0))
            from Venda v
            where v.status = br.com.empresa.pdv.venda.StatusVenda.CONCLUIDA
              and v.dataVenda >= :inicio and v.dataVenda < :fim
              and (:usuarioId is null or v.usuarioId = :usuarioId)
            group by v.usuarioId
            order by v.usuarioId
            """)
    List<ConsolidadoVendedor> consolidarPorVendedor(@Param("inicio") OffsetDateTime inicio,
                                                    @Param("fim") OffsetDateTime fim,
                                                    @Param("usuarioId") Long usuarioId);

    /** Bloqueia a venda para que dois cancelamentos simultâneos não estornem o estoque duas vezes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Venda v where v.id = :id")
    Optional<Venda> bloquear(@Param("id") Long id);
}
