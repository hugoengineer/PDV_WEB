package br.com.empresa.pdv.venda;

import java.time.LocalDate;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
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
import br.com.empresa.pdv.venda.VendaDtos.PreviaRequisicao;
import br.com.empresa.pdv.venda.VendaDtos.PreviaResposta;
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

    /** Calcula subtotal, descontos e total a pagar sem gravar (a tela divide o pagamento sobre esse total). */
    @PostMapping("/previa")
    public PreviaResposta previa(@Valid @RequestBody PreviaRequisicao requisicao) {
        return service.previa(requisicao);
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

    /** Gerente: qualquer venda. Vendedor: só as próprias, com senha_autorizacao_gerente (UC11). */
    @PostMapping("/{id}/cancelar")
    public CancelamentoResposta cancelar(@PathVariable Long id, @Valid @RequestBody CancelamentoRequisicao dados) {
        service.cancelar(id, dados.motivoCancelamento(), dados.senhaAutorizacaoGerente());
        return new CancelamentoResposta(true, "Venda cancelada com sucesso. Estoque estornado.", id,
                StatusVenda.CANCELADA);
    }
}
