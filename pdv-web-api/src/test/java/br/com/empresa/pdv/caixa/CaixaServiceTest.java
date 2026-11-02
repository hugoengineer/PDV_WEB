package br.com.empresa.pdv.caixa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.empresa.pdv.auditoria.AuditoriaService;
import br.com.empresa.pdv.auditoria.TipoAcao;
import br.com.empresa.pdv.comum.Relogio;
import br.com.empresa.pdv.comum.erro.ApiException;
import br.com.empresa.pdv.suporte.ContextoSeguranca;
import br.com.empresa.pdv.usuario.Perfil;
import br.com.empresa.pdv.venda.ModalidadePagamento;
import br.com.empresa.pdv.venda.VendaRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Serviço de caixa (unitário com Mockito)")
class CaixaServiceTest {

    @Mock private CaixaRepository caixas;
    @Mock private MovimentacaoCaixaRepository movimentacoes;
    @Mock private VendaRepository vendas;
    @Mock private AuditoriaService auditoria;

    private CaixaService service;

    @BeforeEach
    void preparar() {
        service = new CaixaService(caixas, movimentacoes, vendas, auditoria,
                new Relogio(Clock.systemUTC(), "America/Sao_Paulo"));
    }

    @AfterEach
    void limpar() {
        ContextoSeguranca.limpar();
    }

    @Test
    @DisplayName("CT-CX-U10 Abrir caixa com outro já aberto retorna 409 e não grava")
    void abrirComCaixaAberto() {
        ContextoSeguranca.autenticar(7L, Perfil.VENDEDOR);
        when(caixas.existsByUsuarioIdAndStatus(7L, StatusCaixa.ABERTO)).thenReturn(true);

        assertThatThrownBy(() -> service.abrir(new BigDecimal("100.00")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(409);
                    assertThat(e.getTipo()).isEqualTo("caixa-ja-aberto");
                });
        verify(caixas, never()).save(any());
    }

    @Test
    @DisplayName("CT-CX-U11 Sangria: igual ao saldo é aceita; 1 centavo acima é rejeitada (valor-limite)")
    void limiteDaSangria() {
        ContextoSeguranca.autenticar(1L, Perfil.GERENTE);
        Caixa caixa = caixaAberto(12L, 7L, "100.00");
        when(caixas.bloquear(12L)).thenReturn(Optional.of(caixa));
        // saldo = 100 (suprimento) + 50 (vendas em dinheiro) - 0 (sangrias) + 0 (suprimentos) = 150
        when(vendas.somarConcluidasDoCaixa(12L)).thenReturn(new BigDecimal("80.00"));
        when(vendas.somarConcluidasDoCaixaPorModalidade(12L, ModalidadePagamento.DINHEIRO)).thenReturn(new BigDecimal("50.00"));
        when(movimentacoes.somarPorTipo(12L, TipoMovimentacao.SANGRIA)).thenReturn(BigDecimal.ZERO);
        when(movimentacoes.somarPorTipo(12L, TipoMovimentacao.SUPRIMENTO)).thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.sangria(12L, new BigDecimal("150.01"), "Cofre"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getTipo()).isEqualTo("saldo-caixa-insuficiente"));
        verify(movimentacoes, never()).save(any());

        when(movimentacoes.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var resposta = service.sangria(12L, new BigDecimal("150.00"), "Cofre");
        assertThat(resposta.saldoEmDinheiroAtual()).isEqualByComparingTo("0.00");
        verify(auditoria).registrar(eq(1L),
                eq(TipoAcao.SANGRIA),
                eq("CAIXA"), eq(12L),
                anyString(), eq("150.00"),
                eq("0.00"));
    }

