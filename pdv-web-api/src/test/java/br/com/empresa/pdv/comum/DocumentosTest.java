package br.com.empresa.pdv.comum;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import br.com.empresa.pdv.suporte.GeradorDocumentos;

/**
 * Técnica: particionamento de equivalência + valores-limite de tamanho.
 * Partições do CPF: válido | DV incorreto | dígitos repetidos | tamanho != 11 | nulo/vazio/não numérico.
 */
@DisplayName("Validação de CPF/CNPJ (Documentos)")
class DocumentosTest {

    @Test
    @DisplayName("CT-DOC-001 CPF válido sem máscara é aceito")
    void cpfValidoSemMascara() {
        assertThat(Documentos.cpfValido(GeradorDocumentos.cpf())).isTrue();
    }

    @Test
    @DisplayName("CT-DOC-002 CPF válido com máscara (000.000.000-00) é aceito")
    void cpfValidoComMascara() {
        assertThat(Documentos.cpfValido(GeradorDocumentos.formatarCpf(GeradorDocumentos.cpf()))).isTrue();
    }

    @Test
    @DisplayName("CT-DOC-003 CPF com primeiro dígito verificador incorreto é rejeitado")
    void cpfPrimeiroDigitoErrado() {
        String cpf = GeradorDocumentos.cpf();
        assertThat(Documentos.cpfValido(trocarDigito(cpf, 9))).isFalse();
    }

    @Test
    @DisplayName("CT-DOC-004 CPF com segundo dígito verificador incorreto é rejeitado")
    void cpfSegundoDigitoErrado() {
        String cpf = GeradorDocumentos.cpf();
        assertThat(Documentos.cpfValido(trocarDigito(cpf, 10))).isFalse();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0}")
    @ValueSource(strings = {"00000000000", "11111111111", "99999999999"})
    @DisplayName("CT-DOC-005 CPF com todos os dígitos iguais é rejeitado (inclui o documento do Consumidor Padrão)")
    void cpfRepetido(String cpf) {
        assertThat(Documentos.cpfValido(cpf)).isFalse();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0} dígitos")
    @ValueSource(ints = {10, 12})
    @DisplayName("CT-DOC-006 CPF com 10 ou 12 dígitos (fora do limite de 11) é rejeitado")
    void cpfTamanhoFora(int tamanho) {
        String cpf = GeradorDocumentos.cpf();
        String alterado = tamanho == 10 ? cpf.substring(0, 10) : cpf + "0";
        assertThat(Documentos.cpfValido(alterado)).isFalse();
    }

    @ParameterizedTest(name = "{displayName} [{index}] \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "abcdefghijk", "529.982.247-2X"})
    @DisplayName("CT-DOC-007 CPF nulo, vazio ou não numérico é rejeitado")
    void cpfAusenteOuInvalido(String valor) {
        assertThat(Documentos.cpfValido(valor)).isFalse();
    }

    @Test
    @DisplayName("CT-DOC-008 CNPJ válido com e sem máscara é aceito")
    void cnpjValido() {
        String cnpj = GeradorDocumentos.cnpj();
        String mascarado = cnpj.substring(0, 2) + "." + cnpj.substring(2, 5) + "." + cnpj.substring(5, 8) + "/"
                + cnpj.substring(8, 12) + "-" + cnpj.substring(12);
        assertThat(Documentos.cnpjValido(cnpj)).isTrue();
        assertThat(Documentos.cnpjValido(mascarado)).isTrue();
    }

    @Test
    @DisplayName("CT-DOC-009 CNPJ com dígito verificador incorreto é rejeitado")
    void cnpjDigitoErrado() {
        String cnpj = GeradorDocumentos.cnpj();
        assertThat(Documentos.cnpjValido(trocarDigito(cnpj, 12))).isFalse();
        assertThat(Documentos.cnpjValido(trocarDigito(cnpj, 13))).isFalse();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0}")
    @ValueSource(strings = {"00000000000000", "1234567800019", "123456780001950"})
    @DisplayName("CT-DOC-010 CNPJ repetido ou com 13/15 dígitos é rejeitado")
    void cnpjForaDoPadrao(String cnpj) {
        assertThat(Documentos.cnpjValido(cnpj)).isFalse();
    }

    @Test
    @DisplayName("CT-DOC-012 Limite do cálculo do DV: resto 2 gera dígito 9 (resto < 2 gera 0)")
    void restoDoisGeraNove() {
        // DV = 9 só ocorre quando o resto da divisão por 11 é exatamente 2 (fronteira da regra "resto < 2 → 0")
        String cpfComNove = gerarAte(GeradorDocumentos::cpf, d -> d.charAt(9) == '9' || d.charAt(10) == '9');
        String cnpjComNove = gerarAte(GeradorDocumentos::cnpj, d -> d.charAt(12) == '9' || d.charAt(13) == '9');
        String cpfComZero = gerarAte(GeradorDocumentos::cpf, d -> d.charAt(9) == '0' || d.charAt(10) == '0');

        assertThat(Documentos.cpfValido(cpfComNove)).isTrue();
        assertThat(Documentos.cnpjValido(cnpjComNove)).isTrue();
        assertThat(Documentos.cpfValido(cpfComZero)).isTrue();
    }

    private static String gerarAte(java.util.function.Supplier<String> gerador,
                                   java.util.function.Predicate<String> condicao) {
        for (int i = 0; i < 10_000; i++) {
            String documento = gerador.get();
            if (condicao.test(documento)) {
                return documento;
            }
        }
        throw new IllegalStateException("Nenhum documento atendeu à condição");
    }

    @Test
    @DisplayName("CT-DOC-011 CPF não é aceito como CNPJ e vice-versa")
    void tiposNaoSeMisturam() {
        assertThat(Documentos.cnpjValido(GeradorDocumentos.cpf())).isFalse();
        assertThat(Documentos.cpfValido(GeradorDocumentos.cnpj())).isFalse();
    }

    private static String trocarDigito(String documento, int posicao) {
        char original = documento.charAt(posicao);
        char trocado = original == '9' ? '0' : (char) (original + 1);
        return documento.substring(0, posicao) + trocado + documento.substring(posicao + 1);
    }
}
