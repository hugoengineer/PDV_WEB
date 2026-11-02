package br.com.empresa.pdv.caixa;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovimentacaoCaixaRepository extends JpaRepository<MovimentacaoCaixa, Long> {

    @Query("select coalesce(sum(m.valor), 0) from MovimentacaoCaixa m where m.caixaId = :caixaId and m.tipo = :tipo")
    BigDecimal somarPorTipo(@Param("caixaId") Long caixaId, @Param("tipo") TipoMovimentacao tipo);
}
