package br.com.empresa.pdv.caixa;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.empresa.pdv.caixa.CaixaDtos.AberturaRequisicao;
import br.com.empresa.pdv.caixa.CaixaDtos.AberturaResposta;
import br.com.empresa.pdv.caixa.CaixaDtos.CaixaResposta;
import br.com.empresa.pdv.caixa.CaixaDtos.FechamentoRequisicao;
import br.com.empresa.pdv.caixa.CaixaDtos.FechamentoResposta;
import br.com.empresa.pdv.caixa.CaixaDtos.MovimentacaoRequisicao;
import br.com.empresa.pdv.caixa.CaixaDtos.SangriaResposta;
import br.com.empresa.pdv.caixa.CaixaDtos.SuprimentoResposta;

/** UC02 Abertura, UC03 Fechamento e UC15 Sangria/Suprimento de caixa. */
@RestController
@RequestMapping("/caixas")
public class CaixaController {

    private final CaixaService service;

    public CaixaController(CaixaService service) {
        this.service = service;
    }

    @GetMapping("/atual")
    public CaixaResposta atual() {
        return service.atual();
    }

    @GetMapping("/{id}")
    public CaixaResposta buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping("/abertura")
    @ResponseStatus(HttpStatus.CREATED)
    public AberturaResposta abrir(@Valid @RequestBody AberturaRequisicao dados) {
        return service.abrir(dados.suprimentoInicial());
    }

    @PostMapping("/{id}/sangria")
    @PreAuthorize("hasRole('GERENTE')")
    public SangriaResposta sangria(@PathVariable Long id, @Valid @RequestBody MovimentacaoRequisicao dados) {
        return service.sangria(id, dados.valor(), dados.observacao());
    }

    @PostMapping("/{id}/suprimento")
    @PreAuthorize("hasRole('GERENTE')")
    public SuprimentoResposta suprimento(@PathVariable Long id, @Valid @RequestBody MovimentacaoRequisicao dados) {
        return service.suprimento(id, dados.valor(), dados.observacao());
    }

    @PostMapping("/{id}/fechamento")
    public FechamentoResposta fechar(@PathVariable Long id, @Valid @RequestBody FechamentoRequisicao dados) {
        return service.fechar(id, dados.valorFinalDinheiro());
    }
}
