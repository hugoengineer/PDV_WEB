package br.com.empresa.pdv.venda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import br.com.empresa.pdv.suporte.TesteIntegracao;

/**
 * Ciclo 3 — RF-04 (várias formas de pagamento, observação), RF-17 (parcelamento sem juros configurado pelo
 * gerente) e o efeito no fechamento de caixa (RF-03): só a parte paga em dinheiro entra no saldo esperado.
 */
@DisplayName("Pagamentos divididos, parcelamento e observação (integração)")
class PagamentosIT extends TesteIntegracao {

    private Operador vendedor;
    private long caixa;
    private long produto;

    @BeforeEach
    void preparar() throws Exception {
        vendedor = criarVendedor("Jean Wictor", "5.0");
        caixa = abrirCaixa(vendedor.token(), "100.00");
        produto = criarProduto("PROD-PAG", "Fone de ouvido", "100.00", 20, 2);
    }

    private String venda(int quantidade, String pagamentos, String extra) {
        return """
                {"caixa_id": %d, "itens": [{"produto_id": %d, "quantidade": %d}], "pagamentos": %s %s}"""
                .formatted(caixa, produto, quantidade, pagamentos, extra);
    }

    @Test
    @DisplayName("CT-PAG-001 Pagamento dividido: R$ 150 em dinheiro + R$ 150 no crédito em 3x sem juros (venda MULTIPLA)")
    void pagamentoDividido() throws Exception {
        var r = postar("/vendas", vendedor.token(), venda(3, """
                [{"modalidade_pagamento": "DINHEIRO", "valor": 150.00},
                 {"modalidade_pagamento": "CARTAO_CREDITO", "valor": 150.00, "parcelas": 3}]""", ""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valor_total").value(300.00))
                .andExpect(jsonPath("$.modalidade_pagamento").value("MULTIPLA"))
                .andExpect(jsonPath("$.pagamentos", hasSize(2)))
                .andExpect(jsonPath("$.pagamentos[1].parcelas").value(3))
                .andExpect(jsonPath("$.pagamentos[1].valor_parcela").value(50.00));
        long id = lerId(r, "$.venda_id");
        obter("/vendas/" + id, vendedor.token())
                .andExpect(jsonPath("$.pagamentos[0].modalidade_pagamento").value("DINHEIRO"))
                .andExpect(jsonPath("$.pagamentos[0].valor").value(150.00))
                .andExpect(jsonPath("$.pagamentos[1].valor_ultima_parcela").value(50.00));
        assertThat(contar("venda_pagamentos")).isEqualTo(2);
    }

    @Test
    @DisplayName("CT-PAG-002 Parcelas sem juros não exatas: R$ 100 em 3x = 33,33 + 33,33 + 33,34")
    void parcelasComCentavos() throws Exception {
        postar("/vendas", vendedor.token(), venda(1, """
                [{"modalidade_pagamento": "CARTAO_CREDITO", "valor": 100.00, "parcelas": 3}]""", ""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modalidade_pagamento").value("CARTAO_CREDITO"))
                .andExpect(jsonPath("$.pagamentos[0].valor_parcela").value(33.33))
                .andExpect(jsonPath("$.pagamentos[0].valor_ultima_parcela").value(33.34));
    }

    @Test
    @DisplayName("CT-PAG-003 Soma dos pagamentos diferente do total (1 centavo) → 422 pagamento-divergente; nada é gravado")
    void somaDivergente() throws Exception {
        postar("/vendas", vendedor.token(), venda(2, """
                [{"modalidade_pagamento": "DINHEIRO", "valor": 100.00},
                 {"modalidade_pagamento": "PIX", "valor": 99.99}]""", ""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("https://api.pdvweb.com/erros/pagamento-divergente"));
        postar("/vendas", vendedor.token(), venda(2, """
                [{"modalidade_pagamento": "DINHEIRO", "valor": 200.01}]""", ""))
                .andExpect(status().isUnprocessableContent());
        assertThat(estoqueDe(produto)).isEqualTo(20);
        assertThat(contar("vendas")).isZero();
        assertThat(contar("venda_pagamentos")).isZero();
    }

    @Test
    @DisplayName("CT-PAG-004 Parcelas acima do máximo do gerente → 422; após o gerente liberar, a venda é aceita")
    void limiteDeParcelas() throws Exception {
        colocar("/configuracoes/parcelamento/CARTAO_CREDITO", tokenGerente, "{\"max_parcelas\": 4}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.max_parcelas").value(4));
        String seisVezes = venda(1, """
                [{"modalidade_pagamento": "CARTAO_CREDITO", "valor": 100.00, "parcelas": 6}]""", "");
        postar("/vendas", vendedor.token(), seisVezes)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("https://api.pdvweb.com/erros/parcelamento-invalido"));

        colocar("/configuracoes/parcelamento/CARTAO_CREDITO", tokenGerente, "{\"max_parcelas\": 6}")
                .andExpect(status().isOk());
        postar("/vendas", vendedor.token(), seisVezes).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("CT-PAG-005 Dinheiro, PIX e débito não parcelam (422); 13 parcelas é recusado (400)")
    void formasAVista() throws Exception {
        for (String forma : new String[] {"DINHEIRO", "PIX", "CARTAO_DEBITO"}) {
            postar("/vendas", vendedor.token(), venda(1, """
                    [{"modalidade_pagamento": "%s", "valor": 100.00, "parcelas": 2}]""".formatted(forma), ""))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.type").value("https://api.pdvweb.com/erros/parcelamento-invalido"));
        }
        postar("/vendas", vendedor.token(), venda(1, """
                [{"modalidade_pagamento": "CARTAO_CREDITO", "valor": 100.00, "parcelas": 13}]""", ""))
                .andExpect(status().isBadRequest());
        assertThat(contar("vendas")).isZero();
    }

    @Test
    @DisplayName("CT-PAG-006 Forma única e lista juntas (400); nenhuma forma (400); MULTIPLA não é forma de pagamento (400)")
    void formatoDoPagamento() throws Exception {
        postar("/vendas", vendedor.token(), """
                {"caixa_id": %d, "modalidade_pagamento": "PIX", "itens": [{"produto_id": %d, "quantidade": 1}],
                 "pagamentos": [{"modalidade_pagamento": "PIX", "valor": 100.00}]}""".formatted(caixa, produto))
                .andExpect(status().isBadRequest());
        postar("/vendas", vendedor.token(), """
                {"caixa_id": %d, "itens": [{"produto_id": %d, "quantidade": 1}]}""".formatted(caixa, produto))
                .andExpect(status().isBadRequest());
        postar("/vendas", vendedor.token(), venda(1, """
                [{"modalidade_pagamento": "MULTIPLA", "valor": 100.00}]""", ""))
                .andExpect(status().isBadRequest());
        assertThat(contar("vendas")).isZero();
    }

    @Test
    @DisplayName("CT-PAG-007 Caixa: só a parte em dinheiro entra no saldo esperado (R$ 40 dinheiro + R$ 60 PIX → saldo 140)")
    void caixaSomaSoDinheiro() throws Exception {
        postar("/vendas", vendedor.token(), venda(1, """
                [{"modalidade_pagamento": "DINHEIRO", "valor": 40.00},
                 {"modalidade_pagamento": "PIX", "valor": 60.00}]""", ""))
                .andExpect(status().isCreated());
        obter("/caixas/atual", vendedor.token())
                .andExpect(jsonPath("$.total_vendas").value(100.00))
                .andExpect(jsonPath("$.total_vendas_dinheiro").value(40.00))
                .andExpect(jsonPath("$.saldo_em_dinheiro_atual").value(140.00));
        postar("/caixas/" + caixa + "/fechamento", vendedor.token(), "{\"valor_final_dinheiro\": 140.00}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo_esperado_dinheiro").value(140.00))
                .andExpect(jsonPath("$.diferenca").value(0.00));
    }

    @Test
    @DisplayName("CT-PAG-008 Contrato original (modalidade_pagamento) continua valendo: um pagamento à vista pelo total")
    void contratoOriginal() throws Exception {
        var r = postar("/vendas", vendedor.token(), vendaSimples(caixa, produto, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modalidade_pagamento").value("DINHEIRO"))
                .andExpect(jsonPath("$.pagamentos", hasSize(1)))
                .andExpect(jsonPath("$.pagamentos[0].valor").value(200.00))
                .andExpect(jsonPath("$.pagamentos[0].parcelas").value(1));
        assertThat(lerId(r, "$.venda_id")).isPositive();
    }

    @Test
    @DisplayName("CT-PAG-009 Comissão sobre o valor da venda, sem efeito das parcelas: 5% de R$ 300 em 10x = R$ 15")
    void comissaoNaoDependeDasParcelas() throws Exception {
        postar("/vendas", vendedor.token(), venda(3, """
                [{"modalidade_pagamento": "CREDIARIO", "valor": 300.00, "parcelas": 10}]""", ""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comissao_vendedor").value(15.00));
    }

    @Test
    @DisplayName("CT-PAG-010 Prévia calcula subtotal, cupom e total sem gravar venda nem baixar estoque")
    void previa() throws Exception {
        criarCupom("PREVIA10", "PERCENTUAL", "10", OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(1));
        postar("/vendas/previa", vendedor.token(), """
                {"cupom_codigo": "previa10", "desconto_direto_gerente": 20.00,
                 "itens": [{"produto_id": %d, "quantidade": 3}]}""".formatted(produto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor_subtotal").value(300.00))
                .andExpect(jsonPath("$.desconto_cupom").value(30.00))
                .andExpect(jsonPath("$.desconto_gerente").value(20.00))
                .andExpect(jsonPath("$.valor_total").value(250.00));
        assertThat(contar("vendas")).isZero();
        assertThat(estoqueDe(produto)).isEqualTo(20);
    }

    @Test
    @DisplayName("CT-PAR-001 Configuração de parcelamento: os dois perfis leem; só o gerente altera (vendedor 403)")
    void configuracaoLeituraEAcesso() throws Exception {
        obter("/configuracoes/parcelamento", vendedor.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].modalidade_pagamento").value("CARTAO_CREDITO"))
                .andExpect(jsonPath("$[0].max_parcelas").value(12));
        colocar("/configuracoes/parcelamento/CREDIARIO", vendedor.token(), "{\"max_parcelas\": 2}")
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CT-PAR-002 Alteração é auditada; forma à vista (422) e limite fora de 1–12 (400) são recusados")
    void configuracaoValidacoes() throws Exception {
        colocar("/configuracoes/parcelamento/CREDIARIO", tokenGerente, "{\"max_parcelas\": 5}")
                .andExpect(status().isOk());
        assertThat(contarAuditoria("CONFIGURACAO_PARCELAMENTO")).isEqualTo(1);
        colocar("/configuracoes/parcelamento/PIX", tokenGerente, "{\"max_parcelas\": 2}")
                .andExpect(status().isUnprocessableContent());
        colocar("/configuracoes/parcelamento/CREDIARIO", tokenGerente, "{\"max_parcelas\": 0}")
                .andExpect(status().isBadRequest());
        colocar("/configuracoes/parcelamento/CREDIARIO", tokenGerente, "{\"max_parcelas\": 13}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CT-OBS-001 Observação é gravada e devolvida no detalhe; acima de 500 caracteres → 400")
    void observacao() throws Exception {
        var r = postar("/vendas", vendedor.token(), venda(1, """
                [{"modalidade_pagamento": "PIX", "valor": 100.00}]""",
                ", \"observacao\": \"  Entregar na loja 2 \""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.observacao").value("Entregar na loja 2"));
        obter("/vendas/" + lerId(r, "$.venda_id"), vendedor.token())
                .andExpect(jsonPath("$.observacao").value("Entregar na loja 2"));
        postar("/vendas", vendedor.token(), venda(1, """
                [{"modalidade_pagamento": "PIX", "valor": 100.00}]""",
                ", \"observacao\": \"" + "x".repeat(501) + "\""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CT-DB-014 Pagamentos e observação da venda não podem ser alterados nem apagados no banco")
    void pagamentosImutaveis() throws Exception {
        long id = lerId(postar("/vendas", vendedor.token(), venda(1, """
                [{"modalidade_pagamento": "PIX", "valor": 100.00}]""", ", \"observacao\": \"original\""))
                .andExpect(status().isCreated()), "$.venda_id");
        assertThatThrownBy(() -> jdbc.update("UPDATE venda_pagamentos SET valor = 1 WHERE venda_id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM venda_pagamentos WHERE venda_id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE vendas SET observacao = 'alterada' WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class);
    }
}
