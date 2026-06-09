# JasperRunner

Substituto funcional do JasperServer Community Edition para execução de relatórios JasperReports (`.jrxml`) em servidor VPS Ubuntu ou container Docker.

> **Porta padrão:** `8090` — não conflita com um JasperServer já em execução no mesmo host.

O código-fonte da aplicação fica em `jasper-runner/`. Este repositório inclui build Maven, deploy via systemd, **Dockerfile pronto para Easypanel** e **collection Postman** para testar a API REST.

---

## Objetivo
O objetivo deste projeto é criar uma aplicação que permita a execução de relatórios JasperReports (`.jrxml`) em um servidor VPS Ubuntu ou container Docker.

## Tecnologias

- Java
- Spring Boot
- JasperReports
- Thymeleaf
- MySQL/MariaDB
- Maven
- Docker
- Easypanel (opcional)
- Postman (opcional)

## Funcionalidades

### Repositório de relatórios
- Tela principal no estilo JasperServer: árvore de pastas à esquerda, lista de relatórios à direita
- CRUD de pastas (criar, renomear, mover, excluir)
- Upload de `.jrxml` e recursos relacionados (`.jasper`, imagens, subrelatórios)
- Toolbar: Executar, Editar metadados, Copiar, Cortar, Colar, Excluir

### Execução de relatórios
- Detecção automática de parâmetros declarados no `.jrxml` via `JRParameter`
- Formulário dinâmico por tipo: String, Date/Timestamp, Integer/Long/BigDecimal, Boolean
- Parâmetros com `valueDescription` exibem tooltip de ajuda
- Seletor de fonte de dados JDBC e formato de saída: **PDF, XLSX, HTML, DOCX, CSV**
- Feedback visual de loading e mensagens claras em erros de compilação

### Fontes de dados JDBC
- CRUD completo com teste de conexão antes de salvar
- Drivers: PostgreSQL, MySQL/MariaDB, Microsoft SQL Server, H2
- Senhas JDBC criptografadas com AES-128 antes de persistir

### Usuários e segurança
- **Spring Security** com duas cadeias: API (`/api/**`, HTTP Basic stateless) e interface web (login por sessão)
- Usuários persistidos em **MySQL** com perfis `ADMIN` e `USER`
- Gestão de usuários (admin): `/users`
- Perfil do usuário: `/profile` (nome, e-mail, troca de senha)
- Recuperação de senha por e-mail com link temporário

