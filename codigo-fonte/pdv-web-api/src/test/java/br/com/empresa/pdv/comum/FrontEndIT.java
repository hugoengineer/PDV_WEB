package br.com.empresa.pdv.comum;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.empresa.pdv.suporte.TesteIntegracao;

@DisplayName("Front-end estático servido pela API")
class FrontEndIT extends TesteIntegracao {

    @Test
    @DisplayName("CT-UI-U01 A raiz do site redireciona para a tela de login")
    void raizRedirecionaParaLogin() throws Exception {
        obter("/", null).andExpect(status().isFound()).andExpect(redirectedUrl("/app/index.html"));
        obter("/app", null).andExpect(status().isFound()).andExpect(redirectedUrl("/app/index.html"));
    }

    @Test
    @DisplayName("CT-UI-U02 Páginas, estilos e scripts do front-end são públicos (sem token)")
    void arquivosEstaticosPublicos() throws Exception {
        obter("/app/index.html", null).andExpect(status().isOk()).andExpect(content().string(containsString("form-login")));
        obter("/app/app.js", null).andExpect(status().isOk());
        obter("/app/app.css", null).andExpect(status().isOk());
    }

    @Test
    @DisplayName("CT-UI-U03 Liberar o front-end não libera os dados: a API continua exigindo token")
    void dadosContinuamProtegidos() throws Exception {
        obter("/produtos", null).andExpect(status().isUnauthorized());
        obter("/app/../produtos", null).andExpect(status().is4xxClientError());
    }
}
