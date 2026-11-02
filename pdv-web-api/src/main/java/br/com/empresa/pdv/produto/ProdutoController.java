package br.com.empresa.pdv.produto;

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
import br.com.empresa.pdv.produto.ProdutoDtos.InativacaoResposta;
import br.com.empresa.pdv.produto.ProdutoDtos.ProdutoPatch;
import br.com.empresa.pdv.produto.ProdutoDtos.ProdutoRequisicao;
import br.com.empresa.pdv.produto.ProdutoDtos.ProdutoResposta;

/** UC09 — Consultar Estoque (Vendedor e Gerente) e UC12 — Gerenciar Catálogo (Gerente). */
@RestController
@RequestMapping("/produtos")
public class ProdutoController {

    private final ProdutoService service;

    public ProdutoController(ProdutoService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('GERENTE')")
    public ProdutoResposta cadastrar(@Valid @RequestBody ProdutoRequisicao dados) {
        return service.cadastrar(dados);
    }

    @GetMapping
    public Pagina<ProdutoResposta> listar(@RequestParam(required = false) Integer pagina,
                                          @RequestParam(required = false) Integer limite,
                                          @RequestParam(required = false) String sku,
                                          @RequestParam(required = false) String nome,
                                          @RequestParam(name = "estoque_critico", required = false) Boolean estoqueCritico,
                                          @RequestParam(name = "incluir_inativos", required = false) Boolean incluirInativos) {
        return service.listar(pagina, limite, sku, nome, estoqueCritico, incluirInativos);
    }

    @GetMapping("/{id}")
    public ProdutoResposta buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('GERENTE')")
    public ProdutoResposta substituir(@PathVariable Long id, @Valid @RequestBody ProdutoRequisicao dados) {
        return service.substituir(id, dados);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('GERENTE')")
    public ProdutoResposta atualizarParcial(@PathVariable Long id, @Valid @RequestBody ProdutoPatch dados) {
        return service.atualizarParcial(id, dados);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('GERENTE')")
    public InativacaoResposta inativar(@PathVariable Long id) {
        service.inativar(id);
        return new InativacaoResposta(true, "Produto inativado com sucesso (Soft Delete).", id, false);
    }
}
