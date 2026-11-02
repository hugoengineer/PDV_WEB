package br.com.empresa.pdv.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.ResultActions;

import br.com.empresa.pdv.suporte.GeradorDocumentos;
import br.com.empresa.pdv.suporte.TesteIntegracao;

/**
 * Correções de robustez de segurança (Ciclo 1.1): expiração de token, revogação persistente,
 * limite de tentativas de login e de senha do gerente, cabeçalhos de segurança.
 */
@DisplayName("Robustez de segurança (integração)")
class RobustezSegurancaIT extends TesteIntegracao {

    @Autowired private JwtEncoder jwtEncoder;

    @Test
    @DisplayName("CT-SEG-010 Token com a assinatura correta, mas expirado, retorna 401")
    void tokenExpirado() throws Exception {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("pdv-web-api").subject(String.valueOf(idGerente)).id(UUID.randomUUID().toString())
                .issuedAt(agora.minus(25, ChronoUnit.HOURS)).expiresAt(agora.minus(1, ChronoUnit.HOURS))
                .claim("nome", "Gerente de Teste").claim("perfil", "GERENTE").build();
        String expirado = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        obter("/produtos", expirado).andExpect(status().isUnauthorized());
        obter("/produtos", tokenGerente).andExpect(status().isOk());
    }

