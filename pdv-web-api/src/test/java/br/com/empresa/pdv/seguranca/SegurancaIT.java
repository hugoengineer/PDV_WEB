package br.com.empresa.pdv.seguranca;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import br.com.empresa.pdv.suporte.TesteIntegracao;

/**
 * Cenários de segurança (tutorial 4.5): sem token, token inválido/adulterado, perfil sem permissão,
 * acesso a dados de outro usuário trocando o id na URL (IDOR) e conteúdo malicioso.
 */
@DisplayName("Segurança e controle de acesso (integração)")
class SegurancaIT extends TesteIntegracao {

    @Test
    @DisplayName("CT-SEG-001 Token adulterado (assinatura inválida) retorna 401")
    void tokenAdulterado() throws Exception {
        String token = login(CPF_GERENTE, SENHA_GERENTE);
        String adulterado = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");
        obter("/produtos", adulterado).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CT-SEG-002 Token malformado retorna 401")
    void tokenMalformado() throws Exception {
        obter("/produtos", "isto-nao-e-um-jwt").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CT-SEG-003 Token emitido com outra chave secreta retorna 401")
    void tokenOutraChave() throws Exception {
        // Header/payload válidos (HS256, sub=1, perfil GERENTE), assinados com uma chave diferente
        String falsificado = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwicGVyZmlsIjoiR0VSRU5URSIsImV4cCI6NDEwMjQ0NDgwMH0."
                + "ZmFsc2lmaWNhZG9fY29tX291dHJhX2NoYXZlX3NlY3JldGE";
        obter("/usuarios", falsificado).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CT-SEG-004 Usuário inativado perde o acesso imediatamente, mesmo com token ainda válido")
    void usuarioInativadoPerdeAcesso() throws Exception {
        Operador vendedor = criarVendedor("Temporário", "1");
        obter("/produtos", vendedor.token()).andExpect(status().isOk());

        excluir("/usuarios/" + vendedor.id(), tokenGerente).andExpect(status().isOk());
        obter("/produtos", vendedor.token()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CT-SEG-009 Gerente rebaixado a Vendedor perde os privilégios na hora (token antigo é invalidado)")
    void perfilAlteradoInvalidaToken() throws Exception {
        Operador promovido = criarVendedor("Futuro gerente", "1");
        remendar("/usuarios/" + promovido.id(), tokenGerente, "{\"perfil\": \"GERENTE\"}").andExpect(status().isOk());
        String tokenComoGerente = login(promovido.cpf(), SENHA_PADRAO);
        obter("/usuarios", tokenComoGerente).andExpect(status().isOk());

        remendar("/usuarios/" + promovido.id(), tokenGerente, "{\"perfil\": \"VENDEDOR\"}").andExpect(status().isOk());

        obter("/usuarios", tokenComoGerente).andExpect(status().isUnauthorized());
        String tokenNovo = login(promovido.cpf(), SENHA_PADRAO);
        obter("/usuarios", tokenNovo).andExpect(status().isForbidden());
        obter("/produtos", tokenNovo).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0} {1}")
    @CsvSource({
            "POST,   /produtos",
            "POST,   /cupons",
            "POST,   /usuarios",
            "GET,    /usuarios",
            "GET,    /auditoria",
            "POST,   /vendas/1/cancelar",
            "POST,   /caixas/1/sangria",
            "DELETE, /clientes/2",
            "DELETE, /produtos/1"
    })
    @DisplayName("CT-SEG-005 Matriz RBAC: operações exclusivas do Gerente retornam 403 para o Vendedor (VAL-02)")
    void matrizRbac(String metodo, String url) throws Exception {
        Operador vendedor = criarVendedor("Vendedor RBAC", "1");
        var resultado = switch (metodo.trim()) {
            case "GET" -> obter(url.trim(), vendedor.token());
            case "DELETE" -> excluir(url.trim(), vendedor.token());
            default -> postar(url.trim(), vendedor.token(), "{}");
        };
        resultado.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://api.pdvweb.com/erros/acesso-negado"));
    }

    @Test
    @DisplayName("CT-SEG-006 IDOR: vendedor não consulta o caixa de outro vendedor trocando o id na URL")
    void idorCaixa() throws Exception {
        Operador a = criarVendedor("Vendedor A", "1");
        Operador b = criarVendedor("Vendedor B", "1");
        long caixaDeA = abrirCaixa(a.token(), "100.00");

        obter("/caixas/" + caixaDeA, b.token()).andExpect(status().isForbidden());
        obter("/caixas/" + caixaDeA, a.token()).andExpect(status().isOk());
        obter("/caixas/" + caixaDeA, tokenGerente).andExpect(status().isOk());
    }

    @Test
    @DisplayName("CT-SEG-007 Conteúdo malicioso (SQL injection / script) é tratado como texto comum")
    void conteudoMalicioso() throws Exception {
        String ataque = "'; DROP TABLE produtos; --<script>alert(1)</script>";
        postar("/produtos", tokenGerente, """
                {"sku": "SEG-7", "nome": "%s", "preco_venda": 1.00, "estoque_atual": 1, "estoque_minimo": 0,
                 "unidade_medida": "UN"}""".formatted(ataque.replace("\"", "\\\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value(ataque));
        obter("/produtos", tokenGerente, "nome", "' OR '1'='1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_registros").value(0));
        obter("/produtos", tokenGerente).andExpect(jsonPath("$.total_registros").value(1));
    }

    @Test
    @DisplayName("CT-SEG-008 Health check é público; demais endpoints do actuator não são expostos")
    void actuator() throws Exception {
        obter("/actuator/health", null).andExpect(status().isOk());
        obter("/actuator/env", tokenGerente).andExpect(status().isNotFound());
    }
}
