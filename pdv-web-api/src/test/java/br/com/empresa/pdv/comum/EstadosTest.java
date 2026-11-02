package br.com.empresa.pdv.comum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import br.com.empresa.pdv.caixa.Caixa;
import br.com.empresa.pdv.caixa.StatusCaixa;
import br.com.empresa.pdv.venda.VendaTestes;

/**
 * Técnica: testes baseados em estados.
 * Caixa: ABERTO --fechar--> FECHADO (FECHADO não aceita novo fechamento).
 * Venda: CONCLUIDA --cancelar--> CANCELADA (CANCELADA não aceita novo cancelamento).
 */
@DisplayName("Transições de estado de Caixa e Venda")
class EstadosTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-09-13T18:00:00-03:00");

    @ParameterizedTest(name = "{displayName} [{index}] esperado={0}, contado={1} → diferença={2}")
    @CsvSource({"300.00, 300.00, 0.00", "300.00, 310.50, 10.50", "300.00, 290.00, -10.00"})
    @DisplayName("CT-CX-U01 Fechamento ABERTO→FECHADO calcula a diferença (sobra, falta ou zero)")
    void fecharCaixa(String esperado, String contado, String diferenca) {
        Caixa caixa = new Caixa(1L, new BigDecimal("100.00"), AGORA.minusHours(10));
        caixa.fechar(new BigDecimal(contado), new BigDecimal(esperado), AGORA);

        assertThat(caixa.getStatus()).isEqualTo(StatusCaixa.FECHADO);
        assertThat(caixa.getDiferenca()).isEqualByComparingTo(diferenca);
        assertThat(caixa.getDataFechamento()).isEqualTo(AGORA);
    }

    @Test
    @DisplayName("CT-CX-U02 Caixa FECHADO não pode ser fechado novamente")
    void fecharCaixaFechado() {
        Caixa caixa = new Caixa(1L, BigDecimal.TEN, AGORA.minusHours(1));
        caixa.fechar(BigDecimal.TEN, BigDecimal.TEN, AGORA);
        assertThatThrownBy(() -> caixa.fechar(BigDecimal.ONE, BigDecimal.TEN, AGORA))
                .isInstanceOf(IllegalStateException.class);
        assertThat(caixa.getValorFinalInformado()).as("o primeiro fechamento é preservado").isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("CT-VEN-U01 Venda CONCLUIDA→CANCELADA registra motivo, gerente e data")
    void cancelarVenda() {
        var venda = VendaTestes.vendaConcluida();
        VendaTestes.cancelar(venda, "Desistência do cliente", 9L, AGORA);

        assertThat(venda.isCancelada()).isTrue();
        assertThat(venda.getMotivoCancelamento()).isEqualTo("Desistência do cliente");
        assertThat(venda.getDataCancelamento()).isEqualTo(AGORA);
    }

    @Test
    @DisplayName("CT-VEN-U02 Venda CANCELADA não pode ser cancelada novamente")
    void cancelarVendaCancelada() {
        var venda = VendaTestes.vendaConcluida();
        VendaTestes.cancelar(venda, "Primeiro cancelamento", 9L, AGORA);
        assertThatThrownBy(() -> VendaTestes.cancelar(venda, "Segundo cancelamento", 9L, AGORA))
                .isInstanceOf(IllegalStateException.class);
        assertThat(venda.getMotivoCancelamento()).isEqualTo("Primeiro cancelamento");
    }

    @Test
    @DisplayName("CT-VEN-U03 Subtotal do item = preço × quantidade, arredondado em centavos")
    void subtotalItem() {
        var venda = VendaTestes.vendaConcluida();
        assertThat(venda.getItens().getFirst().getSubtotal()).isEqualByComparingTo("59.97");
    }
}
