package br.com.empresa.pdv.produto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import br.com.empresa.pdv.suporte.TesteIntegracao;

/** UC09 — Consultar Estoque e UC12 — Gerenciar Catálogo. Persistência real em PostgreSQL. */
@DisplayName("Produtos e estoque (integração)")
class ProdutoIT extends TesteIntegracao {

    @Test
    @DisplayName("CT-PROD-001 Cadastrar produto válido persiste e pode ser consultado (CT-DB-001)")
    void cadastroEConsulta() throws Exception {
        long id = criarProduto("PROD-ELE-001", "Teclado Mecânico RGB", "250.00", 15, 3);

        obter("/produtos/" + id, tokenGerente)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("PROD-ELE-001"))
                .andExpect(jsonPath("$.preco_venda").value(250.00))
                .andExpect(jsonPath("$.estoque_atual").value(15))
                .andExpect(jsonPath("$.ativo").value(true));
        assertThat(jdbc.queryForObject("SELECT preco_venda::text FROM produtos WHERE id = ?", String.class, id))
                .as("valor monetário gravado como NUMERIC(10,2)").isEqualTo("250.00");
        assertThat(contarAuditoria("PRODUTO_CREATE")).isEqualTo(1);
    }

    @ParameterizedTest(name = "{displayName} [{index}] preço {0} → HTTP {1}")
    @CsvSource({"-1.00, 400", "0.00, 400", "0.01, 201", "99999999.99, 201", "100000000.00, 400", "10.999, 400"})
    @DisplayName("CT-PROD-002 Valores-limite do preço de venda (> 0, até 8 inteiros e 2 decimais)")
    void limitesPreco(String preco, int esperado) throws Exception {
        postar("/produtos", tokenGerente, """
                {"sku": "LIM-%s", "nome": "Produto limite", "preco_venda": %s, "estoque_atual": 1,
                 "estoque_minimo": 0, "unidade_medida": "UN"}""".formatted(preco.replace(".", "_"), preco))
                .andExpect(status().is(esperado));
    }

    @ParameterizedTest(name = "{displayName} [{index}] estoque {0} → HTTP {1}")
    @CsvSource({"-1, 400", "0, 201", "1000000, 201", "1000001, 400"})
    @DisplayName("CT-PROD-003 Valores-limite do estoque (0 a 1.000.000, nunca negativo)")
    void limitesEstoque(int estoque, int esperado) throws Exception {
        postar("/produtos", tokenGerente, """
                {"sku": "EST-%d", "nome": "Produto estoque", "preco_venda": 10.00, "estoque_atual": %d,
                 "estoque_minimo": 0, "unidade_medida": "UN"}""".formatted(Math.abs(estoque), estoque))
                .andExpect(status().is(esperado));
    }

    @ParameterizedTest(name = "{displayName} [{index}] nome \"{0}\"")
    @CsvSource(value = {"NULO", "''", "'   '"}, nullValues = "NULO")
    @DisplayName("CT-PROD-004 Nome ausente, vazio ou só com espaços é rejeitado e nada é persistido")
    void nomeObrigatorio(String nome) throws Exception {
        String campoNome = nome == null ? "" : "\"nome\": \"" + nome + "\",";
        postar("/produtos", tokenGerente, """
                {"sku": "SEM-NOME", %s "preco_venda": 10.00, "estoque_atual": 1, "estoque_minimo": 0,
                 "unidade_medida": "UN"}""".formatted(campoNome))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalid_params[0].name").value("nome"));
        assertThat(contar("produtos")).isZero();
    }

    @Test
    @DisplayName("CT-PROD-005 SKU duplicado retorna 409 (comparação sem diferenciar maiúsculas)")
    void skuDuplicado() throws Exception {
        criarProduto("PROD-001", "Primeiro", "10.00", 1, 0);
        postar("/produtos", tokenGerente, """
                {"sku": "prod-001", "nome": "Segundo", "preco_venda": 10.00, "estoque_atual": 1,
                 "estoque_minimo": 0, "unidade_medida": "UN"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.invalid_params[0].name").value("sku"));
    }

    @Test
    @DisplayName("CT-PROD-006 PATCH altera só os campos enviados e audita o preço anterior/novo")
    void patchPreco() throws Exception {
        long id = criarProduto("PROD-ELE-001", "Teclado", "250.00", 15, 3);
        remendar("/produtos/" + id, tokenGerente, """
                {"preco_venda": 270.00, "estoque_minimo": 3}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preco_venda").value(270.00))
                .andExpect(jsonPath("$.nome").value("Teclado"))
                .andExpect(jsonPath("$.atualizado_em").isNotEmpty());

        String detalhes = jdbc.queryForObject(
                "SELECT detalhes FROM logs_auditoria WHERE tipo_acao = 'PRODUTO_UPDATE'", String.class);
        assertThat(detalhes).isEqualTo("Preço alterado de R$ 250.00 para R$ 270.00 no produto ID " + id + ".");
    }

    @Test
    @DisplayName("CT-PROD-007 PUT substitui o recurso: campos opcionais omitidos são apagados")
    void putSubstitui() throws Exception {
        long id = criarProduto("PUT-1", "Original", "10.00", 5, 1);
        remendar("/produtos/" + id, tokenGerente, "{\"fornecedor\": \"ACME\"}").andExpect(status().isOk());
        colocar("/produtos/" + id, tokenGerente, """
                {"sku": "PUT-1", "nome": "Substituído", "preco_venda": 12.00, "estoque_atual": 5,
                 "estoque_minimo": 1, "unidade_medida": "CX"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Substituído"))
                .andExpect(jsonPath("$.unidade_medida").value("CX"))
                .andExpect(jsonPath("$.fornecedor").doesNotExist());
    }

    @Test
    @DisplayName("CT-PROD-008 Ajuste manual de estoque gera registro ESTOQUE_AJUSTE com valores anterior/novo")
    void ajusteEstoqueAuditado() throws Exception {
        long id = criarProduto("AJ-1", "Ajuste", "10.00", 5, 1);
        remendar("/produtos/" + id, tokenGerente, "{\"estoque_atual\": 12}").andExpect(status().isOk());

        var log = jdbc.queryForMap("SELECT valor_anterior, valor_novo FROM logs_auditoria WHERE tipo_acao = 'ESTOQUE_AJUSTE'");
        assertThat(log).containsEntry("valor_anterior", "5").containsEntry("valor_novo", "12");
    }

    @Test
    @DisplayName("CT-PROD-009 Soft delete: produto inativado some da listagem, mas o registro é preservado")
    void softDelete() throws Exception {
        long id = criarProduto("DEL-1", "Descontinuado", "10.00", 5, 1);
        excluir("/produtos/" + id, tokenGerente)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").value("Produto inativado com sucesso (Soft Delete)."))
                .andExpect(jsonPath("$.ativo").value(false));

        obter("/produtos", tokenGerente).andExpect(jsonPath("$.total_registros").value(0));
        obter("/produtos?incluir_inativos=true", tokenGerente).andExpect(jsonPath("$.total_registros").value(1));
        assertThat(contar("produtos")).isEqualTo(1);
        remendar("/produtos/" + id, tokenGerente, "{\"nome\": \"x\"}").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CT-PROD-010 Filtro estoque_critico=true retorna só produtos no ou abaixo do mínimo (UC09/VAL-04)")
    void filtroEstoqueCritico() throws Exception {
        criarProduto("CRIT-1", "Abaixo", "1.00", 1, 2);
        criarProduto("CRIT-2", "No limite", "1.00", 2, 2);
        criarProduto("CRIT-3", "Acima", "1.00", 3, 2);

        obter("/produtos?estoque_critico=true", tokenGerente)
                .andExpect(jsonPath("$.total_registros").value(2))
                .andExpect(jsonPath("$.dados[*].alerta_estoque_critico").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(true))));
    }

    @Test
    @DisplayName("CT-PROD-011 Paginação: 25 produtos com limite 10 geram 3 páginas; a última tem 5 itens")
    void paginacao() throws Exception {
        for (int i = 1; i <= 25; i++) {
            jdbc.update("INSERT INTO produtos (sku, nome, preco_venda, estoque_atual) VALUES (?, ?, 1.00, 1)",
                    "PG-" + i, String.format("Produto %02d", i));
        }
        obter("/produtos?pagina=3&limite=10", tokenGerente)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(3))
                .andExpect(jsonPath("$.limite").value(10))
                .andExpect(jsonPath("$.total_registros").value(25))
                .andExpect(jsonPath("$.total_paginas").value(3))
                .andExpect(jsonPath("$.dados.length()").value(5))
                .andExpect(jsonPath("$.dados[0].nome").value("Produto 21"));
        obter("/produtos?limite=101", tokenGerente).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CT-PROD-012 Busca por nome é parcial e ignora maiúsculas; busca por SKU é exata")
    void buscas() throws Exception {
        criarProduto("TEC-1", "Teclado Mecânico", "1.00", 1, 0);
        criarProduto("MOU-1", "Mouse Óptico", "1.00", 1, 0);
        obter("/produtos?nome=TECLA", tokenGerente).andExpect(jsonPath("$.total_registros").value(1));
        obter("/produtos?sku=mou-1", tokenGerente).andExpect(jsonPath("$.dados[0].nome").value("Mouse Óptico"));
        // "%" é curinga do LIKE: precisa ser tratado como caractere literal, não como "qualquer coisa"
        obter("/produtos", tokenGerente, "nome", "%").andExpect(jsonPath("$.total_registros").value(0));
    }

    @Test
    @DisplayName("CT-PROD-013 Produto inexistente retorna 404 no formato do contrato")
    void inexistente() throws Exception {
        obter("/produtos/999", tokenGerente)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.pdvweb.com/erros/nao-encontrado"));
    }
}
