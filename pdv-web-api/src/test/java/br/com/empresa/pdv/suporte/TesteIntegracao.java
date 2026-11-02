package br.com.empresa.pdv.suporte;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Base dos testes de integração: aplicação completa (HTTP → segurança → serviço → JPA → PostgreSQL).
 * Cada teste começa com o banco limpo, contendo apenas o Consumidor Padrão e o gerente de teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("teste")
@Tag("integracao")
public abstract class TesteIntegracao {

    protected static final String CPF_GERENTE = "11144477735";
    protected static final String SENHA_GERENTE = "Gerente@Teste1";
    protected static final String SENHA_PADRAO = "Senha@Forte1";
    private static final String HASH_GERENTE = new BCryptPasswordEncoder().encode(SENHA_GERENTE);
    private static final String HASH_SENHA_PADRAO = new BCryptPasswordEncoder().encode(SENHA_PADRAO);

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    protected String tokenGerente;
    protected long idGerente;

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", BancoDeTeste::url);
    }

    @BeforeEach
    protected void prepararBanco() throws Exception {
        jdbc.execute("TRUNCATE itens_venda, vendas, movimentacoes_caixa, caixas, cupons_desconto, logs_auditoria, "
                + "tokens_revogados, tentativas_acesso, "
                + "clientes, produtos, usuarios RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO clientes (tipo, cpf_cnpj, nome_razao_social) VALUES ('PF', '00000000000', 'CONSUMIDOR PADRÃO')");
        jdbc.update("INSERT INTO usuarios (cpf, nome, senha_hash, perfil, percentual_comissao) VALUES (?, ?, ?, 'GERENTE', 0)",
                CPF_GERENTE, "Gerente de Teste", HASH_GERENTE);
        idGerente = jdbc.queryForObject("SELECT id FROM usuarios WHERE cpf = ?", Long.class, CPF_GERENTE);
        tokenGerente = login(CPF_GERENTE, SENHA_GERENTE);
    }

    // ------------------------------------------------------------------ HTTP

    protected ResultActions postar(String url, String token, String json) throws Exception {
        return mvc.perform(comCorpo(post(url), token, json));
    }

    protected ResultActions colocar(String url, String token, String json) throws Exception {
        return mvc.perform(comCorpo(put(url), token, json));
    }

    protected ResultActions remendar(String url, String token, String json) throws Exception {
        return mvc.perform(comCorpo(patch(url), token, json));
    }

    protected ResultActions obter(String url, String token) throws Exception {
        return mvc.perform(autenticar(get(url), token));
    }

    /** GET com um parâmetro de consulta passado sem pré-codificação (o MockMvc codifica). */
    protected ResultActions obter(String url, String token, String parametro, String valor) throws Exception {
        return mvc.perform(autenticar(get(url).param(parametro, valor), token));
    }

    protected ResultActions excluir(String url, String token) throws Exception {
        return mvc.perform(autenticar(delete(url), token));
    }

    private MockHttpServletRequestBuilder comCorpo(MockHttpServletRequestBuilder req, String token, String json) {
        autenticar(req, token).contentType(MediaType.APPLICATION_JSON);
        return json == null ? req : req.content(json);
    }

    private MockHttpServletRequestBuilder autenticar(MockHttpServletRequestBuilder req, String token) {
        req.with(ajusteDaRequisicao());
        return token == null ? req : req.header("Authorization", "Bearer " + token);
    }

    /** Subclasses podem alterar todas as requisições (ex.: simular HTTPS ou outro IP de origem). */
    protected RequestPostProcessor ajusteDaRequisicao() {
        return requisicao -> requisicao;
    }

    protected static <T> T ler(ResultActions resultado, String caminho) throws Exception {
        return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), caminho);
    }

    protected static long lerId(ResultActions resultado, String caminho) throws Exception {
        return ((Number) ler(resultado, caminho)).longValue();
    }

    // ------------------------------------------------------------------ Massa de dados

    protected String login(String cpf, String senha) throws Exception {
        ResultActions r = postar("/auth/login", null, """
                {"cpf": "%s", "senha": "%s"}""".formatted(cpf, senha)).andExpect(status().isOk());
        return ler(r, "$.token");
    }

    /** Cria um vendedor direto no banco (rápido) e devolve suas credenciais e token. */
    protected Operador criarVendedor(String nome, String percentualComissao) throws Exception {
        String cpf = GeradorDocumentos.cpf();
        jdbc.update("INSERT INTO usuarios (cpf, nome, senha_hash, perfil, percentual_comissao) VALUES (?, ?, ?, 'VENDEDOR', ?)",
                cpf, nome, HASH_SENHA_PADRAO, new BigDecimal(percentualComissao));
        long id = jdbc.queryForObject("SELECT id FROM usuarios WHERE cpf = ?", Long.class, cpf);
        return new Operador(id, cpf, login(cpf, SENHA_PADRAO));
    }

    protected long criarProduto(String sku, String nome, String preco, int estoque, int minimo) throws Exception {
        ResultActions r = postar("/produtos", tokenGerente, """
                {"sku": "%s", "nome": "%s", "preco_venda": %s, "estoque_atual": %d, "estoque_minimo": %d,
                 "unidade_medida": "UN"}""".formatted(sku, nome, preco, estoque, minimo))
                .andExpect(status().isCreated());
        return lerId(r, "$.id");
    }

    protected long abrirCaixa(String token, String suprimento) throws Exception {
        ResultActions r = postar("/caixas/abertura", token, """
                {"suprimento_inicial": %s}""".formatted(suprimento)).andExpect(status().isCreated());
        return lerId(r, "$.caixa_id");
    }

    protected long criarCupom(String codigo, String tipo, String valor, OffsetDateTime inicio, OffsetDateTime fim)
            throws Exception {
        ResultActions r = postar("/cupons", tokenGerente, """
                {"codigo": "%s", "tipo_desconto": "%s", "valor_desconto": %s,
                 "data_inicio_validade": "%s", "data_termino_validade": "%s"}"""
                .formatted(codigo, tipo, valor, inicio, fim)).andExpect(status().isCreated());
        return lerId(r, "$.id");
    }

    protected long criarCliente(String token) throws Exception {
        ResultActions r = postar("/clientes", token, """
                {"tipo": "PF", "cpf_cnpj": "%s", "nome_razao_social": "Cliente de Teste"}"""
                .formatted(GeradorDocumentos.cpf())).andExpect(status().isCreated());
        return lerId(r, "$.id");
    }

    protected int estoqueDe(long produtoId) {
        return jdbc.queryForObject("SELECT estoque_atual FROM produtos WHERE id = ?", Integer.class, produtoId);
    }

    protected int contar(String tabela) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabela, Integer.class);
    }

    protected int contarAuditoria(String tipoAcao) {
        return jdbc.queryForObject("SELECT count(*) FROM logs_auditoria WHERE tipo_acao = ?", Integer.class, tipoAcao);
    }

    protected static String vendaSimples(long caixaId, long produtoId, int quantidade) {
        return """
                {"caixa_id": %d, "modalidade_pagamento": "DINHEIRO",
                 "itens": [{"produto_id": %d, "quantidade": %d}]}""".formatted(caixaId, produtoId, quantidade);
    }

    /** Credenciais de um operador criado para o teste. */
    public record Operador(long id, String cpf, String token) {
    }
}
