package br.com.empresa.pdv.venda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.empresa.pdv.suporte.TesteIntegracao;

/** UC11 Cancelar Venda (VAL-02/VAL-03), histórico de vendas e UC10 Relatório de Comissões. */
@DisplayName("Cancelamento, histórico e comissões (integração)")
class CancelamentoEConsultaIT extends TesteIntegracao {

    private static final String MOTIVO = """
            {"motivo_cancelamento": "Desistência do cliente antes da retirada."}""";

    private Operador vendedor;
    private long caixa;
    private long produto;

    @BeforeEach
    void preparar() throws Exception {
        vendedor = criarVendedor("Jean Wictor", "5.0");
        caixa = abrirCaixa(vendedor.token(), "100.00");
        produto = criarProduto("PROD-CANC", "Teclado", "250.00", 15, 3);
    }

    @Test
    @DisplayName("CT-CAN-001 Vendedor tenta cancelar a venda → 403 \"Acesso Negado\" (Regras, seção 3)")
    void vendedorNaoCancela() throws Exception {
        long venda = vender(2);
        postar("/vendas/" + venda + "/cancelar", vendedor.token(), MOTIVO)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acesso Negado"));
        assertThat(estoqueDe(produto)).isEqualTo(13);
    }

    @Test
    @DisplayName("CT-CAN-002 Gerente cancela: status CANCELADA, estoque estornado e LOG CANCELAMENTO_VENDA (VAL-03)")
    void gerenteCancela() throws Exception {
        long venda = vender(2);
        assertThat(estoqueDe(produto)).isEqualTo(13);

        postar("/vendas/" + venda + "/cancelar", tokenGerente, MOTIVO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").value("Venda cancelada com sucesso. Estoque estornado."))
                .andExpect(jsonPath("$.status").value("CANCELADA"));

        assertThat(estoqueDe(produto)).isEqualTo(15);
        obter("/vendas/" + venda, tokenGerente)
                .andExpect(jsonPath("$.status").value("CANCELADA"))
                .andExpect(jsonPath("$.motivo_cancelamento").value("Desistência do cliente antes da retirada."));
        String detalhes = jdbc.queryForObject(
                "SELECT detalhes FROM logs_auditoria WHERE tipo_acao = 'CANCELAMENTO_VENDA' AND entidade_id = ?",
                String.class, venda);
        assertThat(detalhes).contains("Desistência do cliente").contains("produto " + produto + " +2");
    }

    @Test
    @DisplayName("CT-CAN-003 Venda já CANCELADA não pode ser cancelada de novo; o estoque não é estornado duas vezes")
    void cancelarDuasVezes() throws Exception {
        long venda = vender(2);
        postar("/vendas/" + venda + "/cancelar", tokenGerente, MOTIVO).andExpect(status().isOk());
        postar("/vendas/" + venda + "/cancelar", tokenGerente, MOTIVO)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("https://api.pdvweb.com/erros/venda-ja-cancelada"));
        assertThat(estoqueDe(produto)).isEqualTo(15);
    }

