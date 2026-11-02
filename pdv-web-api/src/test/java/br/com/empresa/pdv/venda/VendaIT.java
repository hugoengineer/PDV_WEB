package br.com.empresa.pdv.venda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import br.com.empresa.pdv.suporte.TesteIntegracao;

/** UC04 Registrar Venda, UC05 Validar Estoque, UC06 Cupom e UC07 Desconto Direto. */
@DisplayName("Registro de vendas (integração)")
class VendaIT extends TesteIntegracao {

    private Operador vendedor;
    private long caixa;

    @BeforeEach
    void turnoAberto() throws Exception {
        vendedor = criarVendedor("Jean Wictor", "5.0");
        caixa = abrirCaixa(vendedor.token(), "100.00");
    }

    /** Exemplos reais de validação do documento "Regras de Sistema", seção 3. */
    @Nested
    @DisplayName("Exemplos das Regras de Sistema (seção 3)")
    class ExemplosRegrasDeSistema {

        @Test
        @DisplayName("CT-VEN-001 Prod-01 Camiseta P: estoque 10, venda de 2 → concluída e saldo 8 no banco")
        void camiseta() throws Exception {
            long camiseta = criarProduto("PROD-01", "Camiseta P", "49.90", 10, 2);
            postar("/vendas", vendedor.token(), vendaSimples(caixa, camiseta, 2))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("CONCLUIDA"))
                    .andExpect(jsonPath("$.valor_total").value(99.80))
                    .andExpect(jsonPath("$.alertas_estoque_critico").isEmpty());
            assertThat(estoqueDe(camiseta)).isEqualTo(8);
        }

        @Test
        @DisplayName("CT-VEN-002 Prod-02 Tênis 41: estoque 1, pedido de 3 → bloqueio por saldo insuficiente (VAL-01)")
        void tenis() throws Exception {
            long tenis = criarProduto("PROD-02", "Tênis 41", "299.90", 1, 0);
            postar("/vendas", vendedor.token(), vendaSimples(caixa, tenis, 3))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.type").value("https://api.pdvweb.com/erros/estoque-insuficiente"))
                    .andExpect(jsonPath("$.title").value("Estoque Insuficiente"))
                    .andExpect(jsonPath("$.invalid_params[0].name").value("produto_id: " + tenis))
                    .andExpect(jsonPath("$.invalid_params[0].reason").value("Estoque atual é 1, mas foi solicitado 3."));
            assertThat(estoqueDe(tenis)).isEqualTo(1);
            assertThat(contar("vendas")).isZero();
            assertThat(contar("itens_venda")).isZero();
        }

