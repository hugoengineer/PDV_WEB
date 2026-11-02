package br.com.empresa.pdv.venda;

import java.math.BigDecimal;

/** Projeção da consulta de comissões (UC10). */
public record ConsolidadoVendedor(Long usuarioId, long quantidadeVendas, BigDecimal valorTotalVendido,
                                  BigDecimal totalComissao) {
}