    @Test
    @DisplayName("CT-CAN-004 Motivo do cancelamento é obrigatório (mínimo 5 caracteres)")
    void motivoObrigatorio() throws Exception {
        long venda = vender(1);
        postar("/vendas/" + venda + "/cancelar", tokenGerente, "{\"motivo_cancelamento\": \"abc\"}")
                .andExpect(status().isBadRequest());
        postar("/vendas/" + venda + "/cancelar", tokenGerente, "{}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CT-CAN-005 Venda cancelada sai dos totais do caixa e das comissões (débito no relatório)")
    void canceladaForaDosTotais() throws Exception {
        vender(1);
        long cancelada = vender(1);
        postar("/vendas/" + cancelada + "/cancelar", tokenGerente, MOTIVO).andExpect(status().isOk());

        obter("/caixas/atual", vendedor.token())
                .andExpect(jsonPath("$.total_vendas").value(250.00))
                .andExpect(jsonPath("$.saldo_em_dinheiro_atual").value(350.00));
        obter("/vendas/comissoes", vendedor.token())
                .andExpect(jsonPath("$.total_vendas_realizadas").value(1))
                .andExpect(jsonPath("$.total_comissao_acumulada").value(12.50));
    }

    @Test
    @DisplayName("CT-CAN-006 Cancelar venda inexistente retorna 404")
    void inexistente() throws Exception {
        postar("/vendas/999/cancelar", tokenGerente, MOTIVO).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CT-HIS-001 Vendedor vê só as próprias vendas; Gerente vê todas e filtra por usuario_id")
    void historicoPorPerfil() throws Exception {
        Operador outro = criarVendedor("Cesar", "3");
        long caixaOutro = abrirCaixa(outro.token(), "0");
        vender(1);
        postar("/vendas", outro.token(), vendaSimples(caixaOutro, produto, 1)).andExpect(status().isCreated());

        obter("/vendas", vendedor.token())
                .andExpect(jsonPath("$.total_registros").value(1))
                .andExpect(jsonPath("$.dados[0].vendedor").value("Jean Wictor"));
        obter("/vendas", tokenGerente).andExpect(jsonPath("$.total_registros").value(2));
        obter("/vendas?usuario_id=" + outro.id(), tokenGerente).andExpect(jsonPath("$.dados[0].vendedor").value("Cesar"));
        obter("/vendas?usuario_id=" + outro.id(), vendedor.token()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CT-HIS-002 Vendedor não acessa o detalhe da venda de outro vendedor (IDOR → 403)")
    void detalheDeOutro() throws Exception {
        Operador outro = criarVendedor("Cesar", "3");
        long venda = vender(1);
        obter("/vendas/" + venda, outro.token()).andExpect(status().isForbidden());
        obter("/vendas/" + venda, vendedor.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].produto").value("Teclado"))
                .andExpect(jsonPath("$.itens[0].preco_unitario").value(250.00));
    }

    @Test
    @DisplayName("CT-HIS-003 Filtro por período: data_fim anterior a data_inicio → 400; período sem vendas → vazio")
    void filtroPeriodo() throws Exception {
        vender(1);
        LocalDate hoje = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
        obter("/vendas?data_inicio=" + hoje + "&data_fim=" + hoje, tokenGerente)
                .andExpect(jsonPath("$.total_registros").value(1));
        obter("/vendas?data_inicio=" + hoje.minusDays(10) + "&data_fim=" + hoje.minusDays(5), tokenGerente)
                .andExpect(jsonPath("$.total_registros").value(0));
        obter("/vendas?data_inicio=" + hoje + "&data_fim=" + hoje.minusDays(1), tokenGerente)
                .andExpect(status().isBadRequest());
        obter("/vendas?data_inicio=13-09-2026", tokenGerente).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CT-COM-001 Comissão do vendedor (exemplo do contrato): 4.500,00 vendidos × 5% = 225,00")
    void comissaoExemploContrato() throws Exception {
        long item = criarProduto("P-300", "Item 300", "300.00", 100, 0);
        for (int i = 0; i < 15; i++) {
            postar("/vendas", vendedor.token(), vendaSimples(caixa, item, 1).replace("DINHEIRO", "PIX"))
                    .andExpect(status().isCreated());
        }
        obter("/vendas/comissoes", vendedor.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendedor_id").value(vendedor.id()))
                .andExpect(jsonPath("$.vendedor_nome").value("Jean Wictor"))
                .andExpect(jsonPath("$.total_vendas_realizadas").value(15))
                .andExpect(jsonPath("$.valor_total_vendido").value(4500.00))
                .andExpect(jsonPath("$.percentual_comissao").value(5.0))
                .andExpect(jsonPath("$.total_comissao_acumulada").value(225.00));
    }

    @Test
    @DisplayName("CT-COM-002 Vendedor não consulta comissão de outro (403); Gerente vê o consolidado de todos")
    void comissaoPorPerfil() throws Exception {
        Operador outro = criarVendedor("Cesar", "3");
        vender(1);
        obter("/vendas/comissoes?vendedor_id=" + outro.id(), vendedor.token()).andExpect(status().isForbidden());
        obter("/vendas/comissoes", tokenGerente)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.vendedor_nome == 'Cesar')].total_vendas_realizadas").value(0))
                .andExpect(jsonPath("$[?(@.vendedor_nome == 'Jean Wictor')].total_comissao_acumulada").value(12.50));
        obter("/vendas/comissoes?vendedor_id=" + vendedor.id(), tokenGerente)
                .andExpect(jsonPath("$.total_vendas_realizadas").value(1));
    }

    @Test
    @DisplayName("CT-COM-003 Mudança de % de comissão não altera vendas já registradas (percentual congelado)")
    void percentualCongelado() throws Exception {
        vender(1);
        remendar("/usuarios/" + vendedor.id(), tokenGerente, "{\"percentual_comissao\": 10}").andExpect(status().isOk());
        vender(1);
        obter("/vendas/comissoes", vendedor.token())
                .andExpect(jsonPath("$.total_comissao_acumulada").value(37.50))
                .andExpect(jsonPath("$.percentual_comissao").value(10.0));
    }

    private long vender(int quantidade) throws Exception {
        return lerId(postar("/vendas", vendedor.token(), vendaSimples(caixa, produto, quantidade))
                .andExpect(status().isCreated()), "$.venda_id");
    }
}
