package br.com.empresa.pdv.usuario;

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
import br.com.empresa.pdv.usuario.UsuarioDtos.InativacaoResposta;
import br.com.empresa.pdv.usuario.UsuarioDtos.UsuarioAtualizacao;
import br.com.empresa.pdv.usuario.UsuarioDtos.UsuarioCriacao;
import br.com.empresa.pdv.usuario.UsuarioDtos.UsuarioPatch;
import br.com.empresa.pdv.usuario.UsuarioDtos.UsuarioResposta;

/** UC13 — Gerenciar Funcionários/Usuários (somente Gerente). */
@RestController
@RequestMapping("/usuarios")
@PreAuthorize("hasRole('GERENTE')")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResposta cadastrar(@Valid @RequestBody UsuarioCriacao dados) {
        return service.cadastrar(dados);
    }

    @GetMapping
    public Pagina<UsuarioResposta> listar(@RequestParam(required = false) Integer pagina,
                                          @RequestParam(required = false) Integer limite) {
        return service.listar(pagina, limite);
    }

    @GetMapping("/{id}")
    public UsuarioResposta buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PutMapping("/{id}")
    public UsuarioResposta substituir(@PathVariable Long id, @Valid @RequestBody UsuarioAtualizacao dados) {
        return service.substituir(id, dados);
    }

    @PatchMapping("/{id}")
    public UsuarioResposta atualizarParcial(@PathVariable Long id, @Valid @RequestBody UsuarioPatch dados) {
        return service.atualizarParcial(id, dados);
    }

    @DeleteMapping("/{id}")
    public InativacaoResposta inativar(@PathVariable Long id) {
        service.inativar(id);
        return new InativacaoResposta(true, "Usuário inativado com sucesso (Soft Delete).", id, false);
    }
}
