package com.seudominio.jasperrunner.controller.api;

import com.seudominio.jasperrunner.dto.ReportParameterInfo;
import com.seudominio.jasperrunner.model.ReportDefinition;
import com.seudominio.jasperrunner.model.ReportFolder;
import com.seudominio.jasperrunner.service.DatasourceService;
import com.seudominio.jasperrunner.service.FolderService;
import com.seudominio.jasperrunner.service.ReportService;
import net.sf.jasperreports.engine.JRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * API REST para integração externa com o JasperRunner.
 *
 * Endpoints:
 *   GET    /api/reports
 *   GET    /api/reports/{id}
 *   POST   /api/reports          (upload multipart)
 *   DELETE /api/reports/{id}
 *   POST   /api/reports/{id}/run
 */
@RestController
@RequestMapping("/api/reports")
public class ReportApiController {

    private static final Logger log = LoggerFactory.getLogger(ReportApiController.class);

    private final ReportService reportService;
    private final FolderService folderService;
    private final DatasourceService datasourceService;

    public ReportApiController(ReportService reportService, FolderService folderService,
                               DatasourceService datasourceService) {
        this.reportService = reportService;
        this.folderService = folderService;
        this.datasourceService = datasourceService;
    }

    @GetMapping
    public List<ReportDefinition> listAll() {
        return reportService.findByFolder(null);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReportDefinition> getById(@PathVariable Long id) {
        return reportService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/params")
    public ResponseEntity<List<ReportParameterInfo>> getParams(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(reportService.getParameters(id));
        } catch (JRException e) {
            log.error("Erro ao ler parâmetros do relatório {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReportDefinition> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "folderId", required = false) Long folderId) {
        try {
            ReportFolder folder = folderId != null ? folderService.findById(folderId).orElse(null) : null;
            ReportDefinition report = reportService.upload(file, name, description, folder);
            return ResponseEntity.ok(report);
        } catch (IOException e) {
            log.error("Erro no upload via API", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        try {
            reportService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Executa um relatório e retorna o arquivo binário.
     *
     * Body JSON: { "parameters": {"param1": "valor"}, "datasourceId": 1, "format": "PDF" }
     */
    @PostMapping("/{id}/run")
    public ResponseEntity<byte[]> run(@PathVariable Long id,
                                      @RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> params = (Map<String, String>) body.getOrDefault("parameters", Map.of());
            Long datasourceId = ((Number) body.get("datasourceId")).longValue();
            String format = (String) body.getOrDefault("format", "PDF");

            byte[] bytes = reportService.generateReport(id, params, datasourceId, format);

            String contentType = resolveContentType(format);
            String fileName = "report_" + id + "." + format.toLowerCase();

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(bytes);
        } catch (IllegalArgumentException e) {
            log.error("Requisição inválida para relatório {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Erro ao executar relatório {} via API", id, e);
            return ResponseEntity.internalServerError().build();
        }
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
}
