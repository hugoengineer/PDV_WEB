package br.com.empresa.pdv.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import br.com.empresa.pdv.suporte.TesteIntegracao;

/** UC01 — Autenticar no Sistema. Requisitos: Regra 2.3, VAL-06 (bcrypt), política JWT 24 h. */
@DisplayName("Autenticação e sessão (integração)")
class AutenticacaoIT extends TesteIntegracao {

    @Test
    @DisplayName("CT-AUTH-001 Login com CPF e senha válidos retorna token JWT, expiração de 24 h e perfil")
    void loginValido() throws Exception {
        ResultActions r = postar("/auth/login", null, """
                {"cpf": "111.444.777-35", "senha": "%s"}""".formatted(SENHA_GERENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.usuario.perfil").value("GERENTE"))
                .andExpect(jsonPath("$.usuario.nome").value("Gerente de Teste"));

        OffsetDateTime expira = OffsetDateTime.parse(ler(r, "$.expira_em"));
        assertThat(ChronoUnit.MINUTES.between(OffsetDateTime.now(), expira)).isBetween(23 * 60L + 58, 24 * 60L);
        assertThat(expira.getOffset().getTotalSeconds()).as("fuso da loja (-03:00)").isEqualTo(-3 * 3600);
        assertThat(contarAuditoria("LOGIN_SUCESSO")).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("CT-AUTH-002 Senha incorreta retorna 401 e registra LOGIN_FALHA na auditoria")
    void senhaIncorreta() throws Exception {
        postar("/auth/login", null, """
                {"cpf": "%s", "senha": "senha-errada"}""".formatted(CPF_GERENTE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("CPF ou senha inválidos."));
        assertThat(contarAuditoria("LOGIN_FALHA")).isEqualTo(1);
    }

    @Test
    @DisplayName("CT-AUTH-003 CPF inexistente retorna a mesma resposta 401 (não revela se o usuário existe)")
    void cpfInexistente() throws Exception {
        postar("/auth/login", null, """
                {"cpf": "52998224725", "senha": "qualquer"}""")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("CPF ou senha inválidos."));
    }

    @Test
    @DisplayName("CT-AUTH-004 Login sem CPF ou sem senha retorna 400")
    void camposObrigatorios() throws Exception {
        postar("/auth/login", null, "{\"cpf\": \"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalid_params.length()").value(2));
    }

    @Test
    @DisplayName("CT-AUTH-005 Logout revoga o token: a próxima requisição com ele retorna 401")
    void logout() throws Exception {
        String token = login(CPF_GERENTE, SENHA_GERENTE);
        obter("/produtos", token).andExpect(status().isOk());

        postar("/auth/logout", token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").value("Sessão encerrada com sucesso."));
        obter("/produtos", token).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CT-AUTH-006 Refresh-token emite um novo token e invalida o anterior")
    void renovarToken() throws Exception {
        String antigo = login(CPF_GERENTE, SENHA_GERENTE);
        String novo = ler(postar("/auth/refresh-token", antigo, null).andExpect(status().isOk()), "$.token");

        assertThat(novo).isNotEqualTo(antigo);
        obter("/produtos", novo).andExpect(status().isOk());
        obter("/produtos", antigo).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CT-AUTH-007 Alterar senha: senha atual errada é rejeitada; correta permite login com a nova")
    void alterarSenha() throws Exception {
        Operador vendedor = criarVendedor("Jean", "4.5");
        remendar("/auth/alterar-senha", vendedor.token(), """
                {"senha_atual": "errada", "nova_senha": "NovaSenha@2026"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalid_params[0].name").value("senha_atual"));

        remendar("/auth/alterar-senha", vendedor.token(), """
                {"senha_atual": "%s", "nova_senha": "NovaSenha@2026"}""".formatted(SENHA_PADRAO))
                .andExpect(status().isOk());
        postar("/auth/login", null, """
                {"cpf": "%s", "senha": "%s"}""".formatted(vendedor.cpf(), SENHA_PADRAO))
                .andExpect(status().isUnauthorized());
        login(vendedor.cpf(), "NovaSenha@2026");
    }

    @Test
    @DisplayName("CT-AUTH-008 Nova senha com 7 caracteres é rejeitada; 8 caracteres é aceita (valor-limite)")
    void tamanhoMinimoSenha() throws Exception {
        Operador vendedor = criarVendedor("Cesar", "3");
        remendar("/auth/alterar-senha", vendedor.token(), """
                {"senha_atual": "%s", "nova_senha": "1234567"}""".formatted(SENHA_PADRAO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalid_params[0].name").value("nova_senha"));
        remendar("/auth/alterar-senha", vendedor.token(), """
                {"senha_atual": "%s", "nova_senha": "12345678"}""".formatted(SENHA_PADRAO))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CT-AUTH-009 Senha é armazenada com hash BCrypt, nunca em texto puro (VAL-06)")
    void senhaComHash() throws Exception {
        Operador vendedor = criarVendedor("Ana", "2");
        String hash = jdbc.queryForObject("SELECT senha_hash FROM usuarios WHERE id = ?", String.class, vendedor.id());
        assertThat(hash).startsWith("$2a$").doesNotContain(SENHA_PADRAO);
    }
}