        @Test
        @DisplayName("CT-VEN-003 Prod-03 Meia: estoque 3 (mínimo 2), venda de 1 → concluída com alerta crítico (VAL-04)")
        void meia() throws Exception {
            long meia = criarProduto("PROD-03", "Meia", "15.00", 3, 2);
            postar("/vendas", vendedor.token(), vendaSimples(caixa, meia, 1))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.alertas_estoque_critico[0].produto_id").value(meia))
                    .andExpect(jsonPath("$.alertas_estoque_critico[0].estoque_atual").value(2))
                    .andExpect(jsonPath("$.alertas_estoque_critico[0].estoque_minimo").value(2));
            obter("/produtos/" + meia, vendedor.token()).andExpect(jsonPath("$.alerta_estoque_critico").value(true));
        }
    }

    @Test
    @DisplayName("CT-VEN-004 Exemplo do contrato v2.3: R$ 250 − cupom 10% − R$ 10 do gerente = R$ 215; comissão 5% = R$ 10,75")
    void exemploDoContrato() throws Exception {
        long teclado = criarProduto("PROD-ELE-001", "Teclado Mecânico RGB", "250.00", 15, 3);
        long cliente = criarCliente(vendedor.token());
        criarCupom("DESCONTO10", "PERCENTUAL", "10.00", OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(1));

        long vendaId = lerId(postar("/vendas", vendedor.token(), """
                {"caixa_id": %d, "cliente_id": %d, "cupom_codigo": "DESCONTO10",
                 "desconto_direto_gerente": 10.00, "senha_autorizacao_gerente": "%s",
                 "modalidade_pagamento": "PIX",
                 "itens": [{"produto_id": %d, "quantidade": 1, "preco_unitario": 250.00}]}"""
                .formatted(caixa, cliente, SENHA_GERENTE, teclado))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valor_subtotal").value(250.00))
                .andExpect(jsonPath("$.desconto_cupom").value(25.00))
                .andExpect(jsonPath("$.desconto_gerente").value(10.00))
                .andExpect(jsonPath("$.valor_total").value(215.00))
                .andExpect(jsonPath("$.modalidade_pagamento").value("PIX"))
                .andExpect(jsonPath("$.status").value("CONCLUIDA"))
                .andExpect(jsonPath("$.comissao_vendedor").value(10.75)), "$.venda_id");

        var linha = jdbc.queryForMap("SELECT gerente_autorizador_id, percentual_comissao::text AS pc FROM vendas WHERE id = ?", vendaId);
        assertThat(((Number) linha.get("gerente_autorizador_id")).longValue()).isEqualTo(idGerente);
        assertThat(linha.get("pc")).as("percentual congelado na venda").isEqualTo("5.00");
        assertThat(contarAuditoria("DESCONTO_GERENTE")).isEqualTo(1);
        assertThat(estoqueDe(teclado)).isEqualTo(14);
    }

    @Test
    @DisplayName("CT-VEN-005 Venda sem cliente é atribuída ao CONSUMIDOR PADRÃO")
    void consumidorPadrao() throws Exception {
        long produto = criarProduto("P-CP", "Caneta", "2.50", 100, 0);
        long venda = lerId(postar("/vendas", vendedor.token(), vendaSimples(caixa, produto, 4))
                .andExpect(status().isCreated()), "$.venda_id");
        obter("/vendas/" + venda, vendedor.token()).andExpect(jsonPath("$.cliente").value("CONSUMIDOR PADRÃO"));
    }

    @Test
    @DisplayName("CT-VEN-006 Vendedor com desconto direto sem senha do gerente → 403 conforme o contrato")
    void descontoSemSenha() throws Exception {
        long produto = criarProduto("P-D", "Item", "100.00", 10, 0);
        postar("/vendas", vendedor.token(), """
                {"caixa_id": %d, "desconto_direto_gerente": 10.00, "modalidade_pagamento": "PIX",
                 "itens": [{"produto_id": %d, "quantidade": 1}]}""".formatted(caixa, produto))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(VendaService.DETALHE_DESCONTO_SEM_AUTORIZACAO))
                .andExpect(jsonPath("$.invalid_params[0].name").value("senha_autorizacao_gerente"));
        assertThat(estoqueDe(produto)).isEqualTo(10);
    }

    @Test
    @DisplayName("CT-VEN-007 Senha de gerente incorreta (ou senha de outro vendedor) → 403")
    void senhaGerenteIncorreta() throws Exception {
        long produto = criarProduto("P-S", "Item", "100.00", 10, 0);
        postar("/vendas", vendedor.token(), """
                {"caixa_id": %d, "desconto_direto_gerente": 5.00, "senha_autorizacao_gerente": "%s",
                 "modalidade_pagamento": "PIX", "itens": [{"produto_id": %d, "quantidade": 1}]}"""
                .formatted(caixa, SENHA_PADRAO, produto))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CT-VEN-008 Gerente aplica desconto direto na própria venda sem informar senha")
    void gerenteDescontoDireto() throws Exception {
        long caixaGerente = abrirCaixa(tokenGerente, "0.00");
        long produto = criarProduto("P-G", "Item", "100.00", 10, 0);
        postar("/vendas", tokenGerente, """
                {"caixa_id": %d, "desconto_direto_gerente": 30.00, "modalidade_pagamento": "DINHEIRO",
                 "itens": [{"produto_id": %d, "quantidade": 1}]}""".formatted(caixaGerente, produto))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valor_total").value(70.00));
    }

    @Test
    @DisplayName("CT-VEN-009 Desconto direto maior que o valor restante → 422 desconto-invalido")
    void descontoMaiorQueTotal() throws Exception {
        long caixaGerente = abrirCaixa(tokenGerente, "0.00");
        long produto = criarProduto("P-DM", "Item", "100.00", 10, 0);
        postar("/vendas", tokenGerente, """
                {"caixa_id": %d, "desconto_direto_gerente": 100.01, "modalidade_pagamento": "DINHEIRO",
                 "itens": [{"produto_id": %d, "quantidade": 1}]}""".formatted(caixaGerente, produto))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("https://api.pdvweb.com/erros/desconto-invalido"));
        assertThat(estoqueDe(produto)).isEqualTo(10);
    }

    @Test
    @DisplayName("CT-VEN-010 Cupom expirado, futuro, inativo ou inexistente → 422 cupom-invalido")
    void cupomInvalido() throws Exception {
        long produto = criarProduto("P-CI", "Item", "100.00", 10, 0);
        OffsetDateTime agora = OffsetDateTime.now();
        criarCupom("EXPIRADO", "PERCENTUAL", "10", agora.minusDays(10), agora.minusDays(1));
        criarCupom("FUTURO", "PERCENTUAL", "10", agora.plusDays(1), agora.plusDays(10));
        for (String codigo : new String[]{"EXPIRADO", "FUTURO", "NAOEXISTE"}) {
            postar("/vendas", vendedor.token(), """
                    {"caixa_id": %d, "cupom_codigo": "%s", "modalidade_pagamento": "PIX",
                     "itens": [{"produto_id": %d, "quantidade": 1}]}""".formatted(caixa, codigo, produto))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.type").value("https://api.pdvweb.com/erros/cupom-invalido"));
        }
        assertThat(estoqueDe(produto)).isEqualTo(10);
    }

    @Test
    @DisplayName("CT-VEN-011 Cupom de valor fixo usado por vendedor não exige senha do gerente (UC06)")
    void cupomValorFixo() throws Exception {
        long produto = criarProduto("P-VF", "Item", "40.00", 10, 0);
        criarCupom("MENOS15", "VALOR_FIXO", "15.00", OffsetDateTime.now().minusHours(1), OffsetDateTime.now().plusHours(1));
        postar("/vendas", vendedor.token(), """
                {"caixa_id": %d, "cupom_codigo": "menos15", "modalidade_pagamento": "CARTAO_CREDITO",
                 "itens": [{"produto_id": %d, "quantidade": 2}]}""".formatted(caixa, produto))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.desconto_cupom").value(15.00))
                .andExpect(jsonPath("$.valor_total").value(65.00));
    }

    @Test
    @DisplayName("CT-VEN-012 Preço adulterado no payload é ignorado; prevalece o preço do catálogo (segurança)")
    void precoAdulterado() throws Exception {
        long produto = criarProduto("P-PA", "Notebook", "3500.00", 5, 0);
        postar("/vendas", vendedor.token(), """
                {"caixa_id": %d, "modalidade_pagamento": "PIX",
                 "itens": [{"produto_id": %d, "quantidade": 1, "preco_unitario": 0.01}]}""".formatted(caixa, produto))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valor_total").value(3500.00));
    }

    @Test
    @DisplayName("CT-VEN-013 Vários itens: um sem saldo bloqueia a venda inteira (atomicidade)")
    void atomicidade() throws Exception {
        long comSaldo = criarProduto("P-OK", "Com saldo", "10.00", 10, 0);
        long semSaldo = criarProduto("P-NOK", "Sem saldo", "10.00", 1, 0);
        postar("/vendas", vendedor.token(), """
                {"caixa_id": %d, "modalidade_pagamento": "PIX",
                 "itens": [{"produto_id": %d, "quantidade": 5}, {"produto_id": %d, "quantidade": 2}]}"""
                .formatted(caixa, comSaldo, semSaldo))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.invalid_params.length()").value(1));
        assertThat(estoqueDe(comSaldo)).as("o item com saldo também não foi baixado").isEqualTo(10);
        assertThat(contar("vendas")).isZero();
    }

    @Test
    @DisplayName("CT-VEN-014 Mesmo produto em duas linhas soma as quantidades (2 + 2 > estoque 3)")
    void itensRepetidos() throws Exception {
        long produto = criarProduto("P-REP", "Item", "10.00", 3, 0);
        postar("/vendas", vendedor.token(), """
                {"caixa_id": %d, "modalidade_pagamento": "PIX",
                 "itens": [{"produto_id": %d, "quantidade": 2}, {"produto_id": %d, "quantidade": 2}]}"""
                .formatted(caixa, produto, produto))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.invalid_params[0].reason").value("Estoque atual é 3, mas foi solicitado 4."));
    }

    @Test
    @DisplayName("CT-VEN-015 Venda que zera o estoque exatamente é aceita (limite de VAL-01)")
    void zerarEstoque() throws Exception {
        long produto = criarProduto("P-ZERO", "Último", "10.00", 3, 0);
        postar("/vendas", vendedor.token(), vendaSimples(caixa, produto, 3)).andExpect(status().isCreated());
        assertThat(estoqueDe(produto)).isZero();
        postar("/vendas", vendedor.token(), vendaSimples(caixa, produto, 1)).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("CT-VEN-016 Validação de entrada: sem itens, quantidade 0, modalidade inválida, sem caixa (400)")
    void validacoesDeEntrada() throws Exception {
        long produto = criarProduto("P-VAL", "Item", "10.00", 3, 0);
        postar("/vendas", vendedor.token(), """
                {"caixa_id": %d, "modalidade_pagamento": "PIX", "itens": []}""".formatted(caixa))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.invalid_params[0].name").value("itens"));
        postar("/vendas", vendedor.token(), vendaSimples(caixa, produto, 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalid_params[0].name").value("itens[0].quantidade"));
        postar("/vendas", vendedor.token(), vendaSimples(caixa, produto, 1).replace("DINHEIRO", "BITCOIN"))
                .andExpect(status().isBadRequest());
        postar("/vendas", vendedor.token(), """
                {"modalidade_pagamento": "PIX", "itens": [{"produto_id": %d, "quantidade": 1}]}""".formatted(produto))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.invalid_params[0].name").value("caixa_id"));
    }

    @Test
    @DisplayName("CT-VEN-017 Operador sem caixa aberto não vende (VAL-05) e caixa de outro operador é negado (403)")
    void semCaixaAberto() throws Exception {
        Operador outro = criarVendedor("Sem caixa", "5");
        long produto = criarProduto("P-SC", "Item", "10.00", 3, 0);
        postar("/vendas", outro.token(), vendaSimples(caixa, produto, 1)).andExpect(status().isForbidden());
        postar("/vendas", outro.token(), vendaSimples(999, produto, 1)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CT-VEN-018 Produto inativo ou inexistente não pode ser vendido (404)")
    void produtoInativo() throws Exception {
        long produto = criarProduto("P-IN", "Fora de linha", "10.00", 3, 0);
        excluir("/produtos/" + produto, tokenGerente).andExpect(status().isOk());
        postar("/vendas", vendedor.token(), vendaSimples(caixa, produto, 1)).andExpect(status().isNotFound());
        postar("/vendas", vendedor.token(), vendaSimples(caixa, 9999, 1)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CT-VEN-019 Vendas são imutáveis: PUT, PATCH e DELETE em /vendas/{id} retornam 405")
    void imutabilidade() throws Exception {
        long produto = criarProduto("P-IM", "Item", "10.00", 3, 0);
        long venda = lerId(postar("/vendas", vendedor.token(), vendaSimples(caixa, produto, 1)), "$.venda_id");
        colocar("/vendas/" + venda, tokenGerente, "{}").andExpect(status().isMethodNotAllowed());
        remendar("/vendas/" + venda, tokenGerente, "{}").andExpect(status().isMethodNotAllowed());
        excluir("/vendas/" + venda, tokenGerente).andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("CT-VEN-020 Registro de venda gera VENDA_REGISTRO na auditoria com o operador")
    void auditoria() throws Exception {
        long produto = criarProduto("P-AU", "Item", "10.00", 3, 0);
        long venda = lerId(postar("/vendas", vendedor.token(), vendaSimples(caixa, produto, 1)), "$.venda_id");
        Long usuario = jdbc.queryForObject(
                "SELECT usuario_id FROM logs_auditoria WHERE tipo_acao = 'VENDA_REGISTRO' AND entidade_id = ?",
                Long.class, venda);
        assertThat(usuario).isEqualTo(vendedor.id());
    }
}
