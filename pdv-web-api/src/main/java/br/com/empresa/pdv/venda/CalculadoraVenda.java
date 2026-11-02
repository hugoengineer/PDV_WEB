package br.com.empresa.pdv.venda;

import java.math.BigDecimal;

import br.com.empresa.pdv.comum.Dinheiro;
import br.com.empresa.pdv.comum.erro.ApiException;

/**
 * Regras financeiras da venda (UC06, UC07, UC10), sem dependência de banco ou framework:
 * <ol>
 *   <li>O desconto do cupom incide primeiro, sobre o subtotal.</li>
 *   <li>O desconto direto do gerente é um valor fixo em R$, aplicado sobre o que restou.</li>
 *   <li>O total nunca fica negativo: um desconto do gerente maior que o restante é rejeitado.</li>
 *   <li>A comissão é calculada sobre o valor líquido por padrão, ou sobre o bruto se configurado (ADR-001, D-04).</li>
 * </ol>
 */
public final class CalculadoraVenda {

    private CalculadoraVenda() {
    }

    public static ResumoFinanceiro calcular(BigDecimal subtotal, BigDecimal descontoCupom, BigDecimal descontoGerente,
                                            BigDecimal percentualComissao) {
        return calcular(subtotal, descontoCupom, descontoGerente, percentualComissao, BaseComissao.LIQUIDO);
    }

    public static ResumoFinanceiro calcular(BigDecimal subtotal, BigDecimal descontoCupom, BigDecimal descontoGerente,
                                            BigDecimal percentualComissao, BaseComissao base) {
        BigDecimal sub = Dinheiro.arredondar(subtotal);
        BigDecimal cupom = Dinheiro.valorOuZero(descontoCupom).min(sub);
        BigDecimal gerente = Dinheiro.valorOuZero(descontoGerente);

        if (gerente.signum() < 0) {
            throw ApiException.validacao("desconto_direto_gerente", "O desconto não pode ser negativo.");
        }
        BigDecimal restante = sub.subtract(cupom);
        if (gerente.compareTo(restante) > 0) {
            throw ApiException.regraNegocio("desconto-invalido", "Desconto Inválido",
                    "O desconto direto (R$ " + gerente + ") é maior que o valor restante da venda (R$ " + restante + ").");
        }
        BigDecimal total = restante.subtract(gerente);
        BigDecimal percentual = Dinheiro.valorOuZero(percentualComissao);
        BigDecimal baseComissao = base == BaseComissao.BRUTO ? sub : total;
        return new ResumoFinanceiro(sub, cupom, gerente, total, percentual, Dinheiro.percentual(baseComissao, percentual));
    }
}
