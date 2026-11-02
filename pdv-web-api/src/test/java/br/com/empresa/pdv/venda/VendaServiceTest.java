package br.com.empresa.pdv.venda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.assertj.core.groups.Tuple;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.empresa.pdv.auditoria.AuditoriaService;
import br.com.empresa.pdv.auditoria.TipoAcao;
import br.com.empresa.pdv.caixa.Caixa;
import br.com.empresa.pdv.caixa.CaixaService;
import br.com.empresa.pdv.cliente.Cliente;
import br.com.empresa.pdv.cliente.ClienteRepository;
import br.com.empresa.pdv.cliente.ClienteService;
import br.com.empresa.pdv.comum.Relogio;
import br.com.empresa.pdv.comum.erro.ApiException;
import br.com.empresa.pdv.cupom.CupomService;
import br.com.empresa.pdv.produto.Produto;
import br.com.empresa.pdv.produto.ProdutoRepository;
import br.com.empresa.pdv.seguranca.LimitadorDeTentativas;
import br.com.empresa.pdv.seguranca.PropriedadesTentativas;
import br.com.empresa.pdv.seguranca.TentativasExcedidasException;
import br.com.empresa.pdv.suporte.ContextoSeguranca;
import br.com.empresa.pdv.usuario.Perfil;
import br.com.empresa.pdv.usuario.Usuario;
import br.com.empresa.pdv.usuario.UsuarioRepository;
import br.com.empresa.pdv.venda.VendaDtos.ItemRequisicao;
import br.com.empresa.pdv.venda.VendaDtos.VendaRegistradaResposta;
import br.com.empresa.pdv.venda.VendaDtos.VendaRequisicao;

