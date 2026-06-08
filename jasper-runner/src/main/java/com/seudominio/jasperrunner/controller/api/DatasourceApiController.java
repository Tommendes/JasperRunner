package com.seudominio.jasperrunner.controller.api;

import com.seudominio.jasperrunner.dto.ConnectionTestResult;
import com.seudominio.jasperrunner.model.DataSourceConfig;
import com.seudominio.jasperrunner.service.DatasourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API REST para gerenciamento de fontes de dados.
 *
 * Endpoints:
 *   GET    /api/datasources
 *   POST   /api/datasources
 *   PUT    /api/datasources/{id}
 *   DELETE /api/datasources/{id}
 *   POST   /api/datasources/{id}/test
 */
@RestController
@RequestMapping("/api/datasources")
public class DatasourceApiController {

    private static final Logger log = LoggerFactory.getLogger(DatasourceApiController.class);

    private final DatasourceService service;

    public DatasourceApiController(DatasourceService service) {
        this.service = service;
    }

    @GetMapping
    public List<DataSourceConfig> listAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DataSourceConfig> getById(@PathVariable Long id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<DataSourceConfig> create(@RequestBody DataSourceConfig config) {
        try {
            // A senha vem em texto puro no campo 'username' para simplificar — use campo dedicado
            String rawPassword = config.getEncryptedPassword();
            config.setEncryptedPassword(null);
            DataSourceConfig saved = service.create(config, rawPassword);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            log.error("Dados inválidos na criação de datasource: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<DataSourceConfig> update(@PathVariable Long id,
                                                   @RequestBody DataSourceConfig config) {
        try {
            String rawPassword = config.getEncryptedPassword();
            config.setEncryptedPassword(null);
            DataSourceConfig updated = service.update(id, config, rawPassword);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.error("Erro ao atualizar datasource {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<ConnectionTestResult> testConnection(@PathVariable Long id) {
        ConnectionTestResult result = service.testConnection(id);
        return ResponseEntity.ok(result);
    }
}
