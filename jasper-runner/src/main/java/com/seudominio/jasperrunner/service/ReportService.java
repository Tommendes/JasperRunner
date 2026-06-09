package com.seudominio.jasperrunner.service;

import com.seudominio.jasperrunner.config.JasperRunnerProperties;
import com.seudominio.jasperrunner.dto.FolderResource;
import com.seudominio.jasperrunner.dto.ReportParameterInfo;
import com.seudominio.jasperrunner.model.DataSourceConfig;
import com.seudominio.jasperrunner.model.ReportDefinition;
import com.seudominio.jasperrunner.model.ReportFolder;
import com.seudominio.jasperrunner.repository.ReportDefinitionRepository;
import com.seudominio.jasperrunner.repository.ReportFolderRepository;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.engine.export.HtmlExporter;
import net.sf.jasperreports.engine.export.ooxml.JRDocxExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.export.*;
import net.sf.jasperreports.repo.FileRepositoryService;
import net.sf.jasperreports.repo.ReportResource;
import net.sf.jasperreports.repo.RepositoryService;
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
    private final ReportFolderRepository folderRepository;
    private final DatasourceService datasourceService;
    private final JasperRunnerProperties properties;

    public ReportService(ReportDefinitionRepository repository,
                         ReportFolderRepository folderRepository,
                         DatasourceService datasourceService,
                         JasperRunnerProperties properties) {
        this.repository = repository;
        this.folderRepository = folderRepository;
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

    /** Arquivos no disco da pasta que não possuem entrada no banco (imagens, fontes, etc.). */
    @Transactional(readOnly = true)
    public List<FolderResource> findResourcesByFolder(ReportFolder folder) throws IOException {
        Path targetDir = folder != null
            ? resolveReportsRoot().resolve(buildFolderPath(folder))
            : resolveReportsRoot();
        if (!Files.isDirectory(targetDir)) return List.of();

        Set<String> registered = findByFolder(folder != null ? folder.getId() : null).stream()
            .map(ReportDefinition::getFileName)
            .map(name -> name.toLowerCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toSet());

        String folderPrefix = folder != null ? buildFolderPath(folder) + "/" : "";

        try (java.util.stream.Stream<Path> files = Files.list(targetDir)) {
            return files
                .filter(Files::isRegularFile)
                .map(Path::getFileName)
                .map(Path::toString)
                .filter(name -> !registered.contains(name.toLowerCase(Locale.ROOT)))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(name -> new FolderResource(name, folderPrefix + name))
                .toList();
        }
    }

    @Transactional(readOnly = true)
    public Optional<ReportDefinition> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<ReportDefinition> findByIdWithFolder(Long id) {
        return repository.findByIdWithFolder(id);
    }

    @Transactional(readOnly = true)
    public Optional<Long> findFolderIdByReportId(Long reportId) {
        return repository.findFolderIdByReportId(reportId);
    }

    public ReportDefinition upload(MultipartFile file, String name, String description,
                                   ReportFolder folder) throws IOException {
        String fileName = sanitizeFileName(Objects.requireNonNull(file.getOriginalFilename()));

        Path reportsRoot = resolveReportsRoot();
        Path targetDir = folder != null
            ? reportsRoot.resolve(buildFolderPath(folder))
            : reportsRoot;
        Files.createDirectories(targetDir);

        Path targetPath = targetDir.resolve(fileName);
        file.transferTo(targetPath.toFile());

        String relativePath = reportsRoot.relativize(targetPath).toString();

        // Apenas .jrxml e .jasper geram entrada no banco; outros arquivos são recursos
        boolean isJrxml   = fileName.toLowerCase().endsWith(".jrxml");
        boolean isJasper  = fileName.toLowerCase().endsWith(".jasper");
        if (!isJrxml && !isJasper) {
            log.info("Recurso '{}' salvo em: {}", fileName, relativePath);
            return null; // recurso de suporte (imagem, fonte) — sem entrada no BD
        }

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
            ? reportsRoot.resolve(buildFolderPath(targetFolder))
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
            info.setDefaultValueAsString(extractDefaultValue(param));
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

        // Define SUBREPORT_DIR e FileRepositoryService para resolução de caminhos relativos
        String absolutePath = resolveReportsRoot().resolve(reportDef.getJrxmlPath()).toString();
        final File reportParentDir = new File(absolutePath).getParentFile();
        parameters.put("SUBREPORT_DIR", reportParentDir.getAbsolutePath() + File.separator);

        // Contexto JR que herda todos os serviços do DefaultJasperReportsContext
        // (PersistenceService, exporters, etc.) mas sobrescreve apenas o RepositoryService
        // com nossa implementação customizada que:
        //   1. Resolve caminhos relativos a partir do diretório do relatório pai
        //   2. Permite path traversal (ex: "../templates/header01.jasper")
        //   3. Fallback automático de .jasper → .jrxml quando arquivo não existir
        SimpleJasperReportsContext jrContext = new SimpleJasperReportsContext(
            DefaultJasperReportsContext.getInstance()
        );
        final Path reportDirPath = reportParentDir.toPath().toAbsolutePath().normalize();
        jrContext.setExtensions(RepositoryService.class,
            Collections.singletonList(
                new FileRepositoryService(jrContext, reportDirPath.toString(), true) {
                    @Override
                    public <K extends net.sf.jasperreports.repo.Resource> K getResource(
                        net.sf.jasperreports.repo.RepositoryContext context,
                        String location,
                        Class<K> resourceType
                    ) {
                        K res = super.getResource(context, location, resourceType);
                        if (res != null) return res;

                        // Fallback explícito para subrelatórios quando o serviço padrão
                        // não conseguir materializar ReportResource.
                        if (ReportResource.class.isAssignableFrom(resourceType)) {
                            File f = locateFile(location);
                            if (f != null && f.isFile()) {
                                try {
                                    JasperReport report;
                                    if (f.getName().toLowerCase().endsWith(".jasper")) {
                                        report = (JasperReport) JRLoader.loadObject(f);
                                    } else if (f.getName().toLowerCase().endsWith(".jrxml")) {
                                        report = JasperCompileManager.compileReport(f.getAbsolutePath());
                                    } else {
                                        return null;
                                    }

                                    ReportResource rr = new ReportResource();
                                    rr.setReport(report);
                                    log.debug("Fallback getResource carregou subrelatório: '{}'", f.getAbsolutePath());
                                    return resourceType.cast(rr);
                                } catch (Exception e) {
                                    log.warn("Fallback getResource falhou para '{}': {}", location, e.getMessage());
                                }
                            }
                        }
                        return null;
                    }

                    @Override
                    protected File locateFile(String location) {
                        try {
                            // Resolve relativo ao diretório do relatório pai
                            Path resolved = reportDirPath.resolve(location).normalize();
                            log.debug("locateFile: '{}' → '{}'", location, resolved);
                            File f = resolved.toFile();
                            if (f.exists() && f.isFile()) return f;
                            // Fallback: .jasper referenciado mas só existe .jrxml
                            if (location.endsWith(".jasper")) {
                                String jrxmlName = location.substring(0, location.length() - 7) + ".jrxml";
                                Path alt = reportDirPath.resolve(jrxmlName).normalize();
                                File jrxmlFile = alt.toFile();
                                if (jrxmlFile.exists() && jrxmlFile.isFile()) {
                                    log.debug("Fallback .jasper→.jrxml: '{}'", alt);
                                    return jrxmlFile;
                                }
                            }
                            return null;
                        } catch (Exception e) {
                            log.warn("locateFile error para '{}': {}", location, e.getMessage());
                            return null;
                        }
                    }
                }
            ));

        log.debug("Gerando relatório '{}' no formato '{}' com {} parâmetro(s)",
            reportDef.getName(), outputFormat, parameters.size());

        JasperPrint jasperPrint;
        try (Connection connection = datasourceService.openConnection(dsConfig)) {
            jasperPrint = JasperFillManager.getInstance(jrContext)
                .fill(jasperReport, parameters, connection);
        }

        return exportReport(jasperPrint, outputFormat);
    }

    // ─────────────────────── MÉTODOS INTERNOS ─────────────────────────────

    private JasperReport getCompiledReport(String jrxmlRelativePath) throws JRException {
        String absolutePath = resolveReportsRoot().resolve(jrxmlRelativePath).toString();

        JasperReport cached = REPORT_CACHE.get(absolutePath);
        if (cached != null) return cached;

        JasperReport compiled;
        if (absolutePath.toLowerCase().endsWith(".jasper")) {
            // Arquivo pré-compilado: carregar diretamente
            log.debug("Carregando .jasper pré-compilado: {}", absolutePath);
            try {
                compiled = (JasperReport) JRLoader.loadObjectFromFile(absolutePath);
            } catch (Exception e) {
                throw new JRException("Erro ao carregar .jasper '" + absolutePath + "': " + e.getMessage(), e);
            }
        } else {
            log.debug("Compilando relatório: {}", absolutePath);
            try {
                compiled = JasperCompileManager.compileReport(absolutePath);
            } catch (JRException e) {
                throw new JRException("Erro ao compilar relatório '" + absolutePath + "': " + e.getMessage(), e);
            }
        }

        REPORT_CACHE.put(absolutePath, compiled);
        return compiled;
    }

    private byte[] exportReport(JasperPrint jasperPrint, String format) throws JRException {
        return switch (format.toUpperCase()) {
            case "PDF"  -> exportPdf(jasperPrint);
            case "XLSX" -> exportXlsx(jasperPrint);
            case "HTML" -> exportHtml(jasperPrint);
            case "DOCX" -> exportDocx(jasperPrint);
            case "CSV"  -> exportCsv(jasperPrint);
            default -> throw new IllegalArgumentException("Formato de saída não suportado: " + format);
        };
    }

    private byte[] exportPdf(JasperPrint jasperPrint) throws JRException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JRPdfExporter exporter = new JRPdfExporter();
        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
        exporter.exportReport();
        return out.toByteArray();
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

    /**
     * Constrói o caminho relativo da pasta a partir dos nomes (não IDs),
     * preservando hierarquia: "pai/filho/neto".
     * Isso permite que caminhos relativos nos .jrxml (ex: "../templates/sub.jasper")
     * funcionem corretamente no sistema de arquivos.
     */
    /**
     * Extrai o valor default de um parâmetro JR a partir da expressão declarada no JRXML.
     * Trata literais comuns sem precisar avaliar o contexto completo do relatório.
     * Retorna null quando não há expressão ou quando não é possível simplificar.
     */
    private String extractDefaultValue(JRParameter param) {
        if (param.getDefaultValueExpression() == null) return null;
        String expr = param.getDefaultValueExpression().getText();
        if (expr == null || expr.isBlank()) return null;

        expr = expr.trim();
        String type = param.getValueClassName();

        // String literal: "valor" ou 'valor'
        if (expr.startsWith("\"") && expr.endsWith("\"") && expr.length() >= 2) {
            return expr.substring(1, expr.length() - 1);
        }
        if (expr.startsWith("'") && expr.endsWith("'") && expr.length() >= 2) {
            return expr.substring(1, expr.length() - 1);
        }

        // Boolean
        if ("java.lang.Boolean".equals(type)) {
            if (expr.equalsIgnoreCase("Boolean.TRUE") || expr.equalsIgnoreCase("true")) return "true";
            if (expr.equalsIgnoreCase("Boolean.FALSE") || expr.equalsIgnoreCase("false")) return "false";
        }

        // Números: Integer.valueOf(10), Long.valueOf(5L), new BigDecimal("3.14"), ou literal simples
        if (type != null && (type.contains("Integer") || type.contains("Long")
                || type.contains("Double") || type.contains("Float")
                || type.contains("Short") || type.contains("BigDecimal"))) {
            // Extrai o primeiro grupo de dígitos (com ponto decimal e sinal opcional)
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[-+]?[0-9]*\\.?[0-9]+")
                .matcher(expr);
            if (m.find()) return m.group();
        }

        // Data: new Date() → data de hoje; formatos simples como "2024-01-01"
        if (type != null && (type.contains("Date") || type.contains("Timestamp"))) {
            if (expr.contains("new Date()") || expr.contains("new java.util.Date()")) {
                java.time.LocalDate today = java.time.LocalDate.now();
                return today.toString(); // yyyy-MM-dd
            }
            // "yyyy-MM-dd" literal dentro de string
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\d{4}-\\d{2}-\\d{2}")
                .matcher(expr);
            if (m.find()) return m.group();
        }

        // Para qualquer outra expressão simples (ex: constante), devolve a expressão como está
        // somente se for curta o suficiente para não poluir o campo
        if (!expr.contains("(") && expr.length() <= 50) {
            return expr;
        }

        return null;
    }

    private String buildFolderPath(ReportFolder folder) {
        return folder == null ? "" : buildFolderPath(folder.getId());
    }

    /** Monta o caminho no disco sem acessar proxies lazy de parent (open-in-view=false). */
    private String buildFolderPath(Long folderId) {
        Deque<String> parts = new ArrayDeque<>();
        Long currentId = folderId;
        while (currentId != null) {
            Long id = currentId;
            ReportFolder current = folderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pasta não encontrada: " + id));
            parts.addFirst(current.getName().replaceAll("[^a-zA-Z0-9._\\-]", "_"));
            currentId = folderRepository.findParentIdById(currentId).orElse(null);
        }
        return String.join("/", parts);
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
