Crie uma aplicação web Java completa para executar relatórios JasperReports (.jrxml),
substituindo o JasperServer. A aplicação deve ser um substituto funcional para uso
em servidor VPS Ubuntu.
Atenção! Já há um JasperSever rodando no servidor onde isso será implantado, então o nome do projeto deve ser diferente (ex: "JasperRunner") e a porta padrão deve ser 8090 para evitar conflitos.
Atenção 2! O foco é em funcionalidade e arquitetura, não em design. A UI pode ser simples, mas deve ser funcional e responsiva (Bootstrap 5).
Atenção 3! O código deve ser bem estruturado, seguindo boas práticas de organização em camadas (controller, service, repository) e usando Spring Boot para acelerar o desenvolvimento.
Atenção 4! Implementar suporte a logs, controle de usuário e persistência em BD Mysql (configurável via application.yml) para armazenar metadados dos relatórios e fontes de dados.

## Stack obrigatória
- Java 17+
- Spring Boot 3.x
- JasperReports 6.21.x (net.sf.jasperreports:jasperreports)
- Thymeleaf para templates HTML (UI server-side)
- Spring Data JPA + H2 (banco interno para metadados da app)
- Maven como build tool

## Estrutura de pastas do projeto
jasper-runner/
├── src/main/java/com/seudominio/jasperrunner/
│   ├── JasperRunnerApplication.java
│   ├── controller/
│   │   ├── ReportController.java
│   │   ├── DatasourceController.java
│   │   └── FolderController.java
│   ├── service/
│   │   ├── ReportService.java
│   │   ├── DatasourceService.java
│   │   └── FolderService.java
│   ├── model/
│   │   ├── ReportDefinition.java
│   │   ├── DataSourceConfig.java
│   │   └── ReportFolder.java
│   └── repository/
│       ├── ReportDefinitionRepository.java
│       ├── DataSourceConfigRepository.java
│       └── ReportFolderRepository.java
├── src/main/resources/
│   ├── templates/          ← Thymeleaf HTML
│   ├── reports/            ← pasta raiz dos .jrxml
│   └── application.yml
└── pom.xml

## Funcionalidades obrigatórias

### 1. Repositório de Relatórios
- Tela principal igual ao JasperServer: painel esquerdo com árvore de pastas,
  painel direito com lista de relatórios
- CRUD de pastas (criar, renomear, mover, excluir)
- Upload de arquivos .jrxml e recursos relacionados (.jasper, imagens, subrelatórios)
- Ações na toolbar: Executar, Editar (metadados), Copiar, Cortar, Colar, Excluir

### 2. Execução de Relatórios
- Ao clicar em "Executar", detectar automaticamente todos os parâmetros declarados
  no .jrxml via JRParameter
- Renderizar formulário dinâmico com campo para cada parâmetro:
  - Tipo String → input text
  - Tipo Date / Timestamp → date picker
  - Tipo Integer / Long / BigDecimal → input number
  - Tipo Boolean → checkbox
  - Parâmetros com valueDescription → tooltip de ajuda
- Seletor de fonte de dados para o relatório
- Seletor de formato de saída: PDF, XLSX, HTML, DOCX, CSV
- Botão "Executar" que baixa o arquivo gerado

### 3. Gerenciamento de Fontes de Dados
- CRUD completo de Data Sources do tipo JDBC
- Campos: nome, driver JDBC, URL, usuário, senha
- Botão "Testar Conexão" que valida antes de salvar
- Drivers a suportar de início: PostgreSQL, MySQL, Microsoft SQL Server, H2
- Senha armazenada com criptografia (Jasypt ou AES simples)

### 4. Motor de execução (ReportService)
Implementar o método:
```java
public byte[] generateReport(
    String jrxmlPath,
    Map<String, Object> parameters,
    DataSourceConfig dsConfig,
    String outputFormat   // "PDF","XLSX","HTML","DOCX","CSV"
) throws JRException
```
Passos internos:
1. JasperCompileManager.compileReport(jrxmlPath) — cachear o .jasper compilado
2. Abrir Connection JDBC com os dados do DataSourceConfig
3. JasperFillManager.fillReport(jasperReport, parameters, connection)
4. Exportar com o exporter correto:
   - PDF → JRPdfExporter
   - XLSX → JRXlsxExporter  
   - HTML → HtmlExporter
   - DOCX → JRDocxExporter
   - CSV → JRCsvExporter
5. Retornar byte[]

### 5. UI/UX
- Layout idêntico ao JasperServer Community:
  - Barra superior azul escuro com logo e menus (Biblioteca, Visualização, Gerenciar)
  - Painel lateral esquerdo com árvore de pastas colapsável
  - Painel principal à direita com tabela de relatórios
  - Toolbar com botões de ação (Executar, Editar, Copiar, Cortar, Colar, Excluir)
- Modal para parâmetros do relatório ao executar
- Feedback visual de loading durante geração
- Mensagens de erro claras quando jrxml tem erros de compilação
- Responsivo (Bootstrap 5)

### 6. API REST (para integrações futuras)
Expor também endpoints REST paralelos à UI:
POST /api/reports/{reportId}/run
Body: { "parameters": {}, "datasourceId": 1, "format": "PDF" }
Response: arquivo binário
GET  /api/reports
GET  /api/reports/{id}
POST /api/reports (upload jrxml)
DELETE /api/reports/{id}
GET  /api/datasources
POST /api/datasources
PUT  /api/datasources/{id}
DELETE /api/datasources/{id}
POST /api/datasources/{id}/test

### 7. Segurança básica
- Spring Security com autenticação simples usuário/senha
- Configurável via application.yml
- Uma role de admin por enquanto

### 8. Deploy no Ubuntu VPS
- Gerar um fat JAR executável via `mvn package`
- Criar arquivo `jasper-runner.service` para systemd
- Porta padrão: 8090 (configurável)
- Criar script `deploy.sh` que:
  1. Para o serviço se rodando
  2. Copia o novo JAR
  3. Reinicia o serviço

## application.yml base
```yaml
server:
  port: 8090

spring:
  datasource:
    url: jdbc:h2:file:./data/jasperrunner
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: update

jasperrunner:
  reports-root-path: ./reports
  admin-user: admin
  admin-password: changeme
```

## pom.xml — dependências principais a incluir
- spring-boot-starter-web
- spring-boot-starter-thymeleaf
- spring-boot-starter-data-jpa
- spring-boot-starter-security
- net.sf.jasperreports:jasperreports:6.21.0
- net.sf.jasperreports:jasperreports-pdf:6.21.0
- net.sf.jasperreports:jasperreports-excel:6.21.0
- com.h2database:h2
- org.postgresql:postgresql
- com.mysql:mysql-connector-j
- com.microsoft.sqlserver:mssql-jdbc
- org.webjars:bootstrap:5.3.0

## Observações importantes
- O .jrxml compilado (.jasper) deve ser cacheado em memória (Map<String, JasperReport>)
  para evitar recompilação a cada execução
- Tratar JRException com mensagem de erro amigável na UI
- Subrelatórios: o SUBREPORT_DIR deve apontar para a pasta do relatório pai
- Fontes: incluir dependência net.sf.jasperreports:jasperreports-fonts se necessário
- Gerar README.md com instruções de instalação no Ubuntu