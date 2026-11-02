package br.com.empresa.pdv.produto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import br.com.empresa.pdv.comum.erro.ApiException;

/** Regras de estoque do produto (VAL-01, VAL-04) e normalização fiscal (NCM/CEST). */
@DisplayName("Regras do produto e estoque")
class ProdutoTest {

    @ParameterizedTest(name = "{displayName} [{index}] estoque={0}, mínimo=2 → crítico={1}")
    @CsvSource({"3, false", "2, true", "1, true", "0, true"})
    @DisplayName("CT-PROD-U01 Alerta de estoque crítico nos limites do estoque mínimo (VAL-04)")
    void estoqueCritico(int estoque, boolean critico) {
        Produto produto = produto(estoque, 2);
        assertThat(produto.isEstoqueCritico()).isEqualTo(critico);
    }

    @Test
    @DisplayName("CT-PROD-U02 Baixa de quantidade igual ao saldo zera o estoque (limite aceito)")
    void baixaDoSaldoExato() {
        Produto produto = produto(5, 0);
        produto.baixarEstoque(5);
        assertThat(produto.getEstoqueAtual()).isZero();
    }

    @Test
    @DisplayName("CT-PROD-U03 Baixa maior que o saldo é impedida e o estoque não muda (VAL-01)")
    void baixaMaiorQueSaldo() {
        Produto produto = produto(5, 0);
        assertThatThrownBy(() -> produto.baixarEstoque(6)).isInstanceOf(IllegalStateException.class);
        assertThat(produto.getEstoqueAtual()).isEqualTo(5);
    }

    @Test
    @DisplayName("CT-PROD-U04 Estorno devolve a quantidade ao estoque (VAL-03)")
    void estorno() {
        Produto produto = produto(8, 0);
        produto.estornarEstoque(2);
        assertThat(produto.getEstoqueAtual()).isEqualTo(10);
    }

    @ParameterizedTest(name = "{displayName} [{index}] \"{0}\" → {1}")
    @CsvSource({"8471.60.52, 84716052", "84716052, 84716052", "8471 60 52, 84716052"})
    @DisplayName("CT-PROD-U05 NCM com ou sem pontuação é normalizado para 8 dígitos")
    void ncmNormalizado(String entrada, String esperado) {
        assertThat(ProdutoService.normalizarCodigoFiscal(entrada, 8, "ncm")).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "{displayName} [{index}] \"{0}\"")
    @ValueSource(strings = {"8471605", "847160521", "8471.60.5A"})
    @DisplayName("CT-PROD-U06 NCM com 7 ou 9 dígitos, ou com letras, é rejeitado")
    void ncmInvalido(String entrada) {
        assertThatThrownBy(() -> ProdutoService.normalizarCodigoFiscal(entrada, 8, "ncm"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("CT-PROD-U07 CEST aceita 7 dígitos e rejeita 6; vazio vira nulo (opcional)")
    void cest() {
        assertThat(ProdutoService.normalizarCodigoFiscal("21.031.00", 7, "cest")).isEqualTo("2103100");
        assertThatThrownBy(() -> ProdutoService.normalizarCodigoFiscal("21.031.0", 7, "cest"))
                .isInstanceOf(ApiException.class);
        assertThat(ProdutoService.normalizarCodigoFiscal("  ", 7, "cest")).isNull();
    }

    private static Produto produto(int estoque, int minimo) {
        Produto produto = new Produto("SKU-1", OffsetDateTime.now());
        produto.setEstoqueAtual(estoque);
        produto.setEstoqueMinimo(minimo);
        return produto;
    }
}
