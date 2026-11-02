package br.com.empresa.pdv.venda;

import java.time.LocalDate;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.empresa.pdv.comum.Pagina;
import br.com.empresa.pdv.venda.VendaDtos.CancelamentoRequisicao;
import br.com.empresa.pdv.venda.VendaDtos.CancelamentoResposta;
import br.com.empresa.pdv.venda.VendaDtos.VendaDetalhe;
import br.com.empresa.pdv.venda.VendaDtos.VendaRegistradaResposta;
import br.com.empresa.pdv.venda.VendaDtos.VendaRequisicao;
import br.com.empresa.pdv.venda.VendaDtos.VendaResumo;

/**
 * UC04 Registrar Venda, UC10 Comissões e UC11 Cancelar Venda.
 * Vendas são imutáveis: não existem PUT, PATCH nem DELETE (respondem 405).
 */
@RestController
@RequestMapping("/vendas")
public class VendaController {

    private final VendaService service;

    public VendaController(VendaService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VendaRegistradaResposta registrar(@Valid @RequestBody VendaRequisicao requisicao) {
        return service.registrar(requisicao);
    }

    @GetMapping
    public Pagina<VendaResumo> listar(@RequestParam(required = false) Integer pagina,
                                      @RequestParam(required = false) Integer limite,
                                      @RequestParam(name = "data_inicio", required = false) LocalDate dataInicio,
                                      @RequestParam(name = "data_fim", required = false) LocalDate dataFim,
                                      @RequestParam(name = "usuario_id", required = false) Long usuarioId,
                                      @RequestParam(required = false) StatusVenda status) {
        return service.listar(pagina, limite, dataInicio, dataFim, usuarioId, status);
    }

    @GetMapping("/comissoes")
    public Object comissoes(@RequestParam(name = "data_inicio", required = false) LocalDate dataInicio,
                            @RequestParam(name = "data_fim", required = false) LocalDate dataFim,
                            @RequestParam(name = "vendedor_id", required = false) Long vendedorId) {
        return service.comissoes(dataInicio, dataFim, vendedorId);
    }

    @GetMapping("/{id}")
    public VendaDetalhe detalhar(@PathVariable Long id) {
        return service.detalhar(id);
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasRole('GERENTE')")
    public CancelamentoResposta cancelar(@PathVariable Long id, @Valid @RequestBody CancelamentoRequisicao dados) {
        service.cancelar(id, dados.motivoCancelamento());
        return new CancelamentoResposta(true, "Venda cancelada com sucesso. Estoque estornado.", id,
                StatusVenda.CANCELADA);
    }
}
