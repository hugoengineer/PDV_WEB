package br.com.empresa.pdv.venda;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.empresa.pdv.venda.VendaDtos.ParcelamentoAlteracao;
import br.com.empresa.pdv.venda.VendaDtos.ParcelamentoResposta;

/** UC17 — Leitura pelos dois perfis (a tela de venda mostra as opções); alteração só pelo Gerente. */
@RestController
@RequestMapping("/configuracoes/parcelamento")
public class ParcelamentoController {

    private final ParcelamentoService service;

    public ParcelamentoController(ParcelamentoService service) {
        this.service = service;
    }

    @GetMapping
    public List<ParcelamentoResposta> listar() {
        return service.listar();
    }

    @PutMapping("/{modalidade}")
    @PreAuthorize("hasRole('GERENTE')")
    public ParcelamentoResposta alterar(@PathVariable ModalidadePagamento modalidade,
                                        @Valid @RequestBody ParcelamentoAlteracao dados) {
        return service.alterar(modalidade, dados.maxParcelas());
    }
}
