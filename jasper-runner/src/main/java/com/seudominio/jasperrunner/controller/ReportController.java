package com.seudominio.jasperrunner.controller;

import com.seudominio.jasperrunner.dto.ReportParameterInfo;
import com.seudominio.jasperrunner.model.DataSourceConfig;
import com.seudominio.jasperrunner.model.ReportDefinition;
import com.seudominio.jasperrunner.model.ReportFolder;
import com.seudominio.jasperrunner.service.DatasourceService;
import com.seudominio.jasperrunner.service.FolderService;
import com.seudominio.jasperrunner.service.ReportService;
import jakarta.servlet.http.HttpSession;
import net.sf.jasperreports.engine.JRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);
    private static final String CLIPBOARD_KEY = "clipboard";

    private final ReportService reportService;
    private final FolderService folderService;
    private final DatasourceService datasourceService;

    public ReportController(ReportService reportService, FolderService folderService,
                            DatasourceService datasourceService) {
        this.reportService = reportService;
        this.folderService = folderService;
        this.datasourceService = datasourceService;
    }

    // ─────────────────── Página Principal (Biblioteca) ───────────────────

    @GetMapping("/")
    public String index(Model model, HttpSession session) {
        return loadFolderView(null, model, session);
    }

    @GetMapping("/folders/{folderId}")
    public String showFolder(@PathVariable Long folderId, Model model, HttpSession session) {
        return loadFolderView(folderId, model, session);
    }

    private String loadFolderView(Long folderId, Model model, HttpSession session) {
        ReportFolder currentFolder = folderId != null
            ? folderService.findById(folderId).orElse(null)
            : null;

        model.addAttribute("currentFolder", currentFolder);
        model.addAttribute("reports", reportService.findByFolder(folderId));
        try {
            model.addAttribute("resources", reportService.findResourcesByFolder(currentFolder));
        } catch (IOException e) {
            log.warn("Não foi possível listar recursos da pasta", e);
            model.addAttribute("resources", List.of());
        }
        model.addAttribute("rootFolders", folderService.getRootFolders());
        model.addAttribute("subFolders", folderId != null
            ? folderService.findChildFolders(folderId)
            : folderService.getRootFolders());
        model.addAttribute("clipboard", session.getAttribute(CLIPBOARD_KEY));
        model.addAttribute("datasources", datasourceService.findAll());
        return "index";
    }

    // ───────────────────────── Upload de JRXML ───────────────────────────

    @PostMapping("/reports/upload")
    public Object upload(@RequestParam("file") MultipartFile file,
                         @RequestParam(value = "name", required = false) String name,
                         @RequestParam(value = "description", required = false) String description,
                         @RequestParam(value = "folderId", required = false) Long folderId,
                         @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                         RedirectAttributes ra) {
        boolean ajax = "XMLHttpRequest".equals(requestedWith);
        try {
            if (file.isEmpty()) {
                String message = "Nenhum arquivo selecionado.";
                if (ajax) {
                    return ResponseEntity.badRequest().body(Map.of("success", false, "message", message));
                }
                ra.addFlashAttribute("error", message);
                return redirectToFolder(folderId);
            }

            ReportFolder folder = folderId != null ? folderService.findById(folderId).orElse(null) : null;
            ReportDefinition saved = reportService.upload(file, name, description, folder);
            String message = saved != null
                ? "Relatório '" + saved.getName() + "' enviado com sucesso!"
                : "Recurso '" + file.getOriginalFilename() + "' enviado com sucesso!";

            if (ajax) {
                return ResponseEntity.ok(Map.of("success", true, "message", message));
            }
            ra.addFlashAttribute("success", message);
        } catch (Exception e) {
            log.error("Erro no upload de relatório", e);
            if (ajax) {
                return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "Erro ao salvar arquivo: " + e.getMessage()));
            }
            ra.addFlashAttribute("error", "Erro ao salvar arquivo: " + e.getMessage());
        }
        return redirectToFolder(folderId);
    }

    // ───────────────────────── Editar Metadados ──────────────────────────

    @GetMapping("/reports/{id}/edit")
    public String showEdit(@PathVariable Long id, Model model, RedirectAttributes ra) {
        return reportService.findByIdWithFolder(id).map(report -> {
            model.addAttribute("report", report);
            model.addAttribute("rootFolders", folderService.getRootFolders());
            return "report-form";
        }).orElseGet(() -> {
            ra.addFlashAttribute("error", "Relatório não encontrado.");
            return "redirect:/";
        });
    }

    @PostMapping("/reports/{id}/edit")
    public String saveEdit(@PathVariable Long id,
                           @RequestParam("name") String name,
                           @RequestParam(value = "description", required = false) String description,
                           RedirectAttributes ra) {
        try {
            ReportDefinition report = reportService.updateMetadata(id, name, description);
            ra.addFlashAttribute("success", "Relatório atualizado com sucesso!");
            Long folderId = reportService.findFolderIdByReportId(id).orElse(null);
            return redirectToFolder(folderId);
        } catch (Exception e) {
            log.error("Erro ao atualizar relatório {}", id, e);
            ra.addFlashAttribute("error", "Erro ao atualizar: " + e.getMessage());
            return "redirect:/reports/" + id + "/edit";
        }
    }

    // ──────────────────── Formulário de Parâmetros ───────────────────────

    @GetMapping("/reports/{id}/params")
    public String showParams(@PathVariable Long id, Model model, RedirectAttributes ra) {
        return reportService.findByIdWithFolder(id).map(report -> {
            try {
                List<ReportParameterInfo> params = reportService.getParameters(id);
                List<DataSourceConfig> datasources = datasourceService.findAll();

                model.addAttribute("report", report);
                model.addAttribute("parameters", params);
                model.addAttribute("datasources", datasources);
                model.addAttribute("formats", List.of("PDF", "XLSX", "HTML", "DOCX", "CSV"));
                return "report-params";
            } catch (JRException e) {
                log.error("Erro ao ler parâmetros do relatório {}", id, e);
                ra.addFlashAttribute("error", "Erro ao compilar relatório: " + e.getMessage());
                return "redirect:/";
            }
        }).orElseGet(() -> {
            ra.addFlashAttribute("error", "Relatório não encontrado.");
            return "redirect:/";
        });
    }

    // ──────────────────────── Execução / Download ────────────────────────

    @PostMapping("/reports/{id}/run")
    public ResponseEntity<byte[]> runReport(@PathVariable Long id,
                                            @RequestParam Map<String, String> allParams,
                                            @RequestParam("datasourceId") Long datasourceId,
                                            @RequestParam("outputFormat") String outputFormat,
                                            RedirectAttributes ra) {
        try {
            // Remove campos de controle do formulário
            Map<String, String> reportParams = new HashMap<>(allParams);
            reportParams.remove("datasourceId");
            reportParams.remove("outputFormat");
            reportParams.remove("_csrf");

            byte[] reportBytes = reportService.generateReport(id, reportParams, datasourceId, outputFormat);

            String contentType = resolveContentType(outputFormat);
            String fileName = resolveFileName(id, outputFormat);

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(reportBytes);
        } catch (Exception e) {
            log.error("Erro ao gerar relatório {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ──────────────────────── Excluir Relatório ──────────────────────────

    @PostMapping("/reports/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            reportService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Relatório não encontrado: " + id));
            Long folderId = reportService.findFolderIdByReportId(id).orElse(null);
            reportService.delete(id);
            ra.addFlashAttribute("success", "Relatório excluído com sucesso!");
            return redirectToFolder(folderId);
        } catch (Exception e) {
            log.error("Erro ao excluir relatório {}", id, e);
            ra.addFlashAttribute("error", "Erro ao excluir: " + e.getMessage());
            return "redirect:/";
        }
    }

    // ──────────────────── Clipboard (Copiar / Cortar) ────────────────────

    @PostMapping("/reports/{id}/copy")
    @ResponseBody
    public Map<String, Object> copyToClipboard(@PathVariable Long id, HttpSession session) {
        Map<String, Object> clip = new HashMap<>();
        clip.put("reportId", id);
        clip.put("action", "copy");
        session.setAttribute(CLIPBOARD_KEY, clip);
        return Map.of("status", "ok", "action", "copy");
    }

    @PostMapping("/reports/{id}/cut")
    @ResponseBody
    public Map<String, Object> cutToClipboard(@PathVariable Long id, HttpSession session) {
        Map<String, Object> clip = new HashMap<>();
        clip.put("reportId", id);
        clip.put("action", "cut");
        session.setAttribute(CLIPBOARD_KEY, clip);
        return Map.of("status", "ok", "action", "cut");
    }

    @PostMapping("/reports/paste")
    public String paste(@RequestParam(value = "folderId", required = false) Long folderId,
                        HttpSession session, RedirectAttributes ra) {
        @SuppressWarnings("unchecked")
        Map<String, Object> clipboard = (Map<String, Object>) session.getAttribute(CLIPBOARD_KEY);

        if (clipboard == null) {
            ra.addFlashAttribute("error", "Nenhum relatório na área de transferência.");
            return redirectToFolder(folderId);
        }

        try {
            Long reportId = ((Number) clipboard.get("reportId")).longValue();
            String action = (String) clipboard.get("action");
            ReportFolder targetFolder = folderId != null ? folderService.findById(folderId).orElse(null) : null;

            if ("copy".equals(action)) {
                reportService.copyTo(reportId, targetFolder);
                ra.addFlashAttribute("success", "Relatório copiado com sucesso!");
            } else if ("cut".equals(action)) {
                reportService.moveTo(reportId, targetFolder);
                ra.addFlashAttribute("success", "Relatório movido com sucesso!");
                session.removeAttribute(CLIPBOARD_KEY);
            }
        } catch (Exception e) {
            log.error("Erro ao colar relatório", e);
            ra.addFlashAttribute("error", "Erro ao colar: " + e.getMessage());
        }

        return redirectToFolder(folderId);
    }

    // ──────────────────────── Login ──────────────────────────────────────

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    // ──────────────────────── Helpers ────────────────────────────────────

    private String redirectToFolder(Long folderId) {
        return folderId != null ? "redirect:/folders/" + folderId : "redirect:/";
    }

    private String resolveContentType(String format) {
        return switch (format.toUpperCase()) {
            case "PDF"  -> "application/pdf";
            case "XLSX" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "HTML" -> "text/html; charset=UTF-8";
            case "DOCX" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "CSV"  -> "text/csv; charset=UTF-8";
            default     -> "application/octet-stream";
        };
    }

    private String resolveFileName(Long reportId, String format) {
        String reportName = reportService.findById(reportId)
            .map(ReportDefinition::getName)
            .orElse("relatorio");
        String safeName = reportName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return safeName + "." + format.toLowerCase();
    }
}
