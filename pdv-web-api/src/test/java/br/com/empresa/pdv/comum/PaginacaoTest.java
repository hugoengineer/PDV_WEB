package br.com.empresa.pdv.comum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import br.com.empresa.pdv.comum.erro.ApiException;
import br.com.empresa.pdv.comum.erro.NomesJson;

/** Parâmetros de paginação do contrato (pagina ≥ 1; 1 ≤ limite ≤ 100) — valores-limite. */
@DisplayName("Paginação e convenções do contrato")
class PaginacaoTest {

    @Test
    @DisplayName("CT-PAG-001 Sem parâmetros usa página 1 e limite 20 (padrão do contrato)")
    void padroes() {
        Pageable p = Paginacao.de(null, null, Sort.unsorted());
        assertThat(p.getPageNumber()).isZero();
        assertThat(p.getPageSize()).isEqualTo(20);
    }

    @ParameterizedTest(name = "{displayName} [{index}] pagina={0}, limite={1}")
    @CsvSource({"1, 1", "1, 100", "5, 50"})
    @DisplayName("CT-PAG-002 Valores dentro dos limites são aceitos")
    void aceitos(int pagina, int limite) {
        Pageable p = Paginacao.de(pagina, limite, Sort.unsorted());
        assertThat(p.getPageNumber()).isEqualTo(pagina - 1);
        assertThat(p.getPageSize()).isEqualTo(limite);
    }

    @ParameterizedTest(name = "{displayName} [{index}] limite={0}")
    @ValueSource(ints = {0, 101, -1})
    @DisplayName("CT-PAG-003 Limite 0, negativo ou acima de 100 é rejeitado")
    void limiteForaDaFaixa(int limite) {
        assertThatThrownBy(() -> Paginacao.de(1, limite, Sort.unsorted())).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("CT-PAG-004 Página 0 é rejeitada (a numeração começa em 1)")
    void paginaZero() {
        assertThatThrownBy(() -> Paginacao.de(0, 10, Sort.unsorted())).isInstanceOf(ApiException.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0} → {1}")
    @CsvSource({"precoVenda, preco_venda", "nome, nome", "itens[0].produtoId, itens[0].produto_id"})
    @DisplayName("CT-PAG-005 Nomes de campos em invalid_params seguem snake_case")
    void snakeCase(String java, String json) {
        assertThat(NomesJson.paraSnakeCase(java)).isEqualTo(json);
    }
}