    @Test
    @DisplayName("CT-SEG-011 Logout fica gravado no banco e vale para uma nova instância da API (reinício)")
    void revogacaoPersistente() throws Exception {
        String token = login(CPF_GERENTE, SENHA_GERENTE);
        postar("/auth/logout", token, null).andExpect(status().isOk());

        String jti = SignedJWT.parse(token).getJWTClaimsSet().getJWTID();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tokens_revogados WHERE jti = ?", Integer.class, jti))
                .isEqualTo(1);
        // Uma instância nova do componente (como após reiniciar a API) não tem estado em memória
        RevogacaoDeTokens novaInstancia = new RevogacaoDeTokens(jdbc, Clock.systemUTC());
        assertThat(novaInstancia.estaRevogado(jti)).isTrue();
        obter("/produtos", token).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CT-SEG-012 Limpeza periódica remove só registros de tokens já expirados")
    void limpezaDeTokens() {
        jdbc.update("INSERT INTO tokens_revogados (jti, expira_em) VALUES ('expirado', ?), ('vigente', ?)",
                Timestamp.from(Instant.now().minusSeconds(60)), Timestamp.from(Instant.now().plusSeconds(3600)));
        int removidos = new RevogacaoDeTokens(jdbc, Clock.systemUTC()).limparExpirados();

        assertThat(removidos).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT jti FROM tokens_revogados", String.class)).containsExactly("vigente");
    }

    @Test
    @DisplayName("CT-SEG-013 5 senhas erradas no mesmo CPF bloqueiam o login (429 + Retry-After), mesmo com a senha certa")
    void bloqueioPorCpf() throws Exception {
        for (int i = 0; i < 5; i++) {
            tentarLogin(CPF_GERENTE, "errada-" + i).andExpect(status().isUnauthorized());
        }
        tentarLogin(CPF_GERENTE, SENHA_GERENTE)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.type").value("https://api.pdvweb.com/erros/muitas-tentativas"));
        assertThat(contarAuditoria("ACESSO_BLOQUEADO")).isEqualTo(1);

        Operador outro = criarVendedor("Outro", "1");
        login(outro.cpf(), SENHA_PADRAO);
    }

    @Test
    @DisplayName("CT-SEG-014 CPF inexistente é bloqueado da mesma forma (o 429 não revela se o usuário existe)")
    void bloqueioCpfInexistente() throws Exception {
        String inexistente = GeradorDocumentos.cpf();
        for (int i = 0; i < 5; i++) {
            tentarLogin(inexistente, "qualquer").andExpect(status().isUnauthorized());
        }
        tentarLogin(inexistente, "qualquer").andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("CT-SEG-015 20 falhas do mesmo IP (CPFs diferentes) bloqueiam o IP; outro IP continua entrando")
    void bloqueioPorIp() throws Exception {
        for (int i = 0; i < 20; i++) {
            tentarLoginDoIp("203.0.113.9", GeradorDocumentos.cpf(), "x").andExpect(status().isUnauthorized());
        }
        tentarLoginDoIp("203.0.113.9", CPF_GERENTE, SENHA_GERENTE).andExpect(status().isTooManyRequests());
        tentarLoginDoIp("198.51.100.4", CPF_GERENTE, SENHA_GERENTE).andExpect(status().isOk());
    }

    @Test
    @DisplayName("CT-SEG-016 Bloqueio expira após o período; login com sucesso zera a contagem")
    void bloqueioExpiraEZera() throws Exception {
        for (int i = 0; i < 4; i++) {
            tentarLogin(CPF_GERENTE, "errada").andExpect(status().isUnauthorized());
        }
        login(CPF_GERENTE, SENHA_GERENTE);
        for (int i = 0; i < 4; i++) {
            tentarLogin(CPF_GERENTE, "errada").andExpect(status().isUnauthorized());
        }
        login(CPF_GERENTE, SENHA_GERENTE); // 4 + 4 falhas não bloqueiam: o sucesso intermediário zerou

        for (int i = 0; i < 5; i++) {
            tentarLogin(CPF_GERENTE, "errada").andExpect(status().isUnauthorized());
        }
        tentarLogin(CPF_GERENTE, SENHA_GERENTE).andExpect(status().isTooManyRequests());
        jdbc.update("UPDATE tentativas_acesso SET bloqueado_ate = ? WHERE chave = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), LimitadorDeTentativas.chaveCpf(CPF_GERENTE));
        tentarLogin(CPF_GERENTE, SENHA_GERENTE).andExpect(status().isOk());
    }

    @Test
    @DisplayName("CT-SEG-017 Vendedor errando a senha do gerente 5 vezes no desconto é bloqueado (429) e auditado")
    void bloqueioSenhaGerente() throws Exception {
        Operador vendedor = criarVendedor("Jean", "5");
        long caixa = abrirCaixa(vendedor.token(), "0");
        long produto = criarProduto("PIN-1", "Item", "100.00", 50, 0);
        for (int i = 0; i < 5; i++) {
            postar("/vendas", vendedor.token(), vendaComDesconto(caixa, produto, "chute-" + i))
                    .andExpect(status().isForbidden());
        }
        postar("/vendas", vendedor.token(), vendaComDesconto(caixa, produto, SENHA_GERENTE))
                .andExpect(status().isTooManyRequests());
        assertThat(contarAuditoria("AUTORIZACAO_FALHA")).isEqualTo(5);
        assertThat(estoqueDe(produto)).isEqualTo(50);
        // Venda sem desconto direto continua liberada para o vendedor
        postar("/vendas", vendedor.token(), vendaSimples(caixa, produto, 1)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("CT-SEG-018 Respostas trazem cabeçalhos de segurança; HSTS em HTTPS")
    void cabecalhosDeSeguranca() throws Exception {
        obter("/produtos", tokenGerente)
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        mvc.perform(get("/produtos").secure(true).header("Authorization", "Bearer " + tokenGerente))
                .andExpect(header().string("Strict-Transport-Security",
                        containsString("max-age=31536000")));
    }

    @Test
    @DisplayName("CT-SEG-019 10 tentativas erradas simultâneas no mesmo CPF não escapam do limite")
    void limiteSobConcorrencia() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Integer>> futuros = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futuros.add(pool.submit(() -> {
                largada.await();
                return tentarLogin(CPF_GERENTE, "errada").andReturn().getResponse().getStatus();
            }));
        }
        largada.countDown();
        for (Future<Integer> f : futuros) {
            assertThat(f.get()).isIn(401, 429);
        }
        pool.shutdown();
        tentarLogin(CPF_GERENTE, SENHA_GERENTE).andExpect(status().isTooManyRequests());
    }

    private ResultActions tentarLogin(String cpf, String senha) throws Exception {
        return postar("/auth/login", null, """
                {"cpf": "%s", "senha": "%s"}""".formatted(cpf, senha));
    }

    private ResultActions tentarLoginDoIp(String ip, String cpf, String senha) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .with(requisicao -> {
                    requisicao.setRemoteAddr(ip);
                    return requisicao;
                })
                .content("""
                        {"cpf": "%s", "senha": "%s"}""".formatted(cpf, senha)));
    }

    private static String vendaComDesconto(long caixa, long produto, String senha) {
        return """
                {"caixa_id": %d, "desconto_direto_gerente": 5.00, "senha_autorizacao_gerente": "%s",
                 "modalidade_pagamento": "PIX", "itens": [{"produto_id": %d, "quantidade": 1}]}"""
                .formatted(caixa, senha, produto);
    }
}
