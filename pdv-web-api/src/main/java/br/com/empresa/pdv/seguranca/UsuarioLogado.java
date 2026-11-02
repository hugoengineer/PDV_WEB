package br.com.empresa.pdv.seguranca;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import br.com.empresa.pdv.comum.erro.ApiException;
import br.com.empresa.pdv.usuario.Perfil;

/** Dados do operador autenticado, extraídos do JWT da requisição atual. */
public record UsuarioLogado(Long id, String nome, Perfil perfil, Jwt jwt) {

    public boolean isGerente() {
        return perfil == Perfil.GERENTE;
    }

    public static UsuarioLogado atual() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !(autenticacao.getPrincipal() instanceof Jwt jwt)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "nao-autenticado",
                    "Não Autenticado", "Token JWT ausente, inválido ou expirado.");
        }
        return new UsuarioLogado(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("nome"),
                Perfil.valueOf(jwt.getClaimAsString(ConfiguracaoSeguranca.CLAIM_PERFIL)), jwt);
    }
}
