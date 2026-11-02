package br.com.empresa.pdv.seguranca;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limites contra força bruta. Ex.: 5 falhas do mesmo CPF dentro de 15 minutos bloqueiam novas
 * tentativas desse CPF por 15 minutos.
 */
@ConfigurationProperties(prefix = "pdv.seguranca.tentativas")
public record PropriedadesTentativas(Integer maxFalhasCpf, Integer maxFalhasIp, Integer maxFalhasSenhaGerente,
                                     Duration janela, Duration bloqueio) {

    public PropriedadesTentativas {
        maxFalhasCpf = maxFalhasCpf == null ? 5 : maxFalhasCpf;
        maxFalhasIp = maxFalhasIp == null ? 20 : maxFalhasIp;
        maxFalhasSenhaGerente = maxFalhasSenhaGerente == null ? 5 : maxFalhasSenhaGerente;
        janela = janela == null ? Duration.ofMinutes(15) : janela;
        bloqueio = bloqueio == null ? Duration.ofMinutes(15) : bloqueio;
    }
}
