package br.com.empresa.pdv.auditoria;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.empresa.pdv.suporte.TesteIntegracao;

/** UC16 — Consultar Logs de Auditoria e Regra 1.2 (LOG obrigatório de ações sensíveis). */
@DisplayName("Auditoria (integração)")
class AuditoriaIT extends TesteIntegracao {

    @Test
    @DisplayName("CT-AUD-001 Fluxo completo gera um LOG para cada ação sensível da Regra 1.2")
    void acoesSensiveisAuditadas() throws Exception {
        Operador vendedor = criarVendedor("Jean", "5");
        long caixa = abrirCaixa(vendedor.token(), "100.00");
        long produto = criarProduto("AUD-1", "Item", "50.00", 10, 1);
        remendar("/produtos/" + produto, tokenGerente, "{\"preco_venda\": 55.00}").andExpect(status().isOk());
        criarCliente(vendedor.token());
        long venda = lerId(postar("/vendas", vendedor.token(), vendaSimples(caixa, produto, 1)), "$.venda_id");
        postar("/caixas/" + caixa + "/sangria", tokenGerente, "{\"valor\": 10.00, \"observacao\": \"Cofre\"}")
                .andExpect(status().isOk());
        postar("/vendas/" + venda + "/cancelar", tokenGerente, "{\"motivo_cancelamento\": \"Teste de auditoria\"}")
                .andExpect(status().isOk());
        postar("/caixas/" + caixa + "/fechamento", vendedor.token(), "{\"valor_final_dinheiro\": 90.00}")
                .andExpect(status().isOk());

        for (String tipo : new String[]{"CAIXA_ABERTURA", "PRODUTO_CREATE", "PRODUTO_UPDATE", "CLIENTE_CREATE",
                "VENDA_REGISTRO", "SANGRIA", "CANCELAMENTO_VENDA", "CAIXA_FECHAMENTO"}) {
            org.assertj.core.api.Assertions.assertThat(contarAuditoria(tipo)).as(tipo).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("CT-AUD-002 Gerente consulta logs filtrando por tipo_acao, mais recentes primeiro")
    void consultaFiltrada() throws Exception {
        criarProduto("AUD-2", "Item", "1.00", 1, 0);
        criarProduto("AUD-3", "Item", "1.00", 1, 0);
        obter("/auditoria?tipo_acao=PRODUTO_CREATE", tokenGerente)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_registros").value(2))
                .andExpect(jsonPath("$.dados[0].tipo_acao").value("PRODUTO_CREATE"))
                .andExpect(jsonPath("$.dados[0].usuario_id").value(idGerente))
                .andExpect(jsonPath("$.dados[0].detalhes").value(org.hamcrest.Matchers.containsString("AUD-3")));
    }

    @Test
    @DisplayName("CT-AUD-003 Tipo de ação inexistente no filtro retorna 400")
    void tipoInvalido() throws Exception {
        obter("/auditoria?tipo_acao=APAGAR_TUDO", tokenGerente).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CT-AUD-004 Operação que falha não deixa registro de auditoria (transação única)")
    void falhaNaoAudita() throws Exception {
        Operador vendedor = criarVendedor("Jean", "5");
        long caixa = abrirCaixa(vendedor.token(), "0");
        long produto = criarProduto("AUD-4", "Item", "1.00", 1, 0);
        postar("/vendas", vendedor.token(), vendaSimples(caixa, produto, 5)).andExpect(status().isUnprocessableContent());
        org.assertj.core.api.Assertions.assertThat(contarAuditoria("VENDA_REGISTRO")).isZero();
    }

    @Test
    @DisplayName("CT-AUD-005 Filtro por período: hoje traz os logs; período passado vem vazio; período invertido → 400")
    void filtroPorPeriodo() throws Exception {
        criarProduto("AUD-5", "Item", "1.00", 1, 0);
        java.time.LocalDate hoje = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
        // O log é imutável (trigger da V4); só o preparo do cenário desliga a proteção por um instante
        jdbc.execute("ALTER TABLE logs_auditoria DISABLE TRIGGER tg_logs_auditoria_imutavel");
        try {
            jdbc.update("UPDATE logs_auditoria SET data_hora = data_hora - interval '10 days' WHERE tipo_acao = 'LOGIN_SUCESSO'");
        } finally {
            jdbc.execute("ALTER TABLE logs_auditoria ENABLE TRIGGER tg_logs_auditoria_imutavel");
        }

        obter("/auditoria?data_inicio=" + hoje + "&data_fim=" + hoje + "&tipo_acao=PRODUTO_CREATE", tokenGerente)
                .andExpect(jsonPath("$.total_registros").value(1));
        obter("/auditoria?data_inicio=" + hoje.minusDays(30) + "&data_fim=" + hoje.minusDays(20), tokenGerente)
                .andExpect(jsonPath("$.total_registros").value(0));
        obter("/auditoria?data_fim=" + hoje.minusDays(5), tokenGerente)
                .andExpect(jsonPath("$.dados[0].tipo_acao").value("LOGIN_SUCESSO"));
        obter("/auditoria?data_inicio=" + hoje + "&data_fim=" + hoje.minusDays(1), tokenGerente)
                .andExpect(status().isBadRequest());
    }
}
