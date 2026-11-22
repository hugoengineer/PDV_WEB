package br.com.empresa.pdv.caixa;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contratos da seção 3.5 da API (Controle de Caixa). */
public final class CaixaDtos {

    private CaixaDtos() {
    }

    public record AberturaRequisicao(
            @NotNull(message = "Suprimento inicial é obrigatório.")
            @DecimalMin(value = "0.00", message = "O suprimento inicial não pode ser negativo.")
            @Digits(integer = 8, fraction = 2, message = "Máximo de 2 casas decimais.") BigDecimal suprimentoInicial) {
    }

    public record AberturaResposta(Long caixaId, OffsetDateTime dataAbertura, BigDecimal suprimentoInicial,
                                   StatusCaixa status) {
    }

    /** Sangria e suprimento usam o mesmo corpo. */
    public record MovimentacaoRequisicao(
            @NotNull(message = "Valor é obrigatório.")
            @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.")
            @Digits(integer = 8, fraction = 2, message = "Máximo de 2 casas decimais.") BigDecimal valor,
            @NotBlank(message = "Observação/justificativa é obrigatória.")
            @Size(max = 255, message = "Máximo de 255 caracteres.") String observacao) {
    }

    public record SangriaResposta(Long sangriaId, Long caixaId, BigDecimal valor, Long usuarioGerenteId,
                                  OffsetDateTime dataHora, BigDecimal saldoEmDinheiroAtual) {
    }

    public record SuprimentoResposta(Long suprimentoId, Long caixaId, BigDecimal valor, Long usuarioGerenteId,
                                     OffsetDateTime dataHora, BigDecimal saldoEmDinheiroAtual) {
    }

    public record FechamentoRequisicao(
            @NotNull(message = "Valor final em dinheiro é obrigatório.")
            @DecimalMin(value = "0.00", message = "O valor final não pode ser negativo.")
            @Digits(integer = 8, fraction = 2, message = "Máximo de 2 casas decimais.") BigDecimal valorFinalDinheiro) {
    }

    public record FechamentoResposta(Long caixaId, StatusCaixa status, BigDecimal totalVendas,
                                     BigDecimal totalVendasDinheiro, BigDecimal totalSangrias,
                                     BigDecimal totalSuprimentos, BigDecimal saldoEsperadoDinheiro,
                                     BigDecimal valorFinalDinheiro, BigDecimal diferenca,
                                     OffsetDateTime dataFechamento) {
    }

    /** GET /caixas/atual e GET /caixas/{id}: saldo parcial do turno. */
    public record CaixaResposta(Long caixaId, Long usuarioId, StatusCaixa status, OffsetDateTime dataAbertura,
                                BigDecimal suprimentoInicial, BigDecimal totalVendas, BigDecimal totalVendasDinheiro,
                                BigDecimal totalSangrias, BigDecimal totalSuprimentos,
                                BigDecimal saldoEmDinheiroAtual, OffsetDateTime dataFechamento,
                                BigDecimal valorFinalInformado, BigDecimal diferenca) {
    }
}
