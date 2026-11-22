package br.com.empresa.pdv.auditoria;

import java.time.LocalDate;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.empresa.pdv.comum.Pagina;

/** UC16 — Consultar Logs de Auditoria (somente Gerente). */
@RestController
@RequestMapping("/auditoria")
public class AuditoriaController {

    private final AuditoriaService service;

    public AuditoriaController(AuditoriaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('GERENTE')")
    public Pagina<LogAuditoriaResposta> listar(@RequestParam(required = false) Integer pagina,
                                               @RequestParam(required = false) Integer limite,
                                               @RequestParam(name = "data_inicio", required = false) LocalDate dataInicio,
                                               @RequestParam(name = "data_fim", required = false) LocalDate dataFim,
                                               @RequestParam(name = "tipo_acao", required = false) TipoAcao tipoAcao) {
        return service.consultar(pagina, limite, dataInicio, dataFim, tipoAcao);
    }
}