/**
 * Testes unitários do registro de venda (UC04) com dependências simuladas.
 * Objetivo principal: garantir que nenhuma falha de regra grave dados (verifyNoInteractions / never()).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Serviço de vendas (unitário com Mockito)")
class VendaServiceTest {

    private static final long VENDEDOR_ID = 7L;
    private static final long GERENTE_ID = 1L;
    private static final long CAIXA_ID = 12L;

    @Mock private VendaRepository vendas;
    @Mock private ProdutoRepository produtos;
    @Mock private ClienteRepository clientes;
    @Mock private ClienteService clienteService;
    @Mock private UsuarioRepository usuarios;
    @Mock private CaixaService caixaService;
    @Mock private CupomService cupomService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditoriaService auditoria;
    @Mock private LimitadorDeTentativas limitador;

    private VendaService service;
    private Usuario vendedor;
    private Cliente consumidorPadrao;

    @BeforeEach
    void preparar() {
        Clock fixo = Clock.fixed(Instant.parse("2026-09-13T17:30:00Z"), ZoneOffset.UTC);
        service = new VendaService(vendas, produtos, clientes, clienteService, usuarios, caixaService, cupomService,
                passwordEncoder, auditoria, new Relogio(fixo, "America/Sao_Paulo"), limitador, BaseComissao.LIQUIDO);

        vendedor = new Usuario("52998224725", "Jean Wictor", "hash", Perfil.VENDEDOR, new BigDecimal("5.00"),
                OffsetDateTime.now());
        ReflectionTestUtils.setField(vendedor, "id", VENDEDOR_ID);
        consumidorPadrao = new Cliente(OffsetDateTime.now());
        ReflectionTestUtils.setField(consumidorPadrao, "id", 1L);

        ContextoSeguranca.autenticar(VENDEDOR_ID, Perfil.VENDEDOR);
        lenient().when(usuarios.findById(VENDEDOR_ID)).thenReturn(Optional.of(vendedor));
    }

    @AfterEach
    void limpar() {
        ContextoSeguranca.limpar();
    }

    @Test
    @DisplayName("CT-VEN-U10 Estoque insuficiente: venda não é gravada e o estoque não muda (VAL-01)")
    void estoqueInsuficiente() {
        caixaValido();
        when(clienteService.consumidorPadrao()).thenReturn(consumidorPadrao);
        Produto tenis = produto(2L, "Tênis 41", "300.00", 1);
        when(produtos.bloquearPorIds(anyCollection())).thenReturn(List.of(tenis));

        assertThatThrownBy(() -> service.registrar(venda(null, null, item(2L, 3, null))))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(422);
                    assertThat(e.getTipo()).isEqualTo("estoque-insuficiente");
                    assertThat(e.getParametrosInvalidos()).singleElement().satisfies(p -> {
                        assertThat(p.name()).isEqualTo("produto_id: 2");
                        assertThat(p.reason()).isEqualTo("Estoque atual é 1, mas foi solicitado 3.");
                    });
                });
        assertThat(tenis.getEstoqueAtual()).isEqualTo(1);
        verify(vendas, never()).save(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("CT-VEN-U11 Caixa fechado bloqueia a venda antes de qualquer acesso ao estoque (VAL-05)")
    void caixaFechado() {
        when(caixaService.validarCaixaParaVenda(CAIXA_ID, VENDEDOR_ID))
                .thenThrow(ApiException.regraNegocio("caixa-fechado", "Caixa Fechado", "fechado"));

        assertThatThrownBy(() -> service.registrar(venda(null, null, item(2L, 1, null))))
                .isInstanceOf(ApiException.class).hasMessage("fechado");
        verifyNoInteractions(produtos, vendas, auditoria);
    }

    @Test
    @DisplayName("CT-VEN-U12 Vendedor com desconto direto sem senha do gerente recebe 403 (UC07)")
    void descontoSemAutorizacao() {
        caixaValido();
        when(clienteService.consumidorPadrao()).thenReturn(consumidorPadrao);

        assertThatThrownBy(() -> service.registrar(venda(new BigDecimal("10.00"), null, item(2L, 1, null))))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(403);
                    assertThat(e.getMessage()).isEqualTo(VendaService.DETALHE_DESCONTO_SEM_AUTORIZACAO);
                    assertThat(e.getParametrosInvalidos()).extracting("name")
                            .containsExactly("senha_autorizacao_gerente");
                });
        verifyNoInteractions(produtos, vendas);
    }

    @Test
    @DisplayName("CT-VEN-U13 Senha correta de um gerente ativo autoriza o desconto e é auditada")
    void descontoAutorizadoPorSenha() {
        caixaValido();
        when(clienteService.consumidorPadrao()).thenReturn(consumidorPadrao);
        Usuario gerente = new Usuario("11144477735", "Hugo", "hash-gerente", Perfil.GERENTE, BigDecimal.ZERO,
                OffsetDateTime.now());
        ReflectionTestUtils.setField(gerente, "id", GERENTE_ID);
        when(usuarios.findByPerfilAndAtivoTrue(Perfil.GERENTE)).thenReturn(List.of(gerente));
        when(passwordEncoder.matches("pin-do-gerente", "hash-gerente")).thenReturn(true);
        when(produtos.bloquearPorIds(anyCollection())).thenReturn(List.of(produto(2L, "Teclado", "250.00", 15)));
        when(vendas.save(any())).thenAnswer(inv -> comId(inv.getArgument(0), 89L));

        VendaRegistradaResposta resposta = service.registrar(
                venda(new BigDecimal("10.00"), "pin-do-gerente", item(2L, 1, null)));

        assertThat(resposta.valorTotal()).isEqualByComparingTo("240.00");
        ArgumentCaptor<Venda> salva = ArgumentCaptor.forClass(Venda.class);
        verify(vendas).save(salva.capture());
        assertThat(salva.getValue().getGerenteAutorizadorId()).isEqualTo(GERENTE_ID);
        verify(auditoria).registrar(eq(VENDEDOR_ID), eq(TipoAcao.DESCONTO_GERENTE), eq("VENDA"), eq(89L), anyString());
        verify(limitador).verificar(LimitadorDeTentativas.chaveSenhaGerente(VENDEDOR_ID));
        verify(limitador).limpar(LimitadorDeTentativas.chaveSenhaGerente(VENDEDOR_ID));
    }

    @Test
    @DisplayName("CT-VEN-U14 Preço enviado pelo cliente HTTP é ignorado; vale o preço do catálogo (ADR D-06)")
    void precoDoCatalogo() {
        caixaValido();
        when(clienteService.consumidorPadrao()).thenReturn(consumidorPadrao);
        when(produtos.bloquearPorIds(anyCollection())).thenReturn(List.of(produto(2L, "Teclado", "250.00", 15)));
        when(vendas.save(any())).thenAnswer(inv -> comId(inv.getArgument(0), 90L));

        VendaRegistradaResposta resposta = service.registrar(
                venda(null, null, item(2L, 2, new BigDecimal("0.01"))));

        assertThat(resposta.valorSubtotal()).isEqualByComparingTo("500.00");
        assertThat(resposta.comissaoVendedor()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("CT-VEN-U15 Itens repetidos do mesmo produto são somados antes da validação de estoque")
    void itensRepetidosSomados() {
        caixaValido();
        when(clienteService.consumidorPadrao()).thenReturn(consumidorPadrao);
        Produto meia = produto(3L, "Meia", "10.00", 3);
        when(produtos.bloquearPorIds(anyCollection())).thenReturn(List.of(meia));

        assertThatThrownBy(() -> service.registrar(venda(null, null, item(3L, 2, null), item(3L, 2, null))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getParametrosInvalidos())
                        .singleElement().extracting("reason").isEqualTo("Estoque atual é 3, mas foi solicitado 4."));
        assertThat(meia.getEstoqueAtual()).isEqualTo(3);
    }

    @Test
    @DisplayName("CT-VEN-U16 Produto inativo resulta em 404, mesmo com saldo")
    void produtoInativo() {
        caixaValido();
        when(clienteService.consumidorPadrao()).thenReturn(consumidorPadrao);
        Produto inativo = produto(5L, "Antigo", "10.00", 50);
        inativo.setAtivo(false);
        when(produtos.bloquearPorIds(anyCollection())).thenReturn(List.of(inativo));

        assertThatThrownBy(() -> service.registrar(venda(null, null, item(5L, 1, null))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(404));
        assertThat(inativo.getEstoqueAtual()).isEqualTo(50);
        verify(vendas, never()).save(any());
    }

    @Test
    @DisplayName("CT-VEN-U18 Produto inexistente resulta em 404")
    void produtoInexistente() {
        caixaValido();
        when(clienteService.consumidorPadrao()).thenReturn(consumidorPadrao);
        when(produtos.bloquearPorIds(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> service.registrar(venda(null, null, item(6L, 1, null))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(404));
        verify(vendas, never()).save(any());
    }

    @Test
    @DisplayName("CT-VEN-U19 Venda concluída baixa o estoque, grava os itens, monta a resposta e audita VENDA_REGISTRO")
    void vendaConcluidaEfeitos() {
        caixaValido();
        when(clienteService.consumidorPadrao()).thenReturn(consumidorPadrao);
        Produto teclado = produto(2L, "Teclado", "250.00", 15);
        Produto mouse = produto(3L, "Mouse", "80.00", 4);
        mouse.setEstoqueMinimo(3);
        when(produtos.bloquearPorIds(anyCollection())).thenReturn(List.of(teclado, mouse));
        when(vendas.save(any())).thenAnswer(inv -> comId(inv.getArgument(0), 92L));

        VendaRegistradaResposta resposta = service.registrar(venda(null, null, item(2L, 2, null), item(3L, 1, null)));

        assertThat(teclado.getEstoqueAtual()).isEqualTo(13);
        assertThat(mouse.getEstoqueAtual()).isEqualTo(3);
        ArgumentCaptor<Venda> salva = ArgumentCaptor.forClass(Venda.class);
        verify(vendas).save(salva.capture());
        assertThat(salva.getValue().getItens()).extracting(ItemVenda::getProdutoId, ItemVenda::getQuantidade)
                .containsExactly(Tuple.tuple(2L, 2), Tuple.tuple(3L, 1));
        assertThat(salva.getValue().isCancelada()).isFalse();
        assertThat(resposta.vendaId()).isEqualTo(92L);
        assertThat(resposta.status()).isEqualTo(StatusVenda.CONCLUIDA);
        assertThat(resposta.modalidadePagamento()).isEqualTo(ModalidadePagamento.PIX);
        assertThat(resposta.dataVenda()).isEqualTo(OffsetDateTime.parse("2026-09-13T14:30:00-03:00"));
        assertThat(resposta.valorTotal()).isEqualByComparingTo("580.00");
        assertThat(resposta.alertasEstoqueCritico())
                .containsExactly(new VendaDtos.AlertaEstoque(3L, "Mouse", 3, 3));
        verify(auditoria).registrar(eq(VENDEDOR_ID), eq(TipoAcao.VENDA_REGISTRO), eq("VENDA"), eq(92L), anyString());
        verify(auditoria, never()).registrar(any(), eq(TipoAcao.DESCONTO_GERENTE), any(), any(), anyString());
    }

    @Test
    @DisplayName("CT-VEN-U17 Venda sem cliente informado é atribuída ao Consumidor Padrão")
    void consumidorPadraoQuandoSemCliente() {
        caixaValido();
        when(clienteService.consumidorPadrao()).thenReturn(consumidorPadrao);
        when(produtos.bloquearPorIds(anyCollection())).thenReturn(List.of(produto(2L, "Caneta", "2.50", 100)));
        when(vendas.save(any())).thenAnswer(inv -> comId(inv.getArgument(0), 91L));

        service.registrar(venda(null, null, item(2L, 4, null)));

        ArgumentCaptor<Venda> salva = ArgumentCaptor.forClass(Venda.class);
        verify(vendas).save(salva.capture());
        assertThat(salva.getValue().getClienteId()).isEqualTo(1L);
        verify(clientes, never()).findById(anyLong());
    }

    @Test
    @DisplayName("CT-VEN-U20 Cancelamento estorna o estoque, muda o status e audita com o motivo")
    void cancelamentoEstorna() {
        ContextoSeguranca.autenticar(GERENTE_ID, Perfil.GERENTE);
        Venda venda = comId(VendaTestes.vendaConcluida(), 50L); // 3 unidades do produto 10
        Produto produto = produto(10L, "Item", "19.99", 5);
        when(vendas.bloquear(50L)).thenReturn(Optional.of(venda));
        when(produtos.bloquearPorIds(anyCollection())).thenReturn(List.of(produto));

        service.cancelar(50L, "  Desistência do cliente  ");

        assertThat(produto.getEstoqueAtual()).isEqualTo(8);
        assertThat(venda.isCancelada()).isTrue();
        assertThat(venda.getMotivoCancelamento()).isEqualTo("Desistência do cliente");
        verify(auditoria).registrar(eq(GERENTE_ID), eq(TipoAcao.CANCELAMENTO_VENDA), eq("VENDA"), eq(50L),
                contains("Estorno: produto 10 +3."), eq("CONCLUIDA"), eq("CANCELADA"));
    }

    @Test
    @DisplayName("CT-VEN-U21 Venda já cancelada: 422 e nenhum estorno (estoque não é devolvido duas vezes)")
    void cancelamentoDuplo() {
        ContextoSeguranca.autenticar(GERENTE_ID, Perfil.GERENTE);
        Venda venda = VendaTestes.vendaConcluida();
        VendaTestes.cancelar(venda, "Primeiro", GERENTE_ID, OffsetDateTime.now());
        when(vendas.bloquear(51L)).thenReturn(Optional.of(venda));

        assertThatThrownBy(() -> service.cancelar(51L, "Segundo"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getTipo()).isEqualTo("venda-ja-cancelada"));
        verifyNoInteractions(produtos, auditoria);
    }

    @Test
    @DisplayName("CT-VEN-U22 Vendedor pedindo a comissão de outro vendedor recebe 403 sem consulta ao banco")
    void comissaoDeOutroVendedor() {
        assertThatThrownBy(() -> service.comissoes(null, null, 99L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(403));
        verifyNoInteractions(vendas);
    }

    @Test
    @DisplayName("CT-VEN-U23 Consolidado do gerente inclui vendedores ativos sem vendas, com valores zerados")
    void consolidadoDoGerente() {
        ContextoSeguranca.autenticar(GERENTE_ID, Perfil.GERENTE);
        Usuario semVendas = new Usuario("11144477735", "Cesar", "h", Perfil.VENDEDOR, new BigDecimal("3.00"),
                OffsetDateTime.now());
        ReflectionTestUtils.setField(semVendas, "id", 8L);
        when(vendas.consolidarPorVendedor(any(), any(), isNull())).thenReturn(List.of(
                new ConsolidadoVendedor(VENDEDOR_ID, 2, new BigDecimal("300.00"), new BigDecimal("15.00"))));
        when(usuarios.findByPerfilAndAtivoTrue(Perfil.VENDEDOR)).thenReturn(List.of(vendedor, semVendas));
        when(usuarios.findAllById(anyCollection())).thenReturn(List.of(vendedor, semVendas));

        @SuppressWarnings("unchecked")
        List<VendaDtos.ComissaoResposta> lista = (List<VendaDtos.ComissaoResposta>) service.comissoes(null, null, null);

        assertThat(lista).extracting(VendaDtos.ComissaoResposta::vendedorId).containsExactly(VENDEDOR_ID, 8L);
        assertThat(lista.get(0).totalComissaoAcumulada()).isEqualByComparingTo("15.00");
        assertThat(lista.get(1).totalVendasRealizadas()).isZero();
        assertThat(lista.get(1).valorTotalVendido()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("CT-VEN-U24 Senha do gerente: bloqueio por excesso dá 429 sem conferir senhas; erro conta falha e audita AUTORIZACAO_FALHA")
    void limiteDaSenhaDoGerente() {
        String chave = LimitadorDeTentativas.chaveSenhaGerente(VENDEDOR_ID);
        caixaValido();
        when(clienteService.consumidorPadrao()).thenReturn(consumidorPadrao);
        doThrow(new TentativasExcedidasException(300)).when(limitador).verificar(chave);

        assertThatThrownBy(() -> service.registrar(venda(new BigDecimal("10.00"), "chute", item(2L, 1, null))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(429));
        verify(passwordEncoder, never()).matches(anyString(), anyString());

        doNothing().when(limitador).verificar(chave);
        Usuario gerente = new Usuario("11144477735", "Hugo", "hash-gerente", Perfil.GERENTE, BigDecimal.ZERO,
                OffsetDateTime.now());
        when(usuarios.findByPerfilAndAtivoTrue(Perfil.GERENTE)).thenReturn(List.of(gerente));
        when(limitador.propriedades()).thenReturn(new PropriedadesTentativas(null, null, null, null, null));
        when(limitador.registrarFalha(eq(chave), anyInt())).thenReturn(true);

        assertThatThrownBy(() -> service.registrar(venda(new BigDecimal("10.00"), "errada", item(2L, 1, null))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(403));
        verify(auditoria).registrarIsolado(eq(VENDEDOR_ID), eq(TipoAcao.AUTORIZACAO_FALHA), eq("USUARIO"),
                eq(VENDEDOR_ID), contains("bloqueada temporariamente"));
        verify(limitador, never()).limpar(anyString());
        verifyNoInteractions(produtos, vendas);
    }

    @Test
    @DisplayName("CT-VEN-U25 Comissões: período invertido → 400 sem consulta; datas viram limites do dia no fuso da loja")
    void periodoDasComissoes() {
        assertThatThrownBy(() -> service.comissoes(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1), null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(400));
        verifyNoInteractions(vendas);

        when(vendas.consolidarPorVendedor(any(), any(), eq(VENDEDOR_ID))).thenReturn(List.of());
        var propria = (VendaDtos.ComissaoResposta) service.comissoes(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null);
        assertThat(propria.vendedorId()).isEqualTo(VENDEDOR_ID);
        assertThat(propria.totalComissaoAcumulada()).isEqualByComparingTo("0.00");
        verify(vendas).consolidarPorVendedor(OffsetDateTime.parse("2026-09-01T00:00-03:00"),
                OffsetDateTime.parse("2026-10-01T00:00-03:00"), VENDEDOR_ID);

        service.comissoes(null, null, null);
        ArgumentCaptor<OffsetDateTime> de = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> ate = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(vendas, times(2)).consolidarPorVendedor(de.capture(), ate.capture(), eq(VENDEDOR_ID));
        assertThat(de.getValue()).as("sem data_inicio: desde sempre").isBefore(OffsetDateTime.parse("2000-01-01T00:00Z"));
        assertThat(ate.getValue()).as("sem data_fim: até sempre").isAfter(OffsetDateTime.parse("2100-01-01T00:00Z"));
    }

    // ------------------------------------------------------------------ apoio

    private void caixaValido() {
        when(caixaService.validarCaixaParaVenda(CAIXA_ID, VENDEDOR_ID))
                .thenReturn(comIdCaixa(new Caixa(VENDEDOR_ID, BigDecimal.TEN, OffsetDateTime.now())));
    }

    private static Caixa comIdCaixa(Caixa caixa) {
        ReflectionTestUtils.setField(caixa, "id", CAIXA_ID);
        return caixa;
    }

    private static Venda comId(Venda venda, long id) {
        ReflectionTestUtils.setField(venda, "id", id);
        return venda;
    }

    private static Produto produto(long id, String nome, String preco, int estoque) {
        Produto p = new Produto("SKU-" + id, OffsetDateTime.now());
        ReflectionTestUtils.setField(p, "id", id);
        p.setNome(nome);
        p.setPrecoVenda(new BigDecimal(preco));
        p.setEstoqueAtual(estoque);
        p.setEstoqueMinimo(0);
        return p;
    }

    private static ItemRequisicao item(long produtoId, int quantidade, BigDecimal preco) {
        return new ItemRequisicao(produtoId, quantidade, preco);
    }

    private static VendaRequisicao venda(BigDecimal descontoGerente, String senhaGerente, ItemRequisicao... itens) {
        return new VendaRequisicao(CAIXA_ID, null, null, descontoGerente, senhaGerente, ModalidadePagamento.PIX,
                List.of(itens));
    }
}
