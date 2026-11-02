package br.com.empresa.pdv.cliente;

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

import br.com.empresa.pdv.cliente.ClienteDtos.ClientePatch;
import br.com.empresa.pdv.cliente.ClienteDtos.ClienteRequisicao;
import br.com.empresa.pdv.cliente.ClienteDtos.ClienteResposta;
import br.com.empresa.pdv.cliente.ClienteDtos.InativacaoResposta;
import br.com.empresa.pdv.comum.Pagina;

/** UC08 — Cadastrar e Gerenciar Clientes. Exclusão lógica restrita ao Gerente. */
@RestController
@RequestMapping("/clientes")
public class ClienteController {

    private final ClienteService service;

    public ClienteController(ClienteService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClienteResposta cadastrar(@Valid @RequestBody ClienteRequisicao dados) {
        return service.cadastrar(dados);
    }

    @GetMapping
    public Pagina<ClienteResposta> listar(@RequestParam(required = false) Integer pagina,
                                          @RequestParam(required = false) Integer limite,
                                          @RequestParam(name = "cpf_cnpj", required = false) String cpfCnpj,
                                          @RequestParam(name = "nome_razao_social", required = false) String nome) {
        return service.listar(pagina, limite, cpfCnpj, nome);
    }

    @GetMapping("/{id}")
    public ClienteResposta buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PutMapping("/{id}")
    public ClienteResposta substituir(@PathVariable Long id, @Valid @RequestBody ClienteRequisicao dados) {
        return service.substituir(id, dados);
    }

    @PatchMapping("/{id}")
    public ClienteResposta atualizarParcial(@PathVariable Long id, @Valid @RequestBody ClientePatch dados) {
        return service.atualizarParcial(id, dados);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('GERENTE')")
    public InativacaoResposta inativar(@PathVariable Long id) {
        service.inativar(id);
        return new InativacaoResposta(true, "Cliente inativado com sucesso (Soft Delete).", id, false);
    }
}
