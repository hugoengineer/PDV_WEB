package br.com.empresa.pdv.produto;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.empresa.pdv.comum.ConfiguracaoComum;
import br.com.empresa.pdv.produto.ProdutoDtos.ProdutoResposta;
import br.com.empresa.pdv.seguranca.ConfiguracaoSeguranca;
import br.com.empresa.pdv.seguranca.RevogacaoDeTokens;
import br.com.empresa.pdv.usuario.UsuarioRepository;

/**
 * Teste da camada HTTP isolada (MockMvc + serviço simulado): contrato de status, formato de erro
 * RFC 7807 e regras de acesso. Cenários API01–API08 do tutorial, aplicados ao POST /produtos.
 */
@WebMvcTest(controllers = ProdutoController.class,
        properties = "pdv.jwt.segredo=segredo-exclusivo-dos-testes-automatizados-0123456789")
@Import({ConfiguracaoSeguranca.class, ConfiguracaoComum.class})
@DisplayName("Contrato HTTP de /produtos (WebMvcTest)")
class ProdutoControllerWebTest {

    private static final String PRODUTO_VALIDO = """
            {"sku": "PROD-ELE-001", "nome": "Teclado Mecânico RGB", "preco_venda": 250.00,
             "estoque_atual": 15, "estoque_minimo": 3, "unidade_medida": "UN",
             "codigo_barras": "7891234567890", "ncm": "8471.60.52", "cest": "21.031.00"}""";

    @Autowired private MockMvc mvc;
    @MockitoBean private ProdutoService service;
    @MockitoBean private RevogacaoDeTokens revogacao;
    @MockitoBean private UsuarioRepository usuarios;

    @Test
    @DisplayName("CT-API-001 JSON válido enviado pelo Gerente retorna 201 Created com o produto")
    void jsonValido() throws Exception {
        when(service.cadastrar(any())).thenReturn(new ProdutoResposta(104L, "PROD-ELE-001", "Teclado Mecânico RGB",
                null, "UN", "7891234567890", "84716052", "2103100", new BigDecimal("250.00"), 15, 3, false, true, null));

        mvc.perform(post("/produtos").with(gerente()).contentType(MediaType.APPLICATION_JSON).content(PRODUTO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(104))
                .andExpect(jsonPath("$.preco_venda").value(250.00))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @DisplayName("CT-API-002 Nome ausente retorna 400 com invalid_params[nome]")
    void nomeAusente() throws Exception {
        mvc.perform(post("/produtos").with(gerente()).contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUTO_VALIDO.replace("\"nome\": \"Teclado Mecânico RGB\",", "")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.pdvweb.com/erros/validacao"))
                .andExpect(jsonPath("$.invalid_params[0].name").value("nome"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("CT-API-003 Preço negativo retorna 400 com a mensagem do contrato")
    void precoNegativo() throws Exception {
        mvc.perform(post("/produtos").with(gerente()).contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUTO_VALIDO.replace("250.00", "-10.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalid_params[0].name").value("preco_venda"))
                .andExpect(jsonPath("$.invalid_params[0].reason").value("O preço de venda deve ser maior que zero."));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("CT-API-004 JSON malformado retorna 400")
    void jsonMalformado() throws Exception {
        mvc.perform(post("/produtos").with(gerente()).contentType(MediaType.APPLICATION_JSON).content("{\"sku\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Erro de Validação de Dados"));
    }

    @Test
    @DisplayName("CT-API-005 Corpo da requisição ausente retorna 400")
    void corpoAusente() throws Exception {
        mvc.perform(post("/produtos").with(gerente()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CT-API-006 Requisição sem token retorna 401 (RFC 7807)")
    void naoAutenticado() throws Exception {
        mvc.perform(post("/produtos").contentType(MediaType.APPLICATION_JSON).content(PRODUTO_VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://api.pdvweb.com/erros/nao-autenticado"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("CT-API-007 Vendedor autenticado sem permissão recebe 403 (VAL-02)")
    void semPermissao() throws Exception {
        mvc.perform(post("/produtos").with(vendedor()).contentType(MediaType.APPLICATION_JSON).content(PRODUTO_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://api.pdvweb.com/erros/acesso-negado"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("CT-API-008 Banco indisponível retorna 503 sem expor detalhes internos")
    void bancoIndisponivel() throws Exception {
        when(service.cadastrar(any())).thenThrow(
                new DataAccessResourceFailureException("Connection refused: localhost:5432 (senha=xyz)"));

        mvc.perform(post("/produtos").with(gerente()).contentType(MediaType.APPLICATION_JSON).content(PRODUTO_VALIDO))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("https://api.pdvweb.com/erros/servico-indisponivel"))
                .andExpect(content().string(not(containsString("5432"))))
                .andExpect(content().string(not(containsString("senha"))));
    }

    @Test
    @DisplayName("CT-API-009 Erro inesperado retorna 500 genérico, sem stack trace")
    void erroInesperado() throws Exception {
        when(service.buscar(anyLong())).thenThrow(new NullPointerException("campo x nulo em ProdutoService"));

        mvc.perform(get("/produtos/1").with(vendedor()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Ocorreu um erro inesperado ao processar a requisição."))
                .andExpect(content().string(not(containsString("ProdutoService"))));
    }

    @Test
    @DisplayName("CT-API-010 Identificador não numérico na URL retorna 400")
    void idInvalido() throws Exception {
        mvc.perform(get("/produtos/abc").with(vendedor()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalid_params[0].name").value("id"));
    }

    @Test
    @DisplayName("CT-API-011 Método não suportado na coleção retorna 405")
    void metodoNaoSuportado() throws Exception {
        mvc.perform(delete("/produtos").with(gerente()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("CT-API-012 Vendedor pode consultar o estoque (leitura liberada a ambos os perfis)")
    void vendedorConsulta() throws Exception {
        when(service.buscar(104L)).thenReturn(new ProdutoResposta(104L, "PROD-ELE-001", "Teclado", null, "UN", null,
                null, null, new BigDecimal("250.00"), 2, 3, true, true, null));

        mvc.perform(get("/produtos/104").with(vendedor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerta_estoque_critico").value(true));
    }

    private static JwtRequestPostProcessor gerente() {
        return jwt().jwt(j -> j.subject("1").claim("perfil", "GERENTE").claim("nome", "Gerente"))
                .authorities(new SimpleGrantedAuthority("ROLE_GERENTE"));
    }

    private static JwtRequestPostProcessor vendedor() {
        return jwt().jwt(j -> j.subject("2").claim("perfil", "VENDEDOR").claim("nome", "Vendedor"))
                .authorities(new SimpleGrantedAuthority("ROLE_VENDEDOR"));
    }
}
