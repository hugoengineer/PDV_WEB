package br.com.empresa.pdv.comum.erro;

/** Converte nomes de propriedades Java (camelCase) para o padrão do contrato JSON (snake_case). */
public final class NomesJson {

    private NomesJson() {
    }

    public static String paraSnakeCase(String nome) {
        if (nome == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(nome.length() + 4);
        for (char c : nome.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
