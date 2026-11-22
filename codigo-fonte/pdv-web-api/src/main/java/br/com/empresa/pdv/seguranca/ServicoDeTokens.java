package br.com.empresa.pdv.seguranca;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import br.com.empresa.pdv.usuario.Usuario;

@Service
public class ServicoDeTokens {

    private final JwtEncoder encoder;
    private final PropriedadesJwt propriedades;
    private final Clock clock;

    public ServicoDeTokens(JwtEncoder encoder, PropriedadesJwt propriedades, Clock clock) {
        this.encoder = encoder;
        this.propriedades = propriedades;
        this.clock = clock;
    }

    public TokenEmitido emitir(Usuario usuario) {
        Instant agora = clock.instant();
        Instant expiraEm = agora.plus(propriedades.validade());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("pdv-web-api")
                .subject(String.valueOf(usuario.getId()))
                .id(UUID.randomUUID().toString())
                .issuedAt(agora)
                .expiresAt(expiraEm)
                .claim("nome", usuario.getNome())
                .claim(ConfiguracaoSeguranca.CLAIM_PERFIL, usuario.getPerfil().name())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new TokenEmitido(token, expiraEm);
    }

    public record TokenEmitido(String token, Instant expiraEm) {
    }
}
