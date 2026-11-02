package br.com.empresa.pdv;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.empresa.pdv.suporte.TesteIntegracao;

@DisplayName("Smoke test - aplicação sobe e responde")
class FumacaIT extends TesteIntegracao {

    @Test
    @DisplayName("CT-SMK-001 Health check público responde UP")
    void healthCheck() throws Exception {
        obter("/actuator/health", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("CT-SMK-002 Rota protegida sem token responde 401 no formato RFC 7807")
    void semToken() throws Exception {
        obter("/produtos", null).andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://api.pdvweb.com/erros/nao-autenticado"))
                .andExpect(jsonPath("$.invalid_params").isArray());
    }

    @Test
    @DisplayName("CT-SMK-003 Gerente cadastra produto e a resposta usa snake_case")
    void cadastroProduto() throws Exception {
        postar("/produtos", tokenGerente, """
                {"sku": "SMK-1", "nome": "Teclado", "preco_venda": 250.00, "estoque_atual": 15,
                 "estoque_minimo": 3, "unidade_medida": "UN", "ncm": "8471.60.52"}""")
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.preco_venda").value(250.00))
                .andExpect(jsonPath("$.alerta_estoque_critico").value(false))
                .andExpect(jsonPath("$.ncm").value("84716052"));
    }
}
