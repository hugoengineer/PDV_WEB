package br.com.empresa.pdv.venda;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Acesso de teste aos construtores/métodos de pacote de {@link Venda}. */
public final class VendaTestes {

    private VendaTestes() {
    }

    /** Venda de 3 × R$ 19,99 = R$ 59,97, sem descontos. */
    public static Venda vendaConcluida() {
        Venda venda = new Venda(1L, 1L, 1L, ModalidadePagamento.PIX, OffsetDateTime.now());
        venda.adicionarItem(10L, 3, new BigDecimal("19.99"));
        venda.aplicarValores(CalculadoraVenda.calcular(new BigDecimal("59.97"), null, null, BigDecimal.ONE), null, null);
        return venda;
    }

    public static void cancelar(Venda venda, String motivo, Long gerenteId, OffsetDateTime quando) {
        venda.cancelar(motivo, gerenteId, quando);
    }
}
