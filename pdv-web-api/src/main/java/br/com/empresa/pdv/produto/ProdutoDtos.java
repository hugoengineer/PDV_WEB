package br.com.empresa.pdv.produto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Contratos da seção 3.2 da API (Módulo de Produtos). */
public final class ProdutoDtos {

    static final String REGEX_SKU = "^[A-Za-z0-9._-]{1,40}$";
    static final String REGEX_UNIDADE = "^[A-Za-z]{1,10}$";
    static final String REGEX_CODIGO_BARRAS = "^\\d{8,14}$";
    static final int ESTOQUE_MAXIMO = 1_000_000;

    private ProdutoDtos() {
    }

    /** POST e PUT (PUT substitui o recurso por completo). */
    public record ProdutoRequisicao(
            @NotBlank(message = "SKU é obrigatório.")
            @Pattern(regexp = REGEX_SKU, message = "Use até 40 letras, números, ponto, hífen ou sublinhado.") String sku,
            @NotBlank(message = "Nome é obrigatório.") @Size(max = 120, message = "Máximo de 120 caracteres.") String nome,
            @Size(max = 120, message = "Máximo de 120 caracteres.") String fornecedor,
            @NotBlank(message = "Unidade de medida é obrigatória.")
            @Pattern(regexp = REGEX_UNIDADE, message = "Use de 1 a 10 letras (ex.: UN, KG, CX, PCT).") String unidadeMedida,
            @Pattern(regexp = REGEX_CODIGO_BARRAS, message = "Deve conter de 8 a 14 dígitos (EAN/GTIN).") String codigoBarras,
            String ncm,
            String cest,
            @NotNull(message = "Preço de venda é obrigatório.")
            @DecimalMin(value = "0.01", message = "O preço de venda deve ser maior que zero.")
            @Digits(integer = 8, fraction = 2, message = "Máximo de 8 dígitos inteiros e 2 casas decimais.") BigDecimal precoVenda,
            @NotNull(message = "Estoque atual é obrigatório.")
            @Min(value = 0, message = "O estoque não pode ser negativo.")
            @Max(value = ESTOQUE_MAXIMO, message = "Máximo de 1.000.000 unidades.") Integer estoqueAtual,
            @NotNull(message = "Estoque mínimo é obrigatório.")
            @Min(value = 0, message = "O estoque mínimo não pode ser negativo.")
            @Max(value = ESTOQUE_MAXIMO, message = "Máximo de 1.000.000 unidades.") Integer estoqueMinimo) {
    }

    /** PATCH: somente os campos enviados são alterados. */
    public record ProdutoPatch(
            @Pattern(regexp = REGEX_SKU, message = "Use até 40 letras, números, ponto, hífen ou sublinhado.") String sku,
            @Size(min = 1, max = 120, message = "Deve ter entre 1 e 120 caracteres.") String nome,
            @Size(max = 120, message = "Máximo de 120 caracteres.") String fornecedor,
            @Pattern(regexp = REGEX_UNIDADE, message = "Use de 1 a 10 letras (ex.: UN, KG, CX, PCT).") String unidadeMedida,
            @Pattern(regexp = REGEX_CODIGO_BARRAS, message = "Deve conter de 8 a 14 dígitos (EAN/GTIN).") String codigoBarras,
            String ncm,
            String cest,
            @DecimalMin(value = "0.01", message = "O preço de venda deve ser maior que zero.")
            @Digits(integer = 8, fraction = 2, message = "Máximo de 8 dígitos inteiros e 2 casas decimais.") BigDecimal precoVenda,
            @Min(value = 0, message = "O estoque não pode ser negativo.")
            @Max(value = ESTOQUE_MAXIMO, message = "Máximo de 1.000.000 unidades.") Integer estoqueAtual,
            @Min(value = 0, message = "O estoque mínimo não pode ser negativo.")
            @Max(value = ESTOQUE_MAXIMO, message = "Máximo de 1.000.000 unidades.") Integer estoqueMinimo) {
    }

    public record ProdutoResposta(Long id, String sku, String nome, String fornecedor, String unidadeMedida,
                                  String codigoBarras, String ncm, String cest, BigDecimal precoVenda,
                                  int estoqueAtual, int estoqueMinimo, boolean alertaEstoqueCritico, boolean ativo,
                                  OffsetDateTime atualizadoEm) {
    }

    public record InativacaoResposta(boolean sucesso, String mensagem, Long id, boolean ativo) {
    }
}
