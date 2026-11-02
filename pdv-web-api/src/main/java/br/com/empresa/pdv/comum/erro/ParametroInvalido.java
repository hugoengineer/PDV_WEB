package br.com.empresa.pdv.comum.erro;

/** Item de {@code invalid_params} do contrato de erros (RFC 7807). */
public record ParametroInvalido(String name, String reason) {
}
