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
    static final int MAXIMO_PAGAMENTOS = 10;
    static final int TAMANHO_OBSERVACAO = 500;

    private VendaDtos() {
    }

    public record VendaRequisicao(
            @NotNull(message = "caixa_id é obrigatório.") Long caixaId,
            Long clienteId,
            @Size(max = 30, message = "Máximo de 30 caracteres.") String cupomCodigo,
            @DecimalMin(value = "0.00", message = "O desconto não pode ser negativo.")
            @Digits(integer = 8, fraction = 2, message = "Máximo de 2 casas decimais.") BigDecimal descontoDiretoGerente,
            String senhaAutorizacaoGerente,
            /** Forma única (contrato original). Informe esta OU a lista pagamentos. */
            ModalidadePagamento modalidadePagamento,
            /** Pagamento dividido: a soma dos valores deve ser exatamente o total da venda. */
            @Size(max = MAXIMO_PAGAMENTOS, message = "Máximo de 10 pagamentos por venda.")
            List<@Valid @NotNull(message = "Pagamento não pode ser nulo.") PagamentoRequisicao> pagamentos,
            @Size(max = TAMANHO_OBSERVACAO, message = "Máximo de 500 caracteres.") String observacao,
            @NotEmpty(message = "A venda deve ter ao menos um item.")
            @Size(max = MAXIMO_ITENS, message = "Máximo de 200 itens por venda.")
            List<@Valid @NotNull(message = "Item não pode ser nulo.") ItemRequisicao> itens) {
    }

    /** Uma parte do pagamento. Parcelas (sem juros) só em crédito e crediário, até o limite do gerente. */
    public record PagamentoRequisicao(
            @NotNull(message = "Forma de pagamento é obrigatória.") ModalidadePagamento modalidadePagamento,
            @NotNull(message = "Valor é obrigatório.")
            @DecimalMin(value = "0.00", message = "O valor não pode ser negativo.")
            @Digits(integer = 8, fraction = 2, message = "Máximo de 2 casas decimais.") BigDecimal valor,
            @Min(value = 1, message = "Mínimo de 1 parcela.")
            @Max(value = ConfiguracaoParcelamento.LIMITE_DO_SISTEMA, message = "Máximo de 12 parcelas.") Integer parcelas) {
    }

    /** Prévia: os mesmos dados da venda, sem pagamento. Calcula os totais sem gravar nada. */
    public record PreviaRequisicao(
            @Size(max = 30, message = "Máximo de 30 caracteres.") String cupomCodigo,
            @DecimalMin(value = "0.00", message = "O desconto não pode ser negativo.")
            @Digits(integer = 8, fraction = 2, message = "Máximo de 2 casas decimais.") BigDecimal descontoDiretoGerente,
            @NotEmpty(message = "A venda deve ter ao menos um item.")
            @Size(max = MAXIMO_ITENS, message = "Máximo de 200 itens por venda.")
            List<@Valid @NotNull(message = "Item não pode ser nulo.") ItemRequisicao> itens) {
    }

    public record PreviaResposta(BigDecimal valorSubtotal, BigDecimal descontoCupom, BigDecimal descontoGerente,
                                 BigDecimal valorTotal) {
    }

    public record PagamentoDetalhe(ModalidadePagamento modalidadePagamento, BigDecimal valor, int parcelas,
                                   BigDecimal valorParcela, BigDecimal valorUltimaParcela) {
        static PagamentoDetalhe de(PagamentoVenda p) {
            return new PagamentoDetalhe(p.getModalidadePagamento(), p.getValor(), p.getParcelas(),
                    p.getValorParcela(), p.getValorUltimaParcela());
        }
    }

    public record ParcelamentoResposta(ModalidadePagamento modalidadePagamento, int maxParcelas,
                                       OffsetDateTime atualizadoEm, Long atualizadoPor) {
    }

    public record ParcelamentoAlteracao(
            @NotNull(message = "Informe o número máximo de parcelas.")
            @Min(value = 1, message = "Mínimo de 1 parcela.")
            @Max(value = ConfiguracaoParcelamento.LIMITE_DO_SISTEMA, message = "Máximo de 12 parcelas.") Integer maxParcelas) {
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
                                          List<AlertaEstoque> alertasEstoqueCritico,
                                          List<PagamentoDetalhe> pagamentos, String observacao) {
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
                               OffsetDateTime dataCancelamento, List<ItemDetalhe> itens,
                               List<PagamentoDetalhe> pagamentos, String observacao, Long usuarioCancelamentoId) {
    }

    public record CancelamentoRequisicao(
            @NotBlank(message = "Motivo do cancelamento é obrigatório.")
            @Size(min = 5, max = 255, message = "Deve ter entre 5 e 255 caracteres.") String motivoCancelamento,
            /** Obrigatória quando quem cancela é um Vendedor (autorização de um gerente ativo). */
            String senhaAutorizacaoGerente) {
    }

    public record CancelamentoResposta(boolean sucesso, String mensagem, Long vendaId, StatusVenda status) {
    }

    public record ComissaoResposta(Long vendedorId, String vendedorNome, long totalVendasRealizadas,
                                   BigDecimal valorTotalVendido, BigDecimal percentualComissao,
                                   BigDecimal totalComissaoAcumulada) {
    }
}
