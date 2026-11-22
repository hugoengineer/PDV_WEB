package br.com.empresa.pdv.comum;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Descrição da API para o Swagger UI (/swagger-ui.html). O botão "Authorize" recebe o token do
 * POST /auth/login. A documentação só é publicada com PDV_DOCUMENTACAO_API=true.
 */
@Configuration
public class ConfiguracaoOpenApi {

    private static final String ESQUEMA = "token-jwt";

    @Bean
    OpenAPI documentacaoPdvWeb() {
        return new OpenAPI()
                .info(new Info().title("PDV Web API").version("2.3")
                        .description("API do Sistema de Ponto de Venda. Faça login em POST /auth/login, copie o "
                                + "campo token e use o botão Authorize."))
                .components(new Components().addSecuritySchemes(ESQUEMA, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA));
    }
}
