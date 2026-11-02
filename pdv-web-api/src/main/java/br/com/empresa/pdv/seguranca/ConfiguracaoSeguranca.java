package br.com.empresa.pdv.seguranca;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

import br.com.empresa.pdv.usuario.Perfil;
import br.com.empresa.pdv.usuario.UsuarioRepository;

/**
 * Autenticação stateless por JWT (HS256, 24 h) e autorização por perfil (RBAC).
 * Regras de perfil ficam nos controllers via {@code @PreAuthorize}.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({PropriedadesJwt.class, PropriedadesTentativas.class})
public class ConfiguracaoSeguranca {

    static final String CLAIM_PERFIL = "perfil";
    private static final String GERENTE = "GERENTE";

    @Bean
    SecurityFilterChain filtroDeSeguranca(HttpSecurity http,
                                          @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver,
                                          @Value("${pdv.seguranca.exigir-https:false}") boolean exigirHttps)
            throws Exception {
        // 401/403 da camada de filtros passam pelo mesmo tratador RFC 7807 dos controllers
        var tratadorErros = new TratadorErrosSeguranca(resolver);
        if (exigirHttps) {
            // Requisições HTTP são redirecionadas para HTTPS. Atrás de um balanceador que termina o TLS,
            // o esquema original chega por X-Forwarded-Proto (server.forward-headers-strategy=native).
            http.redirectToHttps(Customizer.withDefaults());
        }
        return http
                // Cabeçalhos padrão do Spring Security (nosniff, X-Frame-Options DENY, no-cache, HSTS em HTTPS)
                .headers(h -> h.httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true)
                        .maxAgeInSeconds(31_536_000)))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        // Só respondem quando a documentação está ligada (PDV_DOCUMENTACAO_API); senão, 404
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Operações exclusivas do Gerente (VAL-02) barradas já no filtro, antes da leitura
                        // e validação do corpo: o Vendedor recebe 403, nunca um 400 que revele as regras.
                        // Os @PreAuthorize dos controllers continuam como segunda camada (DEF-WEB-001).
                        .requestMatchers("/usuarios/**", "/auditoria/**").hasRole(GERENTE)
                        .requestMatchers(HttpMethod.POST, "/produtos/**", "/cupons/**").hasRole(GERENTE)
                        .requestMatchers(HttpMethod.PUT, "/produtos/**", "/cupons/**").hasRole(GERENTE)
                        .requestMatchers(HttpMethod.PATCH, "/produtos/**", "/cupons/**").hasRole(GERENTE)
                        .requestMatchers(HttpMethod.DELETE, "/produtos/**", "/cupons/**", "/clientes/**").hasRole(GERENTE)
                        .requestMatchers(HttpMethod.POST, "/vendas/*/cancelar", "/caixas/*/sangria",
                                "/caixas/*/suprimento").hasRole(GERENTE)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(conversorDePerfis()))
                        .authenticationEntryPoint(tratadorErros)
                        .accessDeniedHandler(tratadorErros))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(tratadorErros)
                        .accessDeniedHandler(tratadorErros))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecretKey chaveJwt(PropriedadesJwt propriedades) {
        return new SecretKeySpec(propriedades.segredo().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey chaveJwt) {
        return NimbusJwtEncoder.withSecretKey(chaveJwt).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey chaveJwt, RevogacaoDeTokens revogacao, UsuarioRepository usuarios, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chaveJwt).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> naoRevogado = jwt -> revogacao.estaRevogado(jwt.getId())
                ? OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token revogado (logout).", null))
                : OAuth2TokenValidatorResult.success();
        // O token só vale enquanto o usuário estiver ativo e com o mesmo perfil da emissão: inativação
        // ou mudança de perfil (ex.: gerente rebaixado) têm efeito imediato, sem esperar as 24 h (DEF-WEB-002)
        OAuth2TokenValidator<Jwt> usuarioVigente = jwt -> usuarioVigente(usuarios, jwt)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Usuário inativo ou com perfil alterado.", null));
        // Expiração conferida com o relógio da aplicação (o mesmo usado para emitir os tokens)
        JwtTimestampValidator validadeTemporal = new JwtTimestampValidator(Duration.ofSeconds(30));
        validadeTemporal.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                List.of(validadeTemporal, naoRevogado, usuarioVigente)));
        return decoder;
    }

    private static boolean usuarioVigente(UsuarioRepository usuarios, Jwt jwt) {
        try {
            Perfil perfil = Perfil.valueOf(jwt.getClaimAsString(CLAIM_PERFIL));
            return usuarios.existsByIdAndAtivoTrueAndPerfil(Long.valueOf(jwt.getSubject()), perfil);
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    private static JwtAuthenticationConverter conversorDePerfis() {
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(jwt ->
                List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString(CLAIM_PERFIL))));
        return conversor;
    }
}
