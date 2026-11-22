package br.com.empresa.pdv.comum.erro;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Monta respostas no formato do contrato v2.3 (type, title, status, detail, invalid_params). */
public final class Problemas {

    public static final String BASE_TIPO = "https://api.pdvweb.com/erros/";

    private Problemas() {
    }

    public static ProblemDetail criar(HttpStatus status, String tipo, String titulo, String detalhe,
                                      List<ParametroInvalido> parametros) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setType(URI.create(BASE_TIPO + tipo));
        problema.setTitle(titulo);
        problema.setProperty("invalid_params", parametros);
        return problema;
    }

    public static ProblemDetail naoAutenticado() {
        return criar(HttpStatus.UNAUTHORIZED, "nao-autenticado", "Não Autenticado",
                "Token JWT ausente, inválido ou expirado.", List.of());
    }

    public static ProblemDetail acessoNegado() {
        return criar(HttpStatus.FORBIDDEN, "acesso-negado", "Acesso Negado",
                "Seu perfil não tem permissão para executar esta operação.", List.of());
    }
}