    @Test
    @DisplayName("CT-CX-U12 Venda em caixa de outro operador é negada (403)")
    void caixaDeOutroOperador() {
        when(caixas.bloquear(12L)).thenReturn(Optional.of(caixaAberto(12L, 99L, "0.00")));
        assertThatThrownBy(() -> service.validarCaixaParaVenda(12L, 7L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(403));
    }

    @Test
    @DisplayName("CT-CX-U13 Venda em caixa FECHADO é bloqueada com 422 caixa-fechado (VAL-05)")
    void caixaFechado() {
        Caixa caixa = caixaAberto(12L, 7L, "0.00");
        caixa.fechar(BigDecimal.ZERO, BigDecimal.ZERO, OffsetDateTime.now());
        when(caixas.bloquear(12L)).thenReturn(Optional.of(caixa));
        assertThatThrownBy(() -> service.validarCaixaParaVenda(12L, 7L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getTipo()).isEqualTo("caixa-fechado"));
    }

    @Test
    @DisplayName("CT-CX-U14 Fechamento: esperado = suprimento + suprimentos + vendas em dinheiro − sangrias; diferença e auditoria")
    void fechamentoCalculaEsperado() {
        ContextoSeguranca.autenticar(7L, Perfil.VENDEDOR);
        Caixa caixa = caixaAberto(12L, 7L, "100.00");
        when(caixas.bloquear(12L)).thenReturn(Optional.of(caixa));
        when(vendas.somarConcluidasDoCaixa(12L)).thenReturn(new BigDecimal("180.00"));
        when(vendas.somarConcluidasDoCaixaPorModalidade(12L, ModalidadePagamento.DINHEIRO)).thenReturn(new BigDecimal("50.00"));
        when(movimentacoes.somarPorTipo(12L, TipoMovimentacao.SANGRIA)).thenReturn(new BigDecimal("30.00"));
        when(movimentacoes.somarPorTipo(12L, TipoMovimentacao.SUPRIMENTO)).thenReturn(new BigDecimal("20.00"));

        var resposta = service.fechar(12L, new BigDecimal("135.00"));

        assertThat(resposta.saldoEsperadoDinheiro()).isEqualByComparingTo("140.00");
        assertThat(resposta.diferenca()).isEqualByComparingTo("-5.00");
        assertThat(resposta.totalVendas()).isEqualByComparingTo("180.00");
        assertThat(caixa.getStatus()).isEqualTo(StatusCaixa.FECHADO);
        verify(auditoria).registrar(eq(7L), eq(TipoAcao.CAIXA_FECHAMENTO), eq("CAIXA"), eq(12L), anyString());
    }

    @Test
    @DisplayName("CT-CX-U15 Suprimento soma ao saldo em dinheiro e é auditado com saldo anterior/novo")
    void suprimentoAuditado() {
        ContextoSeguranca.autenticar(1L, Perfil.GERENTE);
        Caixa caixa = caixaAberto(12L, 7L, "100.00");
        when(caixas.bloquear(12L)).thenReturn(Optional.of(caixa));
        when(vendas.somarConcluidasDoCaixa(12L)).thenReturn(BigDecimal.ZERO);
        when(vendas.somarConcluidasDoCaixaPorModalidade(12L, ModalidadePagamento.DINHEIRO)).thenReturn(BigDecimal.ZERO);
        when(movimentacoes.somarPorTipo(12L, TipoMovimentacao.SANGRIA)).thenReturn(BigDecimal.ZERO);
        when(movimentacoes.somarPorTipo(12L, TipoMovimentacao.SUPRIMENTO)).thenReturn(BigDecimal.ZERO);
        when(movimentacoes.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resposta = service.suprimento(12L, new BigDecimal("25.50"), "  Troco  ");

        assertThat(resposta.saldoEmDinheiroAtual()).isEqualByComparingTo("125.50");
        verify(auditoria).registrar(eq(1L), eq(TipoAcao.SUPRIMENTO), eq("CAIXA"), eq(12L), anyString(),
                eq("100.00"), eq("125.50"));
    }

    @Test
    @DisplayName("CT-CX-U16 Fechamento do caixa de outro operador: vendedor recebe 403 e o caixa segue aberto; gerente consegue fechar")
    void fechamentoDeCaixaAlheio() {
        Caixa caixa = caixaAberto(12L, 99L, "0.00");
        when(caixas.bloquear(12L)).thenReturn(Optional.of(caixa));

        ContextoSeguranca.autenticar(7L, Perfil.VENDEDOR);
        assertThatThrownBy(() -> service.fechar(12L, BigDecimal.ZERO))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(403));
        assertThat(caixa.getStatus()).isEqualTo(StatusCaixa.ABERTO);
        verify(auditoria, never()).registrar(any(), any(), any(), any(), anyString());

        ContextoSeguranca.autenticar(1L, Perfil.GERENTE);
        when(vendas.somarConcluidasDoCaixa(12L)).thenReturn(BigDecimal.ZERO);
        when(vendas.somarConcluidasDoCaixaPorModalidade(12L, ModalidadePagamento.DINHEIRO)).thenReturn(BigDecimal.ZERO);
        when(movimentacoes.somarPorTipo(12L, TipoMovimentacao.SANGRIA)).thenReturn(BigDecimal.ZERO);
        when(movimentacoes.somarPorTipo(12L, TipoMovimentacao.SUPRIMENTO)).thenReturn(BigDecimal.ZERO);
        service.fechar(12L, BigDecimal.ZERO);
        assertThat(caixa.getStatus()).isEqualTo(StatusCaixa.FECHADO);
    }

    private static Caixa caixaAberto(long id, long usuarioId, String suprimento) {
        Caixa caixa = new Caixa(usuarioId, new BigDecimal(suprimento), OffsetDateTime.now());
        ReflectionTestUtils.setField(caixa, "id", id);
        return caixa;
    }
}
