package br.com.empresa.pdv.comum.erro;

import java.util.List;

import org.springframework.http.HttpStatus;

/**
 * Exceção de negócio convertida em resposta RFC 7807 pelo {@link TratadorGlobalDeErros}.
 * O {@code tipo} vira o sufixo da URI {@code https://api.pdvweb.com/erros/{tipo}}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String tipo;
    private final String titulo;
    private final List<ParametroInvalido> parametrosInvalidos;

    public ApiException(HttpStatus status, String tipo, String titulo, String detalhe) {
        this(status, tipo, titulo, detalhe, List.of());
    }

    public ApiException(HttpStatus status, String tipo, String titulo, String detalhe,
                        List<ParametroInvalido> parametrosInvalidos) {
        super(detalhe);
        this.status = status;
        this.tipo = tipo;
        this.titulo = titulo;
        this.parametrosInvalidos = List.copyOf(parametrosInvalidos);
    }

    public static ApiException naoEncontrado(String recurso, Object id) {
        return new ApiException(HttpStatus.NOT_FOUND, "nao-encontrado", "Recurso Não Encontrado",
                recurso + " " + id + " não foi encontrado no sistema.");
    }

    public static ApiException conflito(String detalhe, String campo, String motivo) {
        return new ApiException(HttpStatus.CONFLICT, "conflito", "Conflito de Dados", detalhe,
                List.of(new ParametroInvalido(campo, motivo)));
    }

    public static ApiException regraNegocio(String tipo, String titulo, String detalhe) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, tipo, titulo, detalhe);
    }

    public static ApiException acessoNegado(String detalhe) {
        return new ApiException(HttpStatus.FORBIDDEN, "acesso-negado", "Acesso Negado", detalhe);
    }

    public static ApiException validacao(String campo, String motivo) {
        return new ApiException(HttpStatus.BAD_REQUEST, "validacao", "Erro de Validação de Dados",
                "Um ou mais campos contêm valores inválidos.", List.of(new ParametroInvalido(campo, motivo)));
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTipo() {
        return tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public List<ParametroInvalido> getParametrosInvalidos() {
        return parametrosInvalidos;
    }
}
