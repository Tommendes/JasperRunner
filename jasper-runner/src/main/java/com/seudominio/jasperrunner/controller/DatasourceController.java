package com.seudominio.jasperrunner.controller;

import com.seudominio.jasperrunner.dto.ConnectionTestResult;
import com.seudominio.jasperrunner.model.DataSourceConfig;
import com.seudominio.jasperrunner.service.DatasourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/datasources")
public class DatasourceController {

    private static final Logger log = LoggerFactory.getLogger(DatasourceController.class);

    private final DatasourceService service;

    public DatasourceController(DatasourceService service) {
        this.service = service;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("datasources", service.findAll());
        return "datasources";
    }

    @GetMapping("/new")
    public String showNew(Model model) {
        model.addAttribute("datasource", new DataSourceConfig());
        model.addAttribute("editing", false);
        model.addAttribute("driverOptions", driverOptions());
        return "datasource-form";
    }

    @PostMapping
    public String create(@ModelAttribute DataSourceConfig datasource,
                         @RequestParam(value = "rawPassword", required = false) String rawPassword,
                         RedirectAttributes ra) {
        try {
            service.create(datasource, rawPassword);
            ra.addFlashAttribute("success", "Fonte de dados '" + datasource.getName() + "' criada com sucesso!");
            return "redirect:/datasources";
        } catch (Exception e) {
            log.error("Erro ao criar fonte de dados", e);
            ra.addFlashAttribute("error", "Erro ao criar: " + e.getMessage());
            return "redirect:/datasources/new";
        }
    }

    @GetMapping("/{id}/edit")
    public String showEdit(@PathVariable Long id, Model model, RedirectAttributes ra) {
        return service.findById(id).map(ds -> {
            model.addAttribute("datasource", ds);
            model.addAttribute("editing", true);
            model.addAttribute("driverOptions", driverOptions());
            return "datasource-form";
        }).orElseGet(() -> {
            ra.addFlashAttribute("error", "Fonte de dados não encontrada.");
            return "redirect:/datasources";
        });
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute DataSourceConfig datasource,
                         @RequestParam(value = "rawPassword", required = false) String rawPassword,
                         RedirectAttributes ra) {
        try {
            service.update(id, datasource, rawPassword);
            ra.addFlashAttribute("success", "Fonte de dados atualizada com sucesso!");
            return "redirect:/datasources";
        } catch (Exception e) {
            log.error("Erro ao atualizar fonte de dados {}", id, e);
            ra.addFlashAttribute("error", "Erro ao atualizar: " + e.getMessage());
            return "redirect:/datasources/" + id + "/edit";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            service.delete(id);
            ra.addFlashAttribute("success", "Fonte de dados excluída com sucesso!");
        } catch (Exception e) {
            log.error("Erro ao excluir fonte de dados {}", id, e);
            ra.addFlashAttribute("error", "Erro ao excluir: " + e.getMessage());
        }
        return "redirect:/datasources";
    }

    @PostMapping("/{id}/test")
    @ResponseBody
    public ConnectionTestResult testConnection(@PathVariable Long id) {
        return service.testConnection(id);
    }

    private java.util.List<String[]> driverOptions() {
        return java.util.List.of(
            new String[]{"org.postgresql.Driver", "PostgreSQL"},
            new String[]{"com.mysql.cj.jdbc.Driver", "MySQL / MariaDB"},
            new String[]{"com.microsoft.sqlserver.jdbc.SQLServerDriver", "Microsoft SQL Server"},
            new String[]{"org.h2.Driver", "H2"}
        );
    }
}
