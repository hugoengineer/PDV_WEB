package br.com.empresa.pdv.cliente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.empresa.pdv.suporte.GeradorDocumentos;
import br.com.empresa.pdv.suporte.TesteIntegracao;

/** UC08 — Cadastrar e Gerenciar Clientes (PF/PJ). Inclui o exercício do tutorial (cap. 17) adaptado. */
@DisplayName("Clientes (integração)")
class ClienteIT extends TesteIntegracao {

    @Test
    @DisplayName("CT-CLI-001 Vendedor cadastra cliente PF válido com CPF mascarado; documento gravado só com dígitos")
    void cadastroPf() throws Exception {
        Operador vendedor = criarVendedor("Jean", "4.5");
        String cpf = GeradorDocumentos.cpf();
        postar("/clientes", vendedor.token(), """
                {"tipo": "PF", "cpf_cnpj": "%s", "nome_razao_social": "Carlos Eduardo",
                 "email": "carlos@exemplo.com", "cep": "01310-100", "uf": "sp"}"""
                .formatted(GeradorDocumentos.formatarCpf(cpf)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cpf_cnpj").value(cpf))
                .andExpect(jsonPath("$.cep").value("01310100"))
                .andExpect(jsonPath("$.uf").value("SP"))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @DisplayName("CT-CLI-002 Cliente PJ com CNPJ válido é aceito")
    void cadastroPj() throws Exception {
        postar("/clientes", tokenGerente, """
                {"tipo": "PJ", "cpf_cnpj": "%s", "nome_razao_social": "Loja Exemplo LTDA"}"""
                .formatted(GeradorDocumentos.cnpj()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("PJ"));
    }

    @Test
    @DisplayName("CT-CLI-003 CPF inválido é rejeitado com 400 e não persiste")
    void cpfInvalido() throws Exception {
        postar("/clientes", tokenGerente, """
                {"tipo": "PF", "cpf_cnpj": "123.456.789-00", "nome_razao_social": "Maria"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalid_params[0].name").value("cpf_cnpj"));
        assertThat(contar("clientes")).isEqualTo(1);
    }

    @Test
    @DisplayName("CT-CLI-004 Tipo incompatível com o documento (PJ com CPF) é rejeitado")
    void tipoIncompativel() throws Exception {
        postar("/clientes", tokenGerente, """
                {"tipo": "PJ", "cpf_cnpj": "%s", "nome_razao_social": "Maria"}""".formatted(GeradorDocumentos.cpf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CT-CLI-005 CPF/CNPJ já cadastrado retorna 409 (chave única)")
    void documentoDuplicado() throws Exception {
        String cpf = GeradorDocumentos.cpf();
        String corpo = """
                {"tipo": "PF", "cpf_cnpj": "%s", "nome_razao_social": "Maria"}""".formatted(cpf);
        postar("/clientes", tokenGerente, corpo).andExpect(status().isCreated());
        postar("/clientes", tokenGerente, corpo).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("CT-CLI-006 Nome ausente e e-mail inválido são reportados juntos em invalid_params")
    void variosErros() throws Exception {
        postar("/clientes", tokenGerente, """
                {"tipo": "PF", "cpf_cnpj": "%s", "email": "maria@invalido@"}""".formatted(GeradorDocumentos.cpf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalid_params.length()").value(2))
                .andExpect(jsonPath("$.invalid_params[0].name").value("email"))
                .andExpect(jsonPath("$.invalid_params[1].name").value("nome_razao_social"));
    }

    @Test
    @DisplayName("CT-CLI-007 Tentar cadastrar o documento reservado 00000000000 é rejeitado")
    void documentoReservado() throws Exception {
        postar("/clientes", tokenGerente, """
                {"tipo": "PF", "cpf_cnpj": "000.000.000-00", "nome_razao_social": "Fraude"}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CT-CLI-008 Consumidor Padrão não pode ser alterado nem inativado (422)")
    void consumidorPadraoProtegido() throws Exception {
        long id = jdbc.queryForObject("SELECT id FROM clientes WHERE cpf_cnpj = '00000000000'", Long.class);
        remendar("/clientes/" + id, tokenGerente, "{\"nome_razao_social\": \"Outro\"}")
                .andExpect(status().isUnprocessableContent());
        excluir("/clientes/" + id, tokenGerente).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("CT-CLI-009 PATCH pelo vendedor altera o nome e audita valores anterior/novo")
    void patchVendedor() throws Exception {
        Operador vendedor = criarVendedor("Jean", "4.5");
        long id = criarCliente(vendedor.token());
        remendar("/clientes/" + id, vendedor.token(), "{\"nome_razao_social\": \"Carlos Eduardo Silva\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome_razao_social").value("Carlos Eduardo Silva"));
        String novo = jdbc.queryForObject("SELECT valor_novo FROM logs_auditoria WHERE tipo_acao = 'CLIENTE_UPDATE'",
                String.class);
        assertThat(novo).contains("Carlos Eduardo Silva");
    }

    @Test
    @DisplayName("CT-CLI-010 Exclusão lógica pelo Gerente; Vendedor recebe 403")
    void exclusao() throws Exception {
        Operador vendedor = criarVendedor("Jean", "4.5");
        long id = criarCliente(vendedor.token());
        excluir("/clientes/" + id, vendedor.token()).andExpect(status().isForbidden());
        excluir("/clientes/" + id, tokenGerente).andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));
        obter("/clientes/" + id, tokenGerente).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CT-CLI-011 Consulta por CPF com máscara e por parte do nome")
    void consultas() throws Exception {
        String cpf = GeradorDocumentos.cpf();
        postar("/clientes", tokenGerente, """
                {"tipo": "PF", "cpf_cnpj": "%s", "nome_razao_social": "Joana Prado"}""".formatted(cpf))
                .andExpect(status().isCreated());
        obter("/clientes?cpf_cnpj=" + GeradorDocumentos.formatarCpf(cpf), tokenGerente)
                .andExpect(jsonPath("$.total_registros").value(1));
        obter("/clientes?nome_razao_social=prado", tokenGerente)
                .andExpect(jsonPath("$.dados[0].nome_razao_social").value("Joana Prado"));
    }

    @Test
    @DisplayName("CT-CLI-012 PATCH de tipo/documento: PF→PJ com CNPJ aceito; PJ mantendo CPF → 400; documento de outro → 409")
    void patchTipoEDocumento() throws Exception {
        long id = criarCliente(tokenGerente);
        String cpfOutro = GeradorDocumentos.cpf();
        postar("/clientes", tokenGerente, """
                {"tipo": "PF", "cpf_cnpj": "%s", "nome_razao_social": "Outro"}""".formatted(cpfOutro))
                .andExpect(status().isCreated());

        remendar("/clientes/" + id, tokenGerente, "{\"tipo\": \"PJ\"}").andExpect(status().isBadRequest());
        remendar("/clientes/" + id, tokenGerente, "{\"cpf_cnpj\": \"" + cpfOutro + "\"}").andExpect(status().isConflict());
        String cnpj = GeradorDocumentos.cnpj();
        remendar("/clientes/" + id, tokenGerente, """
                {"tipo": "PJ", "cpf_cnpj": "%s", "nome_razao_social": "Empresa LTDA", "uf": "rj"}""".formatted(cnpj))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("PJ"))
                .andExpect(jsonPath("$.cpf_cnpj").value(cnpj))
                .andExpect(jsonPath("$.uf").value("RJ"));
    }

    @Test
    @DisplayName("CT-CLI-013 PUT substitui o cadastro: campos opcionais omitidos são apagados")
    void putSubstitui() throws Exception {
        long id = criarCliente(tokenGerente);
        remendar("/clientes/" + id, tokenGerente, "{\"email\": \"a@b.com\", \"cidade\": \"Santos\"}").andExpect(status().isOk());
        String cpf = GeradorDocumentos.cpf();
        colocar("/clientes/" + id, tokenGerente, """
                {"tipo": "PF", "cpf_cnpj": "%s", "nome_razao_social": "Substituído"}""".formatted(cpf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome_razao_social").value("Substituído"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.cidade").doesNotExist());
    }
}
