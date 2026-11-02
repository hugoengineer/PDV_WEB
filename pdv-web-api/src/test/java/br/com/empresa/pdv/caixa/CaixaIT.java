package br.com.empresa.pdv.caixa;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.empresa.pdv.suporte.TesteIntegracao;

/** UC02 Abertura, UC03 Fechamento, UC15 Sangria/Suprimento e VAL-05 (controle rigoroso de caixa). */
@DisplayName("Controle de caixa (integração)")
class CaixaIT extends TesteIntegracao {

    @Test
    @DisplayName("CT-CX-001 Abertura de caixa com suprimento inicial retorna 201 e status ABERTO")
    void abertura() throws Exception {
        Operador vendedor = criarVendedor("Jean", "5");
        postar("/caixas/abertura", vendedor.token(), "{\"suprimento_inicial\": 100.00}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.suprimento_inicial").value(100.00))
                .andExpect(jsonPath("$.data_abertura").isNotEmpty());
    }

    @Test
    @DisplayName("CT-CX-002 Segunda abertura com caixa já aberto retorna 409")
    void aberturaDuplicada() throws Exception {
        Operador vendedor = criarVendedor("Jean", "5");
        abrirCaixa(vendedor.token(), "100.00");
        postar("/caixas/abertura", vendedor.token(), "{\"suprimento_inicial\": 50.00}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.pdvweb.com/erros/caixa-ja-aberto"));
    }

    @Test
    @DisplayName("CT-CX-003 Suprimento inicial negativo é rejeitado; zero é aceito (valor-limite)")
    void suprimentoLimite() throws Exception {
        Operador vendedor = criarVendedor("Jean", "5");
        postar("/caixas/abertura", vendedor.token(), "{\"suprimento_inicial\": -0.01}").andExpect(status().isBadRequest());
        postar("/caixas/abertura", vendedor.token(), "{\"suprimento_inicial\": 0}").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("CT-CX-004 Saldo parcial do turno (exemplo do contrato): 100 + 350 em dinheiro - 150 de sangria = 300")
    void saldoParcialExemploContrato() throws Exception {
        Operador vendedor = criarVendedor("Jean", "5");
        long caixa = abrirCaixa(vendedor.token(), "100.00");
        long produto = criarProduto("P-350", "Item de 350", "350.00", 10, 0);
        postar("/vendas", vendedor.token(), vendaSimples(caixa, produto, 1)).andExpect(status().isCreated());

        postar("/caixas/" + caixa + "/sangria", tokenGerente, """
                {"valor": 150.00, "observacao": "Retirada de segurança para o cofre."}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sangria_id").isNumber())
                .andExpect(jsonPath("$.usuario_gerente_id").value(idGerente))
                .andExpect(jsonPath("$.saldo_em_dinheiro_atual").value(300.00));

        obter("/caixas/atual", vendedor.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caixa_id").value(caixa))
                .andExpect(jsonPath("$.suprimento_inicial").value(100.00))
                .andExpect(jsonPath("$.total_vendas_dinheiro").value(350.00))
                .andExpect(jsonPath("$.total_sangrias").value(150.00))
                .andExpect(jsonPath("$.saldo_em_dinheiro_atual").value(300.00));
    }

    @Test
    @DisplayName("CT-CX-005 Vendas em PIX/cartão não entram no saldo em dinheiro do caixa")
    void saldoSoEmDinheiro() throws Exception {
        Operador vendedor = criarVendedor("Jean", "5");
        long caixa = abrirCaixa(vendedor.token(), "50.00");
        long produto = criarProduto("P-PIX", "Item", "80.00", 10, 0);
        postar("/vendas", vendedor.token(), vendaSimples(caixa, produto, 1).replace("DINHEIRO", "PIX"))
                .andExpect(status().isCreated());

        obter("/caixas/atual", vendedor.token())
                .andExpect(jsonPath("$.total_vendas").value(80.00))
                .andExpect(jsonPath("$.total_vendas_dinheiro").value(0.00))
                .andExpect(jsonPath("$.saldo_em_dinheiro_atual").value(50.00));
    }

    @Test
    @DisplayName("CT-CX-006 Sangria acima do saldo em dinheiro é rejeitada (422) e não altera o caixa")
    void sangriaAcimaDoSaldo() throws Exception {
        Operador vendedor = criarVendedor("Jean", "5");
        long caixa = abrirCaixa(vendedor.token(), "100.00");
        postar("/caixas/" + caixa + "/sangria", tokenGerente, """
                {"valor": 100.01, "observacao": "Tentativa acima do saldo"}""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("https://api.pdvweb.com/erros/saldo-caixa-insuficiente"));
        org.assertj.core.api.Assertions.assertThat(contar("movimentacoes_caixa")).isZero();
    }

    @Test
    @DisplayName("CT-CX-007 Sangria sem justificativa é rejeitada (400)")
    void sangriaSemJustificativa() throws Exception {
        Operador vendedor = criarVendedor("Jean", "5");
        long caixa = abrirCaixa(vendedor.token(), "100.00");
        postar("/caixas/" + caixa + "/sangria", tokenGerente, "{\"valor\": 10.00, \"observacao\": \" \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalid_params[0].name").value("observacao"));
    }

    @Test
    @DisplayName("CT-CX-008 Suprimento pelo Gerente aumenta o saldo em dinheiro (UC15)")
    void suprimento() throws Exception {
        Operador vendedor = criarVendedor("Jean", "5");
        long caixa = abrirCaixa(vendedor.token(), "100.00");
        postar("/caixas/" + caixa + "/suprimento", tokenGerente, """
                {"valor": 50.00, "observacao": "Reforço de troco"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo_em_dinheiro_atual").value(150.00));
    }

    @Test
    @DisplayName("CT-CX-009 Fechamento compara o valor contado com o esperado e registra a diferença (UC03)")
    void fechamento() throws Exception {
        Operador vendedor = criarVendedor("Jean", "5");
        long caixa = abrirCaixa(vendedor.token(), "100.00");
        long produto = criarProduto("P-FCH", "Item", "350.00", 10, 0);
        postar("/vendas", vendedor.token(), vendaSimples(caixa, produto, 1)).andExpect(status().isCreated());
        postar("/vendas", vendedor.token(), vendaSimples(caixa, produto, 1).replace("DINHEIRO", "CARTAO_DEBITO"))
                .andExpect(status().isCreated());
        postar("/caixas/" + caixa + "/sangria", tokenGerente, "{\"valor\": 150.00, \"observacao\": \"Cofre\"}")
                .andExpect(status().isOk());

        postar("/caixas/" + caixa + "/fechamento", vendedor.token(), "{\"valor_final_dinheiro\": 295.00}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADO"))
                .andExpect(jsonPath("$.total_vendas").value(700.00))
                .andExpect(jsonPath("$.total_sangrias").value(150.00))
                .andExpect(jsonPath("$.saldo_esperado_dinheiro").value(300.00))
                .andExpect(jsonPath("$.diferenca").value(-5.00))
                .andExpect(jsonPath("$.data_fechamento").isNotEmpty());
    }

    @Test
    @DisplayName("CT-CX-010 Caixa FECHADO não aceita novo fechamento, sangria nem venda")
    void caixaFechado() throws Exception {
        Operador vendedor = criarVendedor("Jean", "5");
        long caixa = abrirCaixa(vendedor.token(), "10.00");
        long produto = criarProduto("P-CF", "Item", "1.00", 10, 0);
        postar("/caixas/" + caixa + "/fechamento", vendedor.token(), "{\"valor_final_dinheiro\": 10.00}")
                .andExpect(status().isOk());

        postar("/caixas/" + caixa + "/fechamento", vendedor.token(), "{\"valor_final_dinheiro\": 10.00}")
                .andExpect(status().isUnprocessableContent());
        postar("/caixas/" + caixa + "/sangria", tokenGerente, "{\"valor\": 1.00, \"observacao\": \"x\"}")
                .andExpect(status().isUnprocessableContent());
        postar("/vendas", vendedor.token(), vendaSimples(caixa, produto, 1))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("https://api.pdvweb.com/erros/caixa-fechado"));
        obter("/caixas/atual", vendedor.token()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CT-CX-011 Vendedor não fecha o caixa de outro vendedor (403); Gerente pode fechar")
    void fecharCaixaDeOutro() throws Exception {
        Operador a = criarVendedor("A", "5");
        Operador b = criarVendedor("B", "5");
        long caixaDeA = abrirCaixa(a.token(), "10.00");
        postar("/caixas/" + caixaDeA + "/fechamento", b.token(), "{\"valor_final_dinheiro\": 10.00}")
                .andExpect(status().isForbidden());
        postar("/caixas/" + caixaDeA + "/fechamento", tokenGerente, "{\"valor_final_dinheiro\": 10.00}")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CT-CX-012 Após fechar, o operador pode abrir um novo caixa (novo turno)")
    void novoTurno() throws Exception {
        Operador vendedor = criarVendedor("Jean", "5");
        long primeiro = abrirCaixa(vendedor.token(), "10.00");
        postar("/caixas/" + primeiro + "/fechamento", vendedor.token(), "{\"valor_final_dinheiro\": 10.00}")
                .andExpect(status().isOk());
        long segundo = abrirCaixa(vendedor.token(), "20.00");
        org.assertj.core.api.Assertions.assertThat(segundo).isNotEqualTo(primeiro);
    }
}
