package com.seudominio.jasperrunner.service;

import com.seudominio.jasperrunner.config.JasperRunnerProperties;
import com.seudominio.jasperrunner.dto.ConnectionTestResult;
import com.seudominio.jasperrunner.model.DataSourceConfig;
import com.seudominio.jasperrunner.repository.DataSourceConfigRepository;
import com.seudominio.jasperrunner.util.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class DatasourceService {

    private static final Logger log = LoggerFactory.getLogger(DatasourceService.class);

    private final DataSourceConfigRepository repository;
    private final EncryptionUtil encryptionUtil;

    public DatasourceService(DataSourceConfigRepository repository, JasperRunnerProperties properties) {
        this.repository = repository;
        this.encryptionUtil = new EncryptionUtil(properties.getEncryptionKey());
    }

    @Transactional(readOnly = true)
    public List<DataSourceConfig> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<DataSourceConfig> findById(Long id) {
        return repository.findById(id);
    }

    public DataSourceConfig create(DataSourceConfig config, String rawPassword) {
        if (repository.existsByName(config.getName())) {
            throw new IllegalArgumentException("Já existe uma fonte de dados com o nome: " + config.getName());
        }
        if (rawPassword != null && !rawPassword.isBlank()) {
            config.setEncryptedPassword(encryptionUtil.encrypt(rawPassword));
        }
        return repository.save(config);
    }

    public DataSourceConfig update(Long id, DataSourceConfig updated, String rawPassword) {
        DataSourceConfig existing = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Fonte de dados não encontrada: " + id));

        if (repository.existsByNameAndIdNot(updated.getName(), id)) {
            throw new IllegalArgumentException("Já existe outra fonte de dados com o nome: " + updated.getName());
        }

        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setDriverClassName(updated.getDriverClassName());
        existing.setUrl(updated.getUrl());
        existing.setUsername(updated.getUsername());

        if (rawPassword != null && !rawPassword.isBlank()) {
            existing.setEncryptedPassword(encryptionUtil.encrypt(rawPassword));
        }

        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public ConnectionTestResult testConnection(Long id) {
        return repository.findById(id)
            .map(this::testConnectionDirect)
            .orElse(ConnectionTestResult.fail("Fonte de dados não encontrada"));
    }

    public ConnectionTestResult testConnectionDirect(DataSourceConfig config) {
        try {
            Class.forName(config.getDriverClassName());

            String password = decryptPassword(config);
            try (Connection conn = DriverManager.getConnection(config.getUrl(), config.getUsername(), password)) {
                String info = conn.getMetaData().getDatabaseProductName()
                    + " " + conn.getMetaData().getDatabaseProductVersion();
                log.info("Conexão testada com sucesso para '{}': {}", config.getName(), info);
                return ConnectionTestResult.ok("Conexão OK! Banco: " + info);
            }
        } catch (ClassNotFoundException e) {
            log.error("Driver JDBC não encontrado: {}", config.getDriverClassName());
            return ConnectionTestResult.fail("Driver JDBC não encontrado: " + config.getDriverClassName());
        } catch (Exception e) {
            log.error("Falha na conexão com '{}': {}", config.getUrl(), e.getMessage());
            return ConnectionTestResult.fail("Falha na conexão: " + e.getMessage());
        }
    }

    /**
     * Abre uma conexão JDBC para uso na geração de relatórios.
     * Responsabilidade do chamador fechar a conexão.
     */
    public Connection openConnection(DataSourceConfig config) throws Exception {
        Class.forName(config.getDriverClassName());
        String password = decryptPassword(config);
        return DriverManager.getConnection(config.getUrl(), config.getUsername(), password);
    }

    private String decryptPassword(DataSourceConfig config) {
        if (config.getEncryptedPassword() == null || config.getEncryptedPassword().isBlank()) {
            return null;
        }
        return encryptionUtil.decrypt(config.getEncryptedPassword());
    }
}
