package br.com.empresa.pdv.cliente;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Contratos da seção 3.6 da API (Gestão de Clientes). */
public final class ClienteDtos {

    static final String REGEX_CEP = "^\\d{5}-?\\d{3}$";
    static final String REGEX_UF = "^[A-Za-z]{2}$";
    static final String REGEX_TELEFONE = "^[\\d\\s()+-]{8,20}$";

    private ClienteDtos() {
    }

    /** POST e PUT (PUT substitui o recurso por completo). */
    public record ClienteRequisicao(
            @NotNull(message = "Tipo é obrigatório (PF ou PJ).") TipoPessoa tipo,
            @NotBlank(message = "CPF/CNPJ é obrigatório.") String cpfCnpj,
            @NotBlank(message = "Nome/Razão social é obrigatório.")
            @Size(max = 150, message = "Máximo de 150 caracteres.") String nomeRazaoSocial,
            @Email(message = "E-mail em formato inválido.") @Size(max = 150, message = "Máximo de 150 caracteres.") String email,
            @Pattern(regexp = REGEX_TELEFONE, message = "Telefone em formato inválido.") String telefone,
            @Pattern(regexp = REGEX_CEP, message = "CEP deve ter 8 dígitos (00000-000).") String cep,
            @Size(max = 200, message = "Máximo de 200 caracteres.") String endereco,
            @Size(max = 100, message = "Máximo de 100 caracteres.") String bairro,
            @Size(max = 100, message = "Máximo de 100 caracteres.") String cidade,
            @Pattern(regexp = REGEX_UF, message = "UF deve ter 2 letras.") String uf) {
    }

    /** PATCH: somente os campos enviados são alterados. */
    public record ClientePatch(
            TipoPessoa tipo,
            String cpfCnpj,
            @Size(min = 1, max = 150, message = "Deve ter entre 1 e 150 caracteres.") String nomeRazaoSocial,
            @Email(message = "E-mail em formato inválido.") @Size(max = 150, message = "Máximo de 150 caracteres.") String email,
            @Pattern(regexp = REGEX_TELEFONE, message = "Telefone em formato inválido.") String telefone,
            @Pattern(regexp = REGEX_CEP, message = "CEP deve ter 8 dígitos (00000-000).") String cep,
            @Size(max = 200, message = "Máximo de 200 caracteres.") String endereco,
            @Size(max = 100, message = "Máximo de 100 caracteres.") String bairro,
            @Size(max = 100, message = "Máximo de 100 caracteres.") String cidade,
            @Pattern(regexp = REGEX_UF, message = "UF deve ter 2 letras.") String uf) {
    }

    public record ClienteResposta(Long id, TipoPessoa tipo, String cpfCnpj, String nomeRazaoSocial, String email,
                                  String telefone, String cep, String endereco, String bairro, String cidade,
                                  String uf, boolean ativo, OffsetDateTime atualizadoEm) {
    }

    public record InativacaoResposta(boolean sucesso, String mensagem, Long id, boolean ativo) {
    }
}
