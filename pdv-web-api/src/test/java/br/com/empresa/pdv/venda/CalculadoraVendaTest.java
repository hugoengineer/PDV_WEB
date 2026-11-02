package br.com.empresa.pdv.venda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import br.com.empresa.pdv.comum.erro.ApiException;

/**
 * Técnicas: tabela de decisão (cupom × desconto do gerente) e valores-limite (desconto vs. restante,
 * arredondamento de centavos). Regras: UC06, UC07 (API v2.3 seção 2) e ADR-001 D-04.
 */
@DisplayName("Cálculo financeiro da venda (CalculadoraVenda)")
class CalculadoraVendaTest {

    private static final BigDecimal CINCO_PORCENTO = new BigDecimal("5.00");

    @ParameterizedTest(name = "{displayName} [{index}] subtotal={0} cupom={1} gerente={2} → total={3}, comissão={4}")
    @CsvSource({
            // subtotal, desc.cupom, desc.gerente, total esperado, comissão (5%)
            "250.00,  0.00,  0.00, 250.00, 12.50",  // R1: sem cupom, sem desconto direto
            "250.00, 25.00,  0.00, 225.00, 11.25",  // R2: somente cupom
            "250.00,  0.00, 10.00, 240.00, 12.00",  // R3: somente desconto direto
            "250.00, 25.00, 10.00, 215.00, 10.75"   // R4: cupom + desconto direto (exemplo do contrato v2.3)
    })
    @DisplayName("CT-CALC-001 Tabela de decisão: combinações de cupom e desconto direto")
    void tabelaDeDecisao(String subtotal, String cupom, String gerente, String total, String comissao) {
        ResumoFinanceiro r = CalculadoraVenda.calcular(bd(subtotal), bd(cupom), bd(gerente), CINCO_PORCENTO);

        assertThat(r.total()).isEqualByComparingTo(total);
        assertThat(r.comissao()).isEqualByComparingTo(comissao);
        assertThat(r.subtotal().subtract(r.descontoCupom()).subtract(r.descontoGerente()))
                .as("invariante: total = subtotal - descontos").isEqualByComparingTo(r.total());
    }

    @Test
    @DisplayName("CT-CALC-002 Desconto direto igual ao valor restante zera o total (limite aceito)")
    void descontoIgualAoRestante() {
        ResumoFinanceiro r = CalculadoraVenda.calcular(bd("100.00"), bd("10.00"), bd("90.00"), CINCO_PORCENTO);
        assertThat(r.total()).isEqualByComparingTo("0.00");
        assertThat(r.comissao()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("CT-CALC-003 Desconto direto 1 centavo acima do restante é rejeitado (422 desconto-invalido)")
    void descontoAcimaDoRestante() {
        assertThatThrownBy(() -> CalculadoraVenda.calcular(bd("100.00"), bd("10.00"), bd("90.01"), CINCO_PORCENTO))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(422);
                    assertThat(e.getTipo()).isEqualTo("desconto-invalido");
                });
    }

    @Test
    @DisplayName("CT-CALC-004 Desconto direto negativo é rejeitado (400)")
    void descontoNegativo() {
        assertThatThrownBy(() -> CalculadoraVenda.calcular(bd("100.00"), null, bd("-0.01"), CINCO_PORCENTO))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(400));
    }

    @Test
    @DisplayName("CT-CALC-005 Desconto de cupom maior que o subtotal é limitado ao subtotal")
    void cupomMaiorQueSubtotal() {
        ResumoFinanceiro r = CalculadoraVenda.calcular(bd("30.00"), bd("50.00"), null, CINCO_PORCENTO);
        assertThat(r.descontoCupom()).isEqualByComparingTo("30.00");
        assertThat(r.total()).isEqualByComparingTo("0.00");
    }

    @ParameterizedTest(name = "{displayName} [{index}] total={0} × {1}% = {2}")
    @CsvSource({
            "10.10, 2.50, 0.25",   // 0,2525 → arredonda para baixo
            "10.30, 2.50, 0.26",   // 0,2575 → arredonda para cima
            "0.10,  5.00, 0.01",   // 0,005 → limite exato do HALF_UP
            "0.09,  5.00, 0.00"    // 0,0045 → abaixo do limite
    })
    @DisplayName("CT-CALC-006 Comissão arredondada em centavos com HALF_UP (valores-limite)")
    void arredondamentoComissao(String total, String percentual, String esperado) {
        ResumoFinanceiro r = CalculadoraVenda.calcular(bd(total), null, null, bd(percentual));
        assertThat(r.comissao()).isEqualByComparingTo(esperado);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0}%")
    @CsvSource({"0.00, 0.00", "100.00, 80.00"})
    @DisplayName("CT-CALC-007 Comissão nos extremos de percentual (0% e 100%)")
    void percentuaisExtremos(String percentual, String esperado) {
        ResumoFinanceiro r = CalculadoraVenda.calcular(bd("80.00"), null, null, bd(percentual));
        assertThat(r.comissao()).isEqualByComparingTo(esperado);
    }

    @Test
    @DisplayName("CT-CALC-008 Valores nulos de desconto são tratados como zero")
    void descontosNulos() {
        ResumoFinanceiro r = CalculadoraVenda.calcular(bd("19.90"), null, null, null);
        assertThat(r.descontoCupom()).isEqualByComparingTo("0.00");
        assertThat(r.descontoGerente()).isEqualByComparingTo("0.00");
        assertThat(r.total()).isEqualByComparingTo("19.90");
        assertThat(r.comissao()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("CT-CALC-009 Base da comissão: LIQUIDO usa o total (R$ 10,75); BRUTO usa o subtotal (R$ 12,50)")
    void baseDaComissao() {
        ResumoFinanceiro liquido = CalculadoraVenda.calcular(bd("250.00"), bd("25.00"), bd("10.00"), CINCO_PORCENTO,
                BaseComissao.LIQUIDO);
        ResumoFinanceiro bruto = CalculadoraVenda.calcular(bd("250.00"), bd("25.00"), bd("10.00"), CINCO_PORCENTO,
                BaseComissao.BRUTO);

        assertThat(liquido.comissao()).isEqualByComparingTo("10.75");
        assertThat(bruto.comissao()).isEqualByComparingTo("12.50");
        assertThat(bruto.total()).as("a base da comissão não altera o valor pago").isEqualByComparingTo("215.00");
    }

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor.trim());
    }
}
