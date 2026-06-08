package com.seudominio.jasperrunner.service;

import com.seudominio.jasperrunner.config.JasperRunnerProperties;
import com.seudominio.jasperrunner.dto.ReportParameterInfo;
import com.seudominio.jasperrunner.model.DataSourceConfig;
import com.seudominio.jasperrunner.model.ReportDefinition;
import com.seudominio.jasperrunner.model.ReportFolder;
import com.seudominio.jasperrunner.repository.ReportDefinitionRepository;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.engine.export.HtmlExporter;
import net.sf.jasperreports.engine.export.ooxml.JRDocxExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    /** Cache em memória de relatórios compilados: caminho absoluto → JasperReport */
    private static final Map<String, JasperReport> REPORT_CACHE = new ConcurrentHashMap<>();

    private final ReportDefinitionRepository repository;
    private final DatasourceService datasourceService;
    private final JasperRunnerProperties properties;

    public ReportService(ReportDefinitionRepository repository,
                         DatasourceService datasourceService,
                         JasperRunnerProperties properties) {
        this.repository = repository;
        this.datasourceService = datasourceService;
        this.properties = properties;
    }

    // ─────────────────────────────── CRUD ─────────────────────────────────

    @Transactional(readOnly = true)
    public List<ReportDefinition> findByFolder(Long folderId) {
        Sort sort = Sort.by("name");
        return folderId == null
            ? repository.findByFolderIsNull(sort)
            : repository.findByFolderId(folderId, sort);
    }

    @Transactional(readOnly = true)
    public Optional<ReportDefinition> findById(Long id) {
        return repository.findById(id);
    }

    public ReportDefinition upload(MultipartFile file, String name, String description,
                                   ReportFolder folder) throws IOException {
        String fileName = sanitizeFileName(Objects.requireNonNull(file.getOriginalFilename()));

        Path reportsRoot = resolveReportsRoot();
        Path targetDir = folder != null
            ? reportsRoot.resolve(String.valueOf(folder.getId()))
            : reportsRoot;
        Files.createDirectories(targetDir);

        Path targetPath = targetDir.resolve(fileName);
        file.transferTo(targetPath.toFile());

        String relativePath = reportsRoot.relativize(targetPath).toString();

        ReportDefinition report = new ReportDefinition();
        report.setName(name != null && !name.isBlank() ? name : stripExtension(fileName));
        report.setDescription(description);
        report.setJrxmlPath(relativePath);
        report.setFolder(folder);

        log.info("Relatório '{}' salvo em: {}", report.getName(), relativePath);
        return repository.save(report);
    }

    public ReportDefinition updateMetadata(Long id, String name, String description) {
        ReportDefinition report = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Relatório não encontrado: " + id));
        report.setName(name);
        report.setDescription(description);
        evictCache(report);
        return repository.save(report);
    }

    public ReportDefinition copyTo(Long id, ReportFolder targetFolder) throws IOException {
        ReportDefinition original = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Relatório não encontrado: " + id));

        Path reportsRoot = resolveReportsRoot();
        Path source = reportsRoot.resolve(original.getJrxmlPath());

        Path destDir = targetFolder != null
            ? reportsRoot.resolve(String.valueOf(targetFolder.getId()))
            : reportsRoot;
        Files.createDirectories(destDir);

        String newFileName = "copia_" + source.getFileName();
        Path dest = destDir.resolve(newFileName);
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);

        ReportDefinition copy = new ReportDefinition();
        copy.setName("Cópia de " + original.getName());
        copy.setDescription(original.getDescription());
        copy.setJrxmlPath(reportsRoot.relativize(dest).toString());
        copy.setFolder(targetFolder);
        return repository.save(copy);
    }

    public void moveTo(Long id, ReportFolder targetFolder) {
        ReportDefinition report = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Relatório não encontrado: " + id));
        report.setFolder(targetFolder);
        repository.save(report);
    }

    public void delete(Long id) {
        ReportDefinition report = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Relatório não encontrado: " + id));

        evictCache(report);

        // Remove o arquivo físico
        try {
            Path filePath = resolveReportsRoot().resolve(report.getJrxmlPath());
            Files.deleteIfExists(filePath);
            // Remove .jasper compilado se existir
            String jasperName = filePath.getFileName().toString().replace(".jrxml", ".jasper");
            Files.deleteIfExists(filePath.resolveSibling(jasperName));
        } catch (IOException e) {
            log.warn("Não foi possível excluir arquivo: {}", report.getJrxmlPath(), e);
        }

        repository.deleteById(id);
    }

    // ────────────────────────── PARÂMETROS ────────────────────────────────

    @Transactional(readOnly = true)
    public List<ReportParameterInfo> getParameters(Long reportId) throws JRException {
        ReportDefinition report = repository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Relatório não encontrado: " + reportId));

        JasperReport jasperReport = getCompiledReport(report.getJrxmlPath());
        List<ReportParameterInfo> result = new ArrayList<>();

        for (JRParameter param : jasperReport.getParameters()) {
            if (param.isSystemDefined()) continue;

            ReportParameterInfo info = new ReportParameterInfo();
            info.setName(param.getName());
            info.setTypeName(param.getValueClassName());
            info.setDescription(param.getDescription());
            result.add(info);
        }

        return result;
    }

    // ─────────────────────────── GERAÇÃO ──────────────────────────────────

    /**
     * Compila, preenche e exporta um relatório.
     *
     * @param reportId     ID do relatório no banco de metadados
     * @param rawParams    Parâmetros do formulário (todos como String)
     * @param datasourceId ID da fonte de dados JDBC
     * @param outputFormat PDF, XLSX, HTML, DOCX ou CSV
     * @return bytes do arquivo gerado
     */
    public byte[] generateReport(Long reportId, Map<String, String> rawParams,
                                 Long datasourceId, String outputFormat) throws Exception {
        ReportDefinition reportDef = repository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Relatório não encontrado: " + reportId));

        DataSourceConfig dsConfig = datasourceService.findById(datasourceId)
            .orElseThrow(() -> new IllegalArgumentException("Fonte de dados não encontrada: " + datasourceId));

        JasperReport jasperReport = getCompiledReport(reportDef.getJrxmlPath());
        Map<String, Object> parameters = convertParameters(rawParams, jasperReport);

        // Define SUBREPORT_DIR como o diretório do relatório pai
        String absolutePath = resolveReportsRoot().resolve(reportDef.getJrxmlPath()).toString();
        parameters.put("SUBREPORT_DIR", new File(absolutePath).getParent() + File.separator);

        log.debug("Gerando relatório '{}' no formato '{}' com {} parâmetro(s)",
            reportDef.getName(), outputFormat, parameters.size());

        JasperPrint jasperPrint;
        try (Connection connection = datasourceService.openConnection(dsConfig)) {
            jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, connection);
        }

        return exportReport(jasperPrint, outputFormat);
    }

    // ─────────────────────── MÉTODOS INTERNOS ─────────────────────────────

    private JasperReport getCompiledReport(String jrxmlRelativePath) throws JRException {
        String absolutePath = resolveReportsRoot().resolve(jrxmlRelativePath).toString();

        JasperReport cached = REPORT_CACHE.get(absolutePath);
        if (cached != null) return cached;

        log.debug("Compilando relatório: {}", absolutePath);
        try {
            JasperReport compiled = JasperCompileManager.compileReport(absolutePath);
            REPORT_CACHE.put(absolutePath, compiled);
            return compiled;
        } catch (JRException e) {
            throw new JRException("Erro ao compilar relatório '" + absolutePath + "': " + e.getMessage(), e);
        }
    }

    private byte[] exportReport(JasperPrint jasperPrint, String format) throws JRException {
        return switch (format.toUpperCase()) {
            case "PDF"  -> JasperExportManager.exportReportToPdfBytes(jasperPrint);
            case "XLSX" -> exportXlsx(jasperPrint);
            case "HTML" -> exportHtml(jasperPrint);
            case "DOCX" -> exportDocx(jasperPrint);
            case "CSV"  -> exportCsv(jasperPrint);
            default -> throw new IllegalArgumentException("Formato de saída não suportado: " + format);
        };
    }

    private byte[] exportXlsx(JasperPrint jasperPrint) throws JRException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JRXlsxExporter exporter = new JRXlsxExporter();
        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
        exporter.exportReport();
        return out.toByteArray();
    }

    private byte[] exportHtml(JasperPrint jasperPrint) throws JRException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HtmlExporter exporter = new HtmlExporter();
        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setExporterOutput(new SimpleHtmlExporterOutput(out));
        exporter.exportReport();
        return out.toByteArray();
    }

    private byte[] exportDocx(JasperPrint jasperPrint) throws JRException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JRDocxExporter exporter = new JRDocxExporter();
        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
        exporter.exportReport();
        return out.toByteArray();
    }

    private byte[] exportCsv(JasperPrint jasperPrint) throws JRException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JRCsvExporter exporter = new JRCsvExporter();
        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setExporterOutput(new SimpleWriterExporterOutput(out));
        exporter.exportReport();
        return out.toByteArray();
    }

    private Map<String, Object> convertParameters(Map<String, String> rawParams,
                                                  JasperReport jasperReport) {
        Map<String, Object> result = new HashMap<>();
        if (rawParams == null || rawParams.isEmpty()) return result;

        Map<String, JRParameter> paramMap = new HashMap<>();
        for (JRParameter p : jasperReport.getParameters()) {
            paramMap.put(p.getName(), p);
        }

        for (Map.Entry<String, String> entry : rawParams.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (value == null || value.isBlank()) continue;

            JRParameter param = paramMap.get(name);
            if (param != null && !param.isSystemDefined()) {
                Object converted = convertToType(value, param.getValueClassName());
                if (converted != null) {
                    result.put(name, converted);
                }
            }
        }

        return result;
    }

    private Object convertToType(String value, String typeName) {
        try {
            return switch (typeName) {
                case "java.lang.Integer" -> Integer.valueOf(value);
                case "java.lang.Long"    -> Long.valueOf(value);
                case "java.math.BigDecimal" -> new BigDecimal(value);
                case "java.lang.Double"  -> Double.valueOf(value);
                case "java.lang.Float"   -> Float.valueOf(value);
                case "java.lang.Short"   -> Short.valueOf(value);
                case "java.lang.Boolean" -> Boolean.valueOf(value);
                case "java.util.Date"    -> new SimpleDateFormat("yyyy-MM-dd").parse(value);
                case "java.sql.Date"     -> java.sql.Date.valueOf(value);
                case "java.sql.Timestamp" -> {
                    // Formulário HTML envia "yyyy-MM-ddTHH:mm"
                    String normalized = value.replace("T", " ");
                    if (normalized.length() == 16) normalized += ":00";
                    yield Timestamp.valueOf(normalized);
                }
                default -> value;
            };
        } catch (ParseException e) {
            log.warn("Não foi possível converter '{}' para tipo {}", value, typeName);
            return null;
        }
    }

    private Path resolveReportsRoot() {
        return Path.of(properties.getReportsRootPath()).toAbsolutePath();
    }

    private void evictCache(ReportDefinition report) {
        String absolutePath = resolveReportsRoot().resolve(report.getJrxmlPath()).toString();
        REPORT_CACHE.remove(absolutePath);
    }

    private String sanitizeFileName(String fileName) {
        // Remove path traversal e caracteres inseguros
        return Path.of(fileName).getFileName().toString()
            .replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
