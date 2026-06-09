package com.seudominio.jasperrunner.controller;

import com.seudominio.jasperrunner.service.FolderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/folders")
public class FolderController {

    private static final Logger log = LoggerFactory.getLogger(FolderController.class);

    private final FolderService service;

    public FolderController(FolderService service) {
        this.service = service;
    }

    @PostMapping("/new")
    public String create(@RequestParam("name") String name,
                         @RequestParam(value = "description", required = false) String description,
                         @RequestParam(value = "parentId", required = false) Long parentId,
                         RedirectAttributes ra) {
        try {
            service.create(name, description, parentId);
            ra.addFlashAttribute("success", "Pasta '" + name + "' criada com sucesso!");
        } catch (Exception e) {
            log.error("Erro ao criar pasta", e);
            ra.addFlashAttribute("error", "Erro ao criar pasta: " + e.getMessage());
        }
        return parentId != null ? "redirect:/folders/" + parentId : "redirect:/";
    }

    @PostMapping("/{id}/rename")
    public String rename(@PathVariable Long id,
                         @RequestParam("name") String name,
                         @RequestParam(value = "description", required = false) String description,
                         RedirectAttributes ra) {
        try {
            service.update(id, name, description);
            ra.addFlashAttribute("success", "Pasta renomeada com sucesso!");
        } catch (Exception e) {
            log.error("Erro ao renomear pasta {}", id, e);
            ra.addFlashAttribute("error", "Erro ao renomear: " + e.getMessage());
        }
        return "redirect:/folders/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Long parentId = service.findParentId(id).orElse(null);
            service.delete(id);
            ra.addFlashAttribute("success", "Pasta excluída com sucesso!");
            return parentId != null ? "redirect:/folders/" + parentId : "redirect:/";
        } catch (Exception e) {
            log.error("Erro ao excluir pasta {}", id, e);
            ra.addFlashAttribute("error", "Erro ao excluir pasta: " + e.getMessage());
            return "redirect:/";
        }
    }

    @PostMapping("/{id}/move")
    public String move(@PathVariable Long id,
                       @RequestParam(value = "newParentId", required = false) Long newParentId,
                       RedirectAttributes ra) {
        try {
            service.move(id, newParentId);
            ra.addFlashAttribute("success", "Pasta movida com sucesso!");
        } catch (Exception e) {
            log.error("Erro ao mover pasta {}", id, e);
            ra.addFlashAttribute("error", "Erro ao mover: " + e.getMessage());
        }
        return newParentId != null ? "redirect:/folders/" + newParentId : "redirect:/";
    }
}
