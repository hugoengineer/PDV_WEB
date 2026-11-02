package br.com.empresa.pdv.comum;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ConfiguracaoComum {

    /** Os testes substituem este relógio por um controlável (bean {@code @Primary}). */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
