package br.com.empresa.pdv.venda;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracaoParcelamentoRepository extends JpaRepository<ConfiguracaoParcelamento, ModalidadePagamento> {
}
