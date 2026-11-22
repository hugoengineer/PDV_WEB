package br.com.empresa.pdv.auth;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import br.com.empresa.pdv.usuario.Perfil;

/** Contratos da seção 3.1 da API (Autenticação e Gestão de Sessão). */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequisicao(
            @NotBlank(message = "CPF é obrigatório.") String cpf,
            @NotBlank(message = "Senha é obrigatória.") String senha) {
    }

    public record UsuarioResumo(Long id, String nome, Perfil perfil, BigDecimal percentualComissao) {
    }

    public record LoginResposta(boolean sucesso, String token, OffsetDateTime expiraEm, UsuarioResumo usuario) {
    }

    public record TokenResposta(boolean sucesso, String token, OffsetDateTime expiraEm) {
    }

    public record MensagemResposta(boolean sucesso, String mensagem) {
    }

    public record AlterarSenhaRequisicao(
            @NotBlank(message = "Senha atual é obrigatória.") String senhaAtual,
            @NotBlank(message = "Nova senha é obrigatória.")
            @Size(min = 8, max = 72, message = "Deve ter entre 8 e 72 caracteres.") String novaSenha) {
    }
}
