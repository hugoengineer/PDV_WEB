package br.com.empresa.pdv.suporte;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Gera CPFs/CNPJs sintéticos com dígitos verificadores corretos.
 * Evita usar documentos de pessoas reais nos testes (LGPD).
 */
public final class GeradorDocumentos {

    private static final AtomicLong SEQUENCIA = new AtomicLong(100_000_000L);

    private GeradorDocumentos() {
    }

    public static String cpf() {
        return cpfComBase(String.valueOf(SEQUENCIA.incrementAndGet()).substring(0, 9));
    }

    public static String cpfComBase(String nove) {
        String parcial = nove + digitoCpf(nove, 10);
        return parcial + digitoCpf(parcial, 11);
    }

    public static String cnpj() {
        String doze = String.format("%08d0001", SEQUENCIA.incrementAndGet() % 100_000_000L);
        String parcial = doze + digitoCnpj(doze, new int[]{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        return parcial + digitoCnpj(parcial, new int[]{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
    }

    /** Formata como 000.000.000-00, para testar entradas com máscara. */
    public static String formatarCpf(String cpf) {
        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
    }

    private static int digitoCpf(String base, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < base.length(); i++) {
            soma += (base.charAt(i) - '0') * (pesoInicial - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static int digitoCnpj(String base, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (base.charAt(i) - '0') * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
