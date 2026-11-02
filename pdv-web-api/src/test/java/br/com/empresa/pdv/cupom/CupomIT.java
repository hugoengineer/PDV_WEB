package br.com.empresa.pdv.cupom;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.empresa.pdv.suporte.TesteIntegracao;

/** UC14 — Emitir e Gerenciar Cupons de Desconto. */
@DisplayName("Cupons de desconto (integração)")
class CupomIT extends TesteIntegracao {

    private final OffsetDateTime agora = OffsetDateTime.now();

    @Test
    @DisplayName("CT-CUP-011 Gerente emite cupom; código é normalizado em maiúsculas e o cupom fica vigente")
    void emitir() throws Exception {
        postar("/cupons", tokenGerente, """
                {"codigo": "desconto10", "tipo_desconto": "PERCENTUAL", "valor_desconto": 10.00,
                 "data_inicio_validade": "%s", "data_termino_validade": "%s"}"""
                .formatted(agora.minusDays(1), agora.plusDays(30)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("DESCONTO10"))
                .andExpect(jsonPath("$.vigente").value(true))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @DisplayName("CT-CUP-012 Percentual acima de 100 e término anterior ao início são rejeitados (400)")
    void regrasInvalidas() throws Exception {
        postar("/cupons", tokenGerente, """
                {"codigo": "MUITO", "tipo_desconto": "PERCENTUAL", "valor_desconto": 100.01,
                 "data_inicio_validade": "%s", "data_termino_validade": "%s"}"""
                .formatted(agora, agora.plusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalid_params[0].name").value("valor_desconto"));
        postar("/cupons", tokenGerente, """
                {"codigo": "INVERTIDO", "tipo_desconto": "VALOR_FIXO", "valor_desconto": 5.00,
                 "data_inicio_validade": "%s", "data_termino_validade": "%s"}"""
                .formatted(agora, agora.minusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalid_params[0].name").value("data_termino_validade"));
    }

    @Test
    @DisplayName("CT-CUP-013 Código de cupom duplicado retorna 409")
    void duplicado() throws Exception {
        criarCupom("UNICO", "VALOR_FIXO", "5.00", agora, agora.plusDays(1));
        postar("/cupons", tokenGerente, """
                {"codigo": "unico", "tipo_desconto": "VALOR_FIXO", "valor_desconto": 5.00,
                 "data_inicio_validade": "%s", "data_termino_validade": "%s"}"""
                .formatted(agora, agora.plusDays(1)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("CT-CUP-014 Filtro vigente separa cupons ativos/no prazo de expirados, futuros e inativos")
    void filtroVigente() throws Exception {
        criarCupom("VIGENTE", "PERCENTUAL", "5", agora.minusDays(1), agora.plusDays(1));
        criarCupom("EXPIRADO", "PERCENTUAL", "5", agora.minusDays(10), agora.minusDays(1));
        criarCupom("FUTURO", "PERCENTUAL", "5", agora.plusDays(1), agora.plusDays(10));
        long inativo = criarCupom("INATIVO", "PERCENTUAL", "5", agora.minusDays(1), agora.plusDays(1));
        excluir("/cupons/" + inativo, tokenGerente).andExpect(status().isOk());

        obter("/cupons?vigente=true", tokenGerente)
                .andExpect(jsonPath("$.total_registros").value(1))
                .andExpect(jsonPath("$.dados[0].codigo").value("VIGENTE"));
        obter("/cupons?vigente=false", tokenGerente).andExpect(jsonPath("$.total_registros").value(3));
    }

    @Test
    @DisplayName("CT-CUP-015 Vendedor consulta cupons, mas não emite nem altera (403)")
    void vendedor() throws Exception {
        Operador vendedor = criarVendedor("Jean", "4.5");
        long id = criarCupom("LEITURA", "PERCENTUAL", "5", agora.minusDays(1), agora.plusDays(1));
        obter("/cupons/" + id, vendedor.token()).andExpect(status().isOk());
        remendar("/cupons/" + id, vendedor.token(), "{\"valor_desconto\": 50}").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CT-CUP-016 PATCH prorroga a validade e altera o valor (exemplo do contrato)")
    void prorrogar() throws Exception {
        long id = criarCupom("DESCONTO10", "PERCENTUAL", "10.00", agora.minusDays(5), agora.plusDays(5));
        remendar("/cupons/" + id, tokenGerente, """
                {"data_termino_validade": "%s", "valor_desconto": 15.00}""".formatted(agora.plusDays(30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor_desconto").value(15.00));
    }

    @Test
    @DisplayName("CT-CUP-017 PUT substitui o cupom; cupom inativo ou inexistente → 404; PATCH para código já usado → 409")
    void putEInexistentes() throws Exception {
        long id = criarCupom("ORIGINAL", "PERCENTUAL", "10", agora.minusDays(1), agora.plusDays(1));
        criarCupom("OCUPADO", "PERCENTUAL", "5", agora.minusDays(1), agora.plusDays(1));
        colocar("/cupons/" + id, tokenGerente, """
                {"codigo": "NOVO", "tipo_desconto": "VALOR_FIXO", "valor_desconto": 20.00,
                 "data_inicio_validade": "%s", "data_termino_validade": "%s"}"""
                .formatted(agora.minusDays(1), agora.plusDays(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("NOVO"))
                .andExpect(jsonPath("$.tipo_desconto").value("VALOR_FIXO"));
        remendar("/cupons/" + id, tokenGerente, "{\"codigo\": \"ocupado\"}").andExpect(status().isConflict());

        excluir("/cupons/" + id, tokenGerente).andExpect(status().isOk());
        remendar("/cupons/" + id, tokenGerente, "{\"valor_desconto\": 1}").andExpect(status().isNotFound());
        obter("/cupons/999", tokenGerente).andExpect(status().isNotFound());
    }
}
