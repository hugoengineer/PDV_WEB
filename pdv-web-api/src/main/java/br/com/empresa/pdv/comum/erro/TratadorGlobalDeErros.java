package br.com.empresa.pdv.comum.erro;

import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import br.com.empresa.pdv.seguranca.TentativasExcedidasException;

/**
 * Converte todas as falhas da API para o padrão RFC 7807 definido no contrato v2.3.
 * Nenhuma mensagem interna (SQL, stack trace) é exposta ao cliente.
 */
@RestControllerAdvice
public class TratadorGlobalDeErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorGlobalDeErros.class);
    private static final String DETALHE_VALIDACAO = "Um ou mais campos contêm valores inválidos.";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> api(ApiException e) {
        ProblemDetail problema = Problemas.criar(e.getStatus(), e.getTipo(), e.getTitulo(), e.getMessage(),
                e.getParametrosInvalidos());
        if (e instanceof TentativasExcedidasException bloqueio) {
            return ResponseEntity.status(problema.getStatus())
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(bloqueio.getSegundosParaNovaTentativa()))
                    .body(problema);
        }
        return responder(problema);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> corpoInvalido(MethodArgumentNotValidException e) {
        List<ParametroInvalido> parametros = e.getBindingResult().getFieldErrors().stream()
                .map(erro -> new ParametroInvalido(NomesJson.paraSnakeCase(erro.getField()), mensagem(erro)))
                .sorted(Comparator.comparing(ParametroInvalido::name))
                .toList();
        return validacao(DETALHE_VALIDACAO, parametros);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> parametrosInvalidos(HandlerMethodValidationException e) {
        List<ParametroInvalido> parametros = e.getParameterValidationResults().stream()
                .flatMap(resultado -> resultado.getResolvableErrors().stream()
                        .map(erro -> new ParametroInvalido(
                                NomesJson.paraSnakeCase(resultado.getMethodParameter().getParameterName()),
                                mensagem(erro))))
                .toList();
        return validacao(DETALHE_VALIDACAO, parametros);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> corpoIlegivel(HttpMessageNotReadableException e) {
        return validacao("Corpo da requisição ausente, JSON malformado ou com valor de tipo incorreto.", List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> tipoIncorreto(MethodArgumentTypeMismatchException e) {
        return validacao(DETALHE_VALIDACAO,
                List.of(new ParametroInvalido(NomesJson.paraSnakeCase(e.getName()), "Valor em formato inválido.")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> parametroAusente(MissingServletRequestParameterException e) {
        return validacao(DETALHE_VALIDACAO,
                List.of(new ParametroInvalido(e.getParameterName(), "Parâmetro obrigatório.")));
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    ResponseEntity<ProblemDetail> acessoNegado(RuntimeException e) {
        return responder(Problemas.acessoNegado());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> naoAutenticado(AuthenticationException e) {
        return responder(Problemas.naoAutenticado());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> metodoNaoSuportado(HttpRequestMethodNotSupportedException e) {
        return responder(Problemas.criar(HttpStatus.METHOD_NOT_ALLOWED, "metodo-nao-permitido",
                "Método Não Permitido", "O método " + e.getMethod() + " não é suportado neste recurso.", List.of()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> rotaInexistente(NoResourceFoundException e) {
        return responder(Problemas.criar(HttpStatus.NOT_FOUND, "nao-encontrado", "Recurso Não Encontrado",
                "O recurso solicitado não foi encontrado no sistema.", List.of()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> integridade(DataIntegrityViolationException e) {
        // Normalmente evitado pelas validações dos serviços; aqui cobre condições de corrida
        log.warn("Violação de integridade não antecipada: {}", e.getMostSpecificCause().getMessage());
        return responder(Problemas.criar(HttpStatus.CONFLICT, "conflito", "Conflito de Dados",
                "A operação viola uma restrição de integridade dos dados.", List.of()));
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class})
    ResponseEntity<ProblemDetail> bancoIndisponivel(RuntimeException e) {
        log.error("Banco de dados indisponível", e);
        return responder(Problemas.criar(HttpStatus.SERVICE_UNAVAILABLE, "servico-indisponivel",
                "Serviço Indisponível", "O serviço está temporariamente indisponível. Tente novamente em instantes.",
                List.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> inesperado(Exception e) {
        log.error("Erro inesperado", e);
        return responder(Problemas.criar(HttpStatus.INTERNAL_SERVER_ERROR, "erro-interno", "Erro Interno",
                "Ocorreu um erro inesperado ao processar a requisição.", List.of()));
    }

    private ResponseEntity<ProblemDetail> validacao(String detalhe, List<ParametroInvalido> parametros) {
        return responder(Problemas.criar(HttpStatus.BAD_REQUEST, "validacao", "Erro de Validação de Dados",
                detalhe, parametros));
    }

    private static ResponseEntity<ProblemDetail> responder(ProblemDetail problema) {
        return ResponseEntity.status(problema.getStatus()).body(problema);
    }

    private static String mensagem(MessageSourceResolvable erro) {
        if (erro instanceof FieldError campo && campo.isBindingFailure()) {
            return "Valor em formato inválido.";
        }
        return erro.getDefaultMessage();
    }
}
