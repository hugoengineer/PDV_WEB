package br.com.empresa.pdv.cupom;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Contratos da seção 3.3 da API (Módulo de Cupons de Desconto). */
public final class CupomDtos {

    static final String REGEX_CODIGO = "^[A-Za-z0-9_-]{3,30}$";

    private CupomDtos() {
    }

    /** POST e PUT. */
    public record CupomRequisicao(
            @NotBlank(message = "Código é obrigatório.")
            @Pattern(regexp = REGEX_CODIGO, message = "Use de 3 a 30 letras, números, hífen ou sublinhado.") String codigo,
            @NotNull(message = "Tipo de desconto é obrigatório (PERCENTUAL ou VALOR_FIXO).") TipoDesconto tipoDesconto,
            @NotNull(message = "Valor do desconto é obrigatório.")
            @DecimalMin(value = "0.01", message = "O valor do desconto deve ser maior que zero.")
            @Digits(integer = 8, fraction = 2, message = "Máximo de 2 casas decimais.") BigDecimal valorDesconto,
            @NotNull(message = "Data de início da validade é obrigatória.") OffsetDateTime dataInicioValidade,
            @NotNull(message = "Data de término da validade é obrigatória.") OffsetDateTime dataTerminoValidade) {
    }

    /** PATCH: somente os campos enviados são alterados. */
    public record CupomPatch(
            @Pattern(regexp = REGEX_CODIGO, message = "Use de 3 a 30 letras, números, hífen ou sublinhado.") String codigo,
            TipoDesconto tipoDesconto,
            @DecimalMin(value = "0.01", message = "O valor do desconto deve ser maior que zero.")
            @Digits(integer = 8, fraction = 2, message = "Máximo de 2 casas decimais.") BigDecimal valorDesconto,
            OffsetDateTime dataInicioValidade,
            OffsetDateTime dataTerminoValidade) {
    }

    public record CupomResposta(Long id, String codigo, TipoDesconto tipoDesconto, BigDecimal valorDesconto,
                                OffsetDateTime dataInicioValidade, OffsetDateTime dataTerminoValidade,
                                boolean vigente, boolean ativo) {
    }

    public record InativacaoResposta(boolean sucesso, String mensagem, Long id, boolean ativo) {
    }
}
