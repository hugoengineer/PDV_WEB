package br.com.empresa.pdv.suporte;

import java.time.Instant;
import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import br.com.empresa.pdv.usuario.Perfil;

/** Simula um operador autenticado nos testes unitários de serviço (sem subir o Spring). */
public final class ContextoSeguranca {

    private ContextoSeguranca() {
    }

    public static void autenticar(long usuarioId, Perfil perfil) {
        Jwt jwt = Jwt.withTokenValue("token-de-teste")
                .header("alg", "HS256")
                .subject(String.valueOf(usuarioId))
                .claim("nome", "Operador " + usuarioId)
                .claim("perfil", perfil.name())
                .jti("jti-" + usuarioId)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + perfil.name()))));
    }

    public static void limpar() {
        SecurityContextHolder.clearContext();
    }
}
