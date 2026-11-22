package br.com.empresa.pdv.seguranca;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pdv.jwt")
public record PropriedadesJwt(String segredo, Duration validade) {

    static final int TAMANHO_MINIMO_SEGREDO = 32;

    public PropriedadesJwt {
        // Falha na inicialização em vez de subir a API com uma chave fraca ou vazia
        if (segredo == null || segredo.getBytes(StandardCharsets.UTF_8).length < TAMANHO_MINIMO_SEGREDO) {
            throw new IllegalStateException(
                    "pdv.jwt.segredo (variável PDV_JWT_SEGREDO) deve ter no mínimo " + TAMANHO_MINIMO_SEGREDO + " bytes.");
        }
        if (validade == null) {
            validade = Duration.ofHours(24);
        }
    }
}