### API REST e integrações
- Endpoints REST paralelos à UI para automação e integrações externas
- Collection Postman incluída (ver seção [Postman](#postman))

---

## Principais telas

### Biblioteca de relatórios

Repositório com árvore de pastas, lista de arquivos `.jasper`/`.jrxml`, toolbar de ações (executar, copiar, colar, upload) e navegação por breadcrumb.

![Biblioteca de relatórios](jasper-runner/src/main/resources/static/main-dash.png)

### Fontes de dados JDBC

Cadastro e manutenção das conexões JDBC usadas na execução dos relatórios, com teste de conexão, edição e exclusão.

![Fontes de dados JDBC](jasper-runner/src/main/resources/static/datasources-dash.png)

### Usuários

Gestão de contas da aplicação: criação, edição, perfis (`ADMIN` / `USER`) e envio de e-mail de boas-vindas.

![Gestão de usuários](jasper-runner/src/main/resources/static/users-dash.png)

---

## Stack

| Tecnologia        | Versão  |
|-------------------|---------|
| Java              | 17+     |
| Spring Boot       | 3.2.5   |
| JasperReports     | 6.21.0  |
| Thymeleaf         | 3.x     |
| Spring Data JPA   | 3.x     |
| MySQL / MariaDB   | runtime (metadados da app) |
| Maven             | 3.8+    |

---

## Estrutura do repositório

```
jasper-runner/
├── src/main/java/com/seudominio/jasperrunner/
│   ├── JasperRunnerApplication.java
│   ├── config/          ← SecurityConfig, JasperRunnerProperties, AdminUserSeeder
│   ├── controller/      ← ReportController, UserController, ProfileController, ...
│   │   └── api/         ← ReportApiController, DatasourceApiController
│   ├── dto/             ← Formulários e DTOs de API
│   ├── model/           ← User, ReportFolder, ReportDefinition, DataSourceConfig, ...
│   ├── repository/      ← Interfaces JPA
│   ├── security/        ← UserPrincipal, DatabaseUserDetailsService
│   ├── service/         ← ReportService, UserService, EmailService, ...
│   └── util/            ← EncryptionUtil, PasswordPolicy
├── src/main/resources/
│   ├── templates/       ← Thymeleaf (login, index, profile, users, ...)
│   ├── static/css/      ← app.css (tema JasperServer)
│   ├── application.yml
│   └── jasperreports.properties
├── postman/
│   └── JasperRunner.postman_collection.json
├── Dockerfile            ← build multi-stage; pronto para Easypanel
├── docker-entrypoint.sh
├── EASYPANEL-INSTRUCTIONS.md
├── jasper-runner.service ← systemd
├── deploy.sh
├── .env.example
└── pom.xml
```

---

## Motor de execução (`ReportService`)

Fluxo interno ao gerar um relatório:

1. `JasperCompileManager.compileReport(jrxmlPath)` — compilado cacheado em memória (`ConcurrentHashMap`)
2. Abrir `Connection` JDBC com os dados do `DataSourceConfig`
3. `JasperFillManager.fillReport(jasperReport, parameters, connection)`
4. Exportar com o exporter correspondente:
   - PDF → `JRPdfExporter`
   - XLSX → `JRXlsxExporter`
   - HTML → `HtmlExporter`
   - DOCX → `JRDocxExporter`
   - CSV → `JRCsvExporter`
5. Retornar `byte[]` para download

`SUBREPORT_DIR` é definido automaticamente para o diretório do relatório pai.

---

## Compilar

```bash
cd jasper-runner
mvn clean package -DskipTests
```

O fat JAR é gerado em `jasper-runner/target/jasper-runner-1.0.0.jar`.

---

## Executar localmente

Configure o MySQL e inicie:

```bash
export DB_HOST=localhost
export DB_PORT=3306
export DB_DATABASE=jasper_runner
export DB_USER=root
export DB_PASSWORD=sua_senha

cd jasper-runner
java -jar target/jasper-runner-1.0.0.jar
```

Acesse: [http://localhost:8090](http://localhost:8090)

Credenciais padrão (criadas no primeiro start, se não existir admin):

| Campo    | Valor       |
|----------|-------------|
| Usuário  | `admin`     |
| Senha    | `changeme`  |

> Altere obrigatoriamente as credenciais em produção.

As mesmas credenciais servem para login na interface web e para HTTP Basic na API.

Copie o arquivo de exemplo para desenvolvimento local (não commite o `.env`):

```bash
cp jasper-runner/.env.example jasper-runner/.env
```

---

## Deploy com Docker (Easypanel)

O repositório inclui `jasper-runner/Dockerfile` (build multi-stage: Maven + `eclipse-temurin:17-jre`). Java **não** precisa estar instalado no host — só Docker.

| Item | Valor |
|------|-------|
| Caminho de build (Easypanel) | `/jasper-runner` |
| Tipo de build | Dockerfile |
| Porta interna | `8090` |
| Health check | `GET /health` |

### Variáveis obrigatórias no container

| Variável | Descrição |
|----------|-----------|
| `DB_HOST` | Host do MySQL/MariaDB |
| `DB_PORT` | `3306` |
| `DB_DATABASE` | ex: `jasper_runner` |
| `DB_USER` / `DB_PASSWORD` | credenciais do banco |
| `JASPERRUNNER_ADMIN_PASSWORD` | senha do admin (padrão: `changeme`) |
| `JASPERRUNNER_ENCRYPTION_KEY` | chave AES para senhas JDBC (mín. 16 caracteres) |

### Variáveis recomendadas

| Variável | Descrição |
|----------|-----------|
| `APP_BASE_URL` | URL pública (ex: `https://relatorios.seudominio.com`) |
| `JASPERRUNNER_REPORTS_ROOT_PATH` | `/app/reports` (padrão no Dockerfile) |
| `LOGGING_FILE_NAME` | `/app/logs/jasper-runner.log` |
| `JPA_DATABASE_PLATFORM` | `org.hibernate.dialect.MariaDBDialect` (já definido no Dockerfile; útil com MariaDB) |

### Volumes persistentes

| Volume | Caminho no container |
|--------|---------------------|
| relatórios | `/app/reports` |
| logs | `/app/logs` |

### Build manual (sem Easypanel)

```bash
cd jasper-runner
docker build -t jasper-runner .
docker run -p 8090:8090 \
  -e DB_HOST=host.docker.internal \
  -e DB_DATABASE=jasper_runner \
  -e DB_USER=root \
  -e DB_PASSWORD=senha \
  -e JASPERRUNNER_ADMIN_PASSWORD=senha_forte \
  -e JASPERRUNNER_ENCRYPTION_KEY=MinhaChaveSecreta32Chars!! \
  -v jasperrunner-reports:/app/reports \
  -v jasperrunner-logs:/app/logs \
  jasper-runner
```

Guia passo a passo com checklist e troubleshooting: [`jasper-runner/EASYPANEL-INSTRUCTIONS.md`](jasper-runner/EASYPANEL-INSTRUCTIONS.md).

---

## Configuração MySQL

Os metadados da aplicação (usuários, pastas, relatórios, datasources) ficam em **MySQL/MariaDB** — separado dos bancos usados pelos relatórios.

Crie o banco antes do primeiro start:

```sql
CREATE DATABASE jasper_runner CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

O Hibernate cria/atualiza as tabelas automaticamente (`ddl-auto: update`).

Variáveis de ambiente (valores padrão entre parênteses):

| Variável | Padrão |
|----------|--------|
| `DB_HOST` | `localhost` |
| `DB_PORT` | `3306` |
| `DB_DATABASE` | `jasper_runner` |
| `DB_USER` | `root` |
| `DB_PASSWORD` | (vazio) |
| `JPA_DATABASE_PLATFORM` | `org.hibernate.dialect.MariaDBDialect` |

### E-mail (recuperação de senha)

```bash
MAILER_HOST=smtp.hostinger.com
MAILER_PORT=465
MAILER_SECURE=true
MAILER_USER=seu@email.com
MAILER_PASS=sua_senha_smtp
```

Os links nos e-mails usam o domínio da requisição HTTP (headers `X-Forwarded-*` em proxy reverso). Fallback opcional: `APP_BASE_URL=https://seu-dominio.com`.

---

## Usuários e recuperação de senha

Usuários ficam na tabela `app_users` com senha em BCrypt. No primeiro start, se não existir o username de `jasperrunner.admin-user`, um administrador é criado com os valores de `application.yml`.

**Perfil** (`/profile`): editar nome/e-mail e alterar senha (mínimo 8 caracteres).

**Recuperação de senha:**
1. Login → **Esqueceu sua senha?**
2. Informe o username (resposta genérica, não revela se existe)
3. Link enviado para `/password/reset?token=...` (válido 30 min por padrão)
4. Nova senha invalida o token

Requisitos: `MAILER_USER`, `MAILER_PASS` e URL acessível (`APP_BASE_URL` ou domínio da requisição).

---

## Configuração (`application.yml`)

```yaml
server:
  port: 8090

jasperrunner:
  reports-root-path: ./reports
  admin-user: admin
  admin-password: SUA_SENHA_FORTE
  admin-name: Administrator
  admin-email: admin@seudominio.com
  base-url: http://localhost:8090    # ou APP_BASE_URL em produção
  password-reset-expiration-minutes: 30
  encryption-key: SUA_CHAVE_AES_32   # senhas JDBC
```

Em Docker/Easypanel, prefira variáveis de ambiente (`JASPERRUNNER_*`, `DB_*`, `APP_BASE_URL`).

---

## Deploy no Ubuntu VPS (systemd)

### Pré-requisitos

```bash
sudo apt update
sudo apt install openjdk-17-jre-headless
```

### Deploy automatizado

```bash
cd jasper-runner
chmod +x deploy.sh
./deploy.sh
```

O script para o serviço, faz backup do JAR anterior, copia para `/opt/jasperrunner/`, instala/atualiza o systemd e reinicia.

### Deploy manual

```bash
sudo useradd -r -s /bin/false jasperrunner
sudo mkdir -p /opt/jasperrunner/{data,reports,logs}
sudo chown -R jasperrunner:jasperrunner /opt/jasperrunner

sudo cp jasper-runner/target/jasper-runner-1.0.0.jar /opt/jasperrunner/jasper-runner.jar
sudo cp jasper-runner/src/main/resources/application.yml /opt/jasperrunner/
sudo cp jasper-runner/jasper-runner.service /etc/systemd/system/

sudo systemctl daemon-reload
sudo systemctl enable jasper-runner
sudo systemctl start jasper-runner
```

### Gerenciar o serviço

```bash
sudo systemctl status jasper-runner
sudo systemctl restart jasper-runner
sudo journalctl -u jasper-runner -f
```

---

## API REST

Endpoints em `/api/**` exigem **HTTP Basic** (stateless). Sem credenciais válidas: `401 Unauthorized`.

A interface web usa login por formulário com sessão — fluxos separados.

### Saúde

| Método | Endpoint   | Descrição        |
|--------|------------|------------------|
| GET    | `/health`  | Health check     |

### Relatórios

| Método | Endpoint                    | Descrição                   |
|--------|-----------------------------|-----------------------------|
| GET    | `/api/reports`              | Listar relatórios           |
| GET    | `/api/reports/{id}`         | Metadados                   |
| GET    | `/api/reports/{id}/params`  | Parâmetros do relatório     |
| POST   | `/api/reports`              | Upload JRXML (multipart)    |
| DELETE | `/api/reports/{id}`         | Excluir                     |
| POST   | `/api/reports/{id}/run`     | Executar e baixar           |

**Exemplo de execução:**

```bash
curl -X POST http://admin:changeme@localhost:8090/api/reports/1/run \
  -H "Content-Type: application/json" \
  -d '{"parameters": {"DATA_INICIO": "2024-01-01"}, "datasourceId": 1, "format": "PDF"}' \
  --output relatorio.pdf
```

### Fontes de dados

| Método | Endpoint                     | Descrição        |
|--------|------------------------------|------------------|
| GET    | `/api/datasources`           | Listar           |
| GET    | `/api/datasources/{id}`      | Obter            |
| POST   | `/api/datasources`           | Criar            |
| PUT    | `/api/datasources/{id}`      | Atualizar        |
| DELETE | `/api/datasources/{id}`      | Excluir          |
| POST   | `/api/datasources/{id}/test` | Testar conexão   |

---

## Postman

Collection pronta para importar:

**`jasper-runner/postman/JasperRunner.postman_collection.json`**

Inclui pastas:

- **Auth (Sessao Web)** — `GET /login` (captura CSRF) e `POST /login`
- **Reports (API Basic)** — listar, obter, upload multipart, executar, excluir
- **Datasources (API Basic)** — CRUD e teste de conexão

Variáveis da collection (ajuste conforme ambiente):

| Variável       | Padrão                  |
|----------------|-------------------------|
| `baseUrl`      | `http://localhost:8090` |
| `apiUser`      | `admin`                 |
| `apiPass`      | `changeme`              |
| `reportId`     | `1`                     |
| `datasourceId` | `1`                     |
| `folderId`     | `1`                     |

No Postman: **Import** → selecione o arquivo JSON → configure `baseUrl` e credenciais.

---

## Observações técnicas

- **Cache de compilação:** `.jrxml` compilado na primeira execução e mantido em `ConcurrentHashMap`.
- **SUBREPORT_DIR:** definido automaticamente para o diretório do relatório pai.
- **Senhas JDBC:** AES-128/CBC antes de persistir no MySQL.
- **Drivers JDBC:** incluídos como dependências Maven (`runtime`).
- **Fontes:** `jasperreports.properties` mitiga ausência de Calibri; prefira DejaVu Sans nos JRXML.
- **Upload:** limite de 50 MB por arquivo (`spring.servlet.multipart`).
- **Timezone:** padrão `America/Recife` no container; datas JDBC em UTC.
