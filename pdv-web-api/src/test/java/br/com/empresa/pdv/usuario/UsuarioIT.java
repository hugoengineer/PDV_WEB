package br.com.empresa.pdv.usuario;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import br.com.empresa.pdv.suporte.GeradorDocumentos;
import br.com.empresa.pdv.suporte.TesteIntegracao;

/** UC13 — Gerenciar Funcionários/Usuários e parametrizar comissões. */
@DisplayName("Usuários e funcionários (integração)")
class UsuarioIT extends TesteIntegracao {

    @Test
    @DisplayName("CT-USR-001 Gerente cadastra vendedor; a resposta não expõe senha nem hash")
    void cadastrar() throws Exception {
        postar("/usuarios", tokenGerente, """
                {"cpf": "%s", "nome": "Jean Wictor", "perfil": "VENDEDOR", "percentual_comissao": 4.5,
                 "senha": "senha123forte"}""".formatted(GeradorDocumentos.cpf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.perfil").value("VENDEDOR"))
                .andExpect(jsonPath("$.percentual_comissao").value(4.5))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senha_hash").doesNotExist());
    }

    @ParameterizedTest(name = "{displayName} [{index}] comissão {0} → HTTP {1}")
    @CsvSource({"-0.01, 400", "0.00, 201", "100.00, 201", "100.01, 400"})
    @DisplayName("CT-USR-002 Valores-limite do percentual de comissão (0 a 100)")
    void limitesComissao(String comissao, int esperado) throws Exception {
        postar("/usuarios", tokenGerente, """
                {"cpf": "%s", "nome": "Limite", "perfil": "VENDEDOR", "percentual_comissao": %s,
                 "senha": "senha123forte"}""".formatted(GeradorDocumentos.cpf(), comissao))
                .andExpect(status().is(esperado));
    }

    @Test
    @DisplayName("CT-USR-003 CPF inválido (400) e CPF já cadastrado (409)")
    void cpf() throws Exception {
        postar("/usuarios", tokenGerente, """
                {"cpf": "12345678900", "nome": "X", "perfil": "VENDEDOR", "percentual_comissao": 1,
                 "senha": "senha123forte"}""").andExpect(status().isBadRequest());
        postar("/usuarios", tokenGerente, """
                {"cpf": "%s", "nome": "X", "perfil": "VENDEDOR", "percentual_comissao": 1,
                 "senha": "senha123forte"}""".formatted(CPF_GERENTE)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("CT-USR-004 PATCH de comissão é auditado com valores anterior e novo")
    void patchComissao() throws Exception {
        Operador vendedor = criarVendedor("Jean Wictor", "4.5");
        remendar("/usuarios/" + vendedor.id(), tokenGerente, """
                {"nome": "Jean Wictor Silva", "percentual_comissao": 5.0}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percentual_comissao").value(5.0));
        var log = jdbc.queryForMap("SELECT valor_anterior, valor_novo FROM logs_auditoria WHERE tipo_acao = 'USUARIO_UPDATE'");
        org.assertj.core.api.Assertions.assertThat((String) log.get("valor_anterior")).contains("percentual_comissao=4.50");
        org.assertj.core.api.Assertions.assertThat((String) log.get("valor_novo")).contains("percentual_comissao=5.00");
    }

    @Test
    @DisplayName("CT-USR-005 Gerente não pode inativar a si mesmo nem remover o próprio perfil de gerente")
    void autoProtecao() throws Exception {
        excluir("/usuarios/" + idGerente, tokenGerente).andExpect(status().isUnprocessableContent());
        remendar("/usuarios/" + idGerente, tokenGerente, "{\"perfil\": \"VENDEDOR\"}")
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("CT-USR-006 Usuário inativado não aparece na listagem e não consegue logar")
    void inativar() throws Exception {
        Operador vendedor = criarVendedor("Saiu", "1");
        excluir("/usuarios/" + vendedor.id(), tokenGerente)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").value("Usuário inativado com sucesso (Soft Delete)."));
        obter("/usuarios", tokenGerente).andExpect(jsonPath("$.total_registros").value(1));
        postar("/auth/login", null, """
                {"cpf": "%s", "senha": "%s"}""".formatted(vendedor.cpf(), SENHA_PADRAO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CT-USR-007 PUT substitui nome, perfil e comissão; consulta e alteração de inexistente → 404")
    void putEInexistente() throws Exception {
        Operador vendedor = criarVendedor("Jean", "4.5");
        colocar("/usuarios/" + vendedor.id(), tokenGerente, """
                {"nome": "Jean Wictor Silva", "perfil": "GERENTE", "percentual_comissao": 2.5}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Jean Wictor Silva"))
                .andExpect(jsonPath("$.perfil").value("GERENTE"))
                .andExpect(jsonPath("$.percentual_comissao").value(2.5));
        obter("/usuarios/" + vendedor.id(), tokenGerente).andExpect(jsonPath("$.perfil").value("GERENTE"));
        obter("/usuarios/999", tokenGerente).andExpect(status().isNotFound());
        remendar("/usuarios/999", tokenGerente, "{\"nome\": \"x\"}").andExpect(status().isNotFound());
    }
}
