package br.com.empresa.pdv.venda;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contratos da seção 3.4 da API (Módulo de Vendas e Comissões). */
public final class VendaDtos {

    static final int MAXIMO_ITENS = 200;
    static final int QUANTIDADE_MAXIMA_ITEM = 10_000;

    private VendaDtos() {
    }

    public record VendaRequisicao(
            @NotNull(message = "caixa_id é obrigatório.") Long caixaId,
            Long clienteId,
            @Size(max = 30, message = "Máximo de 30 caracteres.") String cupomCodigo,
            @DecimalMin(value = "0.00", message = "O desconto não pode ser negativo.")
            @Digits(integer = 8, fraction = 2, message = "Máximo de 2 casas decimais.") BigDecimal descontoDiretoGerente,
            String senhaAutorizacaoGerente,
            @NotNull(message = "Modalidade de pagamento é obrigatória.") ModalidadePagamento modalidadePagamento,
            @NotEmpty(message = "A venda deve ter ao menos um item.")
            @Size(max = MAXIMO_ITENS, message = "Máximo de 200 itens por venda.")
            List<@Valid @NotNull(message = "Item não pode ser nulo.") ItemRequisicao> itens) {
    }

    /**
     * {@code preco_unitario} é aceito por compatibilidade com o contrato, mas ignorado:
     * o preço praticado é sempre o do catálogo (ADR-001, D-06).
     */
    public record ItemRequisicao(
            @NotNull(message = "produto_id é obrigatório.") Long produtoId,
            @NotNull(message = "Quantidade é obrigatória.")
            @Min(value = 1, message = "A quantidade deve ser maior ou igual a 1.")
            @Max(value = QUANTIDADE_MAXIMA_ITEM, message = "Máximo de 10.000 unidades por item.") Integer quantidade,
            BigDecimal precoUnitario) {
    }

    public record AlertaEstoque(Long produtoId, String nome, int estoqueAtual, int estoqueMinimo) {
    }

    public record VendaRegistradaResposta(Long vendaId, BigDecimal valorSubtotal, BigDecimal descontoCupom,
                                          BigDecimal descontoGerente, BigDecimal valorTotal,
                                          ModalidadePagamento modalidadePagamento, StatusVenda status,
                                          BigDecimal comissaoVendedor, OffsetDateTime dataVenda,
                                          List<AlertaEstoque> alertasEstoqueCritico) {
    }

    public record VendaResumo(Long vendaId, OffsetDateTime dataVenda, String cliente, String vendedor,
                              BigDecimal valorTotal, ModalidadePagamento modalidadePagamento, StatusVenda status) {
    }

    public record ItemDetalhe(Long produtoId, String produto, int quantidade, BigDecimal precoUnitario,
                              BigDecimal subtotal) {
    }

    public record VendaDetalhe(Long vendaId, OffsetDateTime dataVenda, Long caixaId, Long clienteId, String cliente,
                               Long vendedorId, String vendedor, BigDecimal valorSubtotal, BigDecimal descontoCupom,
                               BigDecimal descontoGerente, BigDecimal valorTotal,
                               ModalidadePagamento modalidadePagamento, StatusVenda status,
                               BigDecimal percentualComissao, BigDecimal comissaoVendedor, Long cupomId,
                               Long gerenteAutorizadorId, String motivoCancelamento,
                               OffsetDateTime dataCancelamento, List<ItemDetalhe> itens) {
    }

    public record CancelamentoRequisicao(
            @NotBlank(message = "Motivo do cancelamento é obrigatório.")
            @Size(min = 5, max = 255, message = "Deve ter entre 5 e 255 caracteres.") String motivoCancelamento) {
    }

    public record CancelamentoResposta(boolean sucesso, String mensagem, Long vendaId, StatusVenda status) {
    }

    public record ComissaoResposta(Long vendedorId, String vendedorNome, long totalVendasRealizadas,
                                   BigDecimal valorTotalVendido, BigDecimal percentualComissao,
                                   BigDecimal totalComissaoAcumulada) {
    }
}
