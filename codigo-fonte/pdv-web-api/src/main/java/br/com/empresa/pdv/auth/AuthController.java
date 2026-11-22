package br.com.empresa.pdv.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.empresa.pdv.auth.AuthDtos.AlterarSenhaRequisicao;
import br.com.empresa.pdv.auth.AuthDtos.LoginRequisicao;
import br.com.empresa.pdv.auth.AuthDtos.LoginResposta;
import br.com.empresa.pdv.auth.AuthDtos.MensagemResposta;
import br.com.empresa.pdv.auth.AuthDtos.TokenResposta;

/** UC01 — Autenticar no Sistema. */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    /** O IP vem do Tomcat; atrás de proxy interno, de X-Forwarded-For (server.forward-headers-strategy=native). */
    @PostMapping("/login")
    public LoginResposta login(@Valid @RequestBody LoginRequisicao requisicao, HttpServletRequest http) {
        return service.login(requisicao.cpf(), requisicao.senha(), http.getRemoteAddr());
    }

    @PostMapping("/refresh-token")
    public TokenResposta renovar() {
        return service.renovar();
    }

    @PostMapping("/logout")
    public MensagemResposta logout() {
        service.logout();
        return new MensagemResposta(true, "Sessão encerrada com sucesso.");
    }

    @PatchMapping("/alterar-senha")
    public MensagemResposta alterarSenha(@Valid @RequestBody AlterarSenhaRequisicao requisicao) {
        service.alterarSenha(requisicao.senhaAtual(), requisicao.novaSenha());
        return new MensagemResposta(true, "Senha alterada com sucesso.");
    }
}
