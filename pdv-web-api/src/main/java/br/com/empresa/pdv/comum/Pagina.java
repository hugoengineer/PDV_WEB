package br.com.empresa.pdv.comum;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Envelope padrão das listagens (contrato v2.3, seção 1: "Padronização de Resposta das Listagens"). */
public record Pagina<T>(int pagina, int limite, long totalRegistros, int totalPaginas, List<T> dados) {

    public static <E, T> Pagina<T> de(Page<E> page, Function<E, T> conversor) {
        return new Pagina<>(
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getContent().stream().map(conversor).toList());
    }
}
