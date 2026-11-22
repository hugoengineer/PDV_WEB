package br.com.empresa.pdv.cupom;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.empresa.pdv.comum.Pagina;
import br.com.empresa.pdv.cupom.CupomDtos.CupomPatch;
import br.com.empresa.pdv.cupom.CupomDtos.CupomRequisicao;
import br.com.empresa.pdv.cupom.CupomDtos.CupomResposta;
import br.com.empresa.pdv.cupom.CupomDtos.InativacaoResposta;

/** UC14 — Emitir e Gerenciar Cupons de Desconto (escrita: Gerente; leitura: todos). */
@RestController
@RequestMapping("/cupons")
public class CupomController {

    private final CupomService service;

    public CupomController(CupomService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('GERENTE')")
    public CupomResposta emitir(@Valid @RequestBody CupomRequisicao dados) {
        return service.emitir(dados);
    }

    @GetMapping
    public Pagina<CupomResposta> listar(@RequestParam(required = false) Integer pagina,
                                        @RequestParam(required = false) Integer limite,
                                        @RequestParam(required = false) String codigo,
                                        @RequestParam(required = false) Boolean vigente) {
        return service.listar(pagina, limite, codigo, vigente);
    }

    @GetMapping("/{id}")
    public CupomResposta buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('GERENTE')")
    public CupomResposta substituir(@PathVariable Long id, @Valid @RequestBody CupomRequisicao dados) {
        return service.substituir(id, dados);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('GERENTE')")
    public CupomResposta atualizarParcial(@PathVariable Long id, @Valid @RequestBody CupomPatch dados) {
        return service.atualizarParcial(id, dados);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('GERENTE')")
    public InativacaoResposta inativar(@PathVariable Long id) {
        service.inativar(id);
        return new InativacaoResposta(true, "Cupom inativado com sucesso.", id, false);
    }
}
