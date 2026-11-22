package br.com.empresa.pdv.usuario;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contratos da seção 3.7 da API (Gestão de Usuários e Funcionários). */
public final class UsuarioDtos {

    private UsuarioDtos() {
    }

    public record UsuarioCriacao(
            @NotBlank(message = "CPF é obrigatório.") String cpf,
            @NotBlank(message = "Nome é obrigatório.") @Size(max = 100, message = "Máximo de 100 caracteres.") String nome,
            @NotNull(message = "Perfil é obrigatório.") Perfil perfil,
            @NotNull(message = "Percentual de comissão é obrigatório.")
            @DecimalMin(value = "0.00", message = "Deve estar entre 0 e 100.")
            @DecimalMax(value = "100.00", message = "Deve estar entre 0 e 100.")
            @Digits(integer = 3, fraction = 2, message = "Máximo de 2 casas decimais.") BigDecimal percentualComissao,
            @NotBlank(message = "Senha é obrigatória.")
            @Size(min = 8, max = 72, message = "Deve ter entre 8 e 72 caracteres.") String senha) {
    }

    /** PUT: substitui o recurso por completo (a senha só muda por /auth/alterar-senha). */
    public record UsuarioAtualizacao(
            @NotBlank(message = "Nome é obrigatório.") @Size(max = 100, message = "Máximo de 100 caracteres.") String nome,
            @NotNull(message = "Perfil é obrigatório.") Perfil perfil,
            @NotNull(message = "Percentual de comissão é obrigatório.")
            @DecimalMin(value = "0.00", message = "Deve estar entre 0 e 100.")
            @DecimalMax(value = "100.00", message = "Deve estar entre 0 e 100.")
            @Digits(integer = 3, fraction = 2, message = "Máximo de 2 casas decimais.") BigDecimal percentualComissao) {
    }

    /** PATCH: somente os campos enviados são alterados. */
    public record UsuarioPatch(
            @Size(min = 1, max = 100, message = "Deve ter entre 1 e 100 caracteres.") String nome,
            Perfil perfil,
            @DecimalMin(value = "0.00", message = "Deve estar entre 0 e 100.")
            @DecimalMax(value = "100.00", message = "Deve estar entre 0 e 100.")
            @Digits(integer = 3, fraction = 2, message = "Máximo de 2 casas decimais.") BigDecimal percentualComissao) {
    }

    public record UsuarioResposta(Long id, String cpf, String nome, Perfil perfil, BigDecimal percentualComissao,
                                  boolean ativo, OffsetDateTime atualizadoEm) {
    }

    public record InativacaoResposta(boolean sucesso, String mensagem, Long id, boolean ativo) {
    }
}
