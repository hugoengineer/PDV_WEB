package br.com.empresa.pdv.comum;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Front-end básico do PDV Web: páginas estáticas em {@code src/main/resources/static/app}.
 * A raiz do site e {@code /app} levam para a tela de login.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracaoFrontEnd implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/app/index.html");
        registry.addRedirectViewController("/app", "/app/index.html");
        registry.addRedirectViewController("/app/", "/app/index.html");
    }
}
