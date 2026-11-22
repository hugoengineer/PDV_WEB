package br.com.empresa.pdv.cupom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.empresa.pdv.comum.erro.ApiException;

/** Técnica: valores-limite sobre a janela de vigência (limites inclusivos) e sobre o percentual (0,01–100). */
@DisplayName("Regras do cupom de desconto (UC06 / UC14)")
class CupomTest {

    private static final ZoneOffset BRT = ZoneOffset.ofHours(-3);
    private static final OffsetDateTime INICIO = OffsetDateTime.of(2026, 9, 1, 0, 0, 0, 0, BRT);
    private static final OffsetDateTime TERMINO = OffsetDateTime.of(2026, 9, 30, 23, 59, 59, 0, BRT);

    @Test
    @DisplayName("CT-CUP-001 Cupom é vigente exatamente no instante de início (limite inclusivo)")
    void vigenteNoInicio() {
        assertThat(cupom(TipoDesconto.PERCENTUAL, "10").isVigente(INICIO)).isTrue();
    }

    @Test
    @DisplayName("CT-CUP-002 Cupom não é vigente 1 segundo antes do início")
    void naoVigenteAntesDoInicio() {
        assertThat(cupom(TipoDesconto.PERCENTUAL, "10").isVigente(INICIO.minusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("CT-CUP-003 Cupom é vigente exatamente no instante de término (limite inclusivo)")
    void vigenteNoTermino() {
        assertThat(cupom(TipoDesconto.PERCENTUAL, "10").isVigente(TERMINO)).isTrue();
    }

    @Test
    @DisplayName("CT-CUP-004 Cupom não é vigente 1 segundo após o término")
    void naoVigenteAposTermino() {
        assertThat(cupom(TipoDesconto.PERCENTUAL, "10").isVigente(TERMINO.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("CT-CUP-005 Cupom inativo não é vigente mesmo dentro do período")
    void inativoNaoVigente() {
        Cupom cupom = cupom(TipoDesconto.PERCENTUAL, "10");
        cupom.setAtivo(false);
        assertThat(cupom.isVigente(INICIO.plusDays(10))).isFalse();
    }

    @Test
    @DisplayName("CT-CUP-006 Cupom percentual de 10% sobre R$ 250,00 gera R$ 25,00 (exemplo do contrato)")
    void percentualExemploContrato() {
        assertThat(cupom(TipoDesconto.PERCENTUAL, "10.00").calcularDesconto(new BigDecimal("250.00")))
                .isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("CT-CUP-007 Desconto percentual é arredondado em centavos (33,33% de R$ 10,00 = R$ 3,33)")
    void percentualArredondado() {
        assertThat(cupom(TipoDesconto.PERCENTUAL, "33.33").calcularDesconto(new BigDecimal("10.00")))
                .isEqualByComparingTo("3.33");
    }

    @Test
    @DisplayName("CT-CUP-008 Cupom de valor fixo maior que o subtotal é limitado ao subtotal")
    void valorFixoLimitado() {
        assertThat(cupom(TipoDesconto.VALOR_FIXO, "50.00").calcularDesconto(new BigDecimal("30.00")))
                .isEqualByComparingTo("30.00");
        assertThat(cupom(TipoDesconto.VALOR_FIXO, "20.00").calcularDesconto(new BigDecimal("30.00")))
                .isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("CT-CUP-009 Percentual 100,00 é aceito e 100,01 é rejeitado na emissão")
    void limitePercentual() {
        assertThatCode(() -> CupomService.validarRegras(TipoDesconto.PERCENTUAL, new BigDecimal("100.00"), INICIO, TERMINO))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> CupomService.validarRegras(TipoDesconto.PERCENTUAL, new BigDecimal("100.01"), INICIO, TERMINO))
                .isInstanceOf(ApiException.class).hasMessageContaining("inválidos");
        assertThatCode(() -> CupomService.validarRegras(TipoDesconto.VALOR_FIXO, new BigDecimal("150.00"), INICIO, TERMINO))
                .as("o limite de 100 vale só para percentual").doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CT-CUP-010 Término igual ao início é rejeitado; 1 segundo depois é aceito")
    void limitePeriodo() {
        assertThatThrownBy(() -> CupomService.validarRegras(TipoDesconto.PERCENTUAL, BigDecimal.TEN, INICIO, INICIO))
                .isInstanceOf(ApiException.class);
        assertThatCode(() -> CupomService.validarRegras(TipoDesconto.PERCENTUAL, BigDecimal.TEN, INICIO, INICIO.plusSeconds(1)))
                .doesNotThrowAnyException();
    }

    private static Cupom cupom(TipoDesconto tipo, String valor) {
        Cupom cupom = new Cupom("DESCONTO10", INICIO.minusDays(1));
        cupom.setTipoDesconto(tipo);
        cupom.setValorDesconto(new BigDecimal(valor));
        cupom.setDataInicioValidade(INICIO);
        cupom.setDataTerminoValidade(TERMINO);
        return cupom;
    }
}
