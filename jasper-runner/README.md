# JasperRunner

Substituto funcional do JasperServer Community Edition para execução de relatórios JasperReports (`.jrxml`) em servidor VPS Ubuntu.

> **Porta padrão:** `8090` — não conflita com um JasperServer já em execução.

---

## Funcionalidades

- **Repositório de relatórios** com árvore de pastas (CRUD completo)
- **Upload de arquivos `.jrxml`** e gerenciamento de metadados
- **Execução de relatórios** com formulário dinâmico de parâmetros (detectado automaticamente via `JRParameter`)
- **Exportação em:** PDF, XLSX, HTML, DOCX, CSV
- **Gerenciamento de Fontes de Dados JDBC** com teste de conexão e senha criptografada (AES-128)
- **Drivers suportados:** PostgreSQL, MySQL/MariaDB, Microsoft SQL Server, H2
- **API REST** para integrações externas
- **Spring Security** com autenticação via `application.yml`
- **UI responsiva** com Bootstrap 5, inspirada no layout do JasperServer

---

## Stack

| Tecnologia        | Versão  |
|-------------------|---------|
| Java              | 17+     |
| Spring Boot       | 3.2.5   |
| JasperReports     | 6.21.0  |
| Thymeleaf         | 3.x     |
| Spring Data JPA   | 3.x     |
| H2 (metadados)    | runtime |
| Maven             | 3.8+    |

---

## Compilar

```bash
cd jasper-runner
mvn clean package -DskipTests
```

O fat JAR é gerado em `target/jasper-runner-1.0.0.jar`.

---

## Executar localmente

```bash
java -jar target/jasper-runner-1.0.0.jar
```

Acesse: [http://localhost:8090](http://localhost:8090)

Credenciais padrão:
- **Usuário:** `admin`
- **Senha:** `changeme`

> ⚠️ **Altere obrigatoriamente as credenciais em produção!**

---

## Configuração (`application.yml`)

```yaml
server:
  port: 8090

jasperrunner:
  reports-root-path: ./reports       # pasta onde os .jrxml são armazenados
  admin-user: admin
  admin-password: SUA_SENHA_FORTE    # altere isso!
  encryption-key: SUA_CHAVE_AES_32   # chave de criptografia das senhas JDBC
```

---

## Deploy no Ubuntu VPS

### 1. Pré-requisitos

```bash
sudo apt update
sudo apt install openjdk-17-jre-headless
```

### 2. Criar usuário de serviço

```bash
sudo useradd -r -s /bin/false jasperrunner
sudo mkdir -p /opt/jasperrunner/{data,reports,logs}
sudo chown -R jasperrunner:jasperrunner /opt/jasperrunner
```

### 3. Deploy automatizado

```bash
# A partir do diretório do projeto (após mvn package)
chmod +x deploy.sh
./deploy.sh
```

O script:
1. Para o serviço existente
2. Faz backup do JAR anterior
3. Copia o novo JAR para `/opt/jasperrunner/`
4. Instala/atualiza o serviço systemd
5. Reinicia e verifica o serviço

### 4. Deploy manual

```bash
# Copiar JAR
sudo cp target/jasper-runner-1.0.0.jar /opt/jasperrunner/jasper-runner.jar

# Copiar configuração (edite antes!)
sudo cp src/main/resources/application.yml /opt/jasperrunner/

# Instalar serviço
sudo cp jasper-runner.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable jasper-runner
sudo systemctl start jasper-runner
```

### 5. Gerenciar o serviço

```bash
sudo systemctl status jasper-runner     # verificar status
sudo systemctl restart jasper-runner    # reiniciar
sudo journalctl -u jasper-runner -f     # acompanhar logs em tempo real
```

---

## API REST

Todos os endpoints exigem autenticação HTTP Basic.

### Relatórios

| Método | Endpoint                    | Descrição                        |
|--------|-----------------------------|----------------------------------|
| GET    | `/api/reports`              | Listar relatórios                |
| GET    | `/api/reports/{id}`         | Obter metadados                  |
| POST   | `/api/reports`              | Upload de JRXML (multipart)      |
| DELETE | `/api/reports/{id}`         | Excluir relatório                |
| POST   | `/api/reports/{id}/run`     | Executar e baixar                |

**Exemplo de execução:**
```bash
curl -X POST http://admin:changeme@localhost:8090/api/reports/1/run \
  -H "Content-Type: application/json" \
  -d '{"parameters": {"DATA_INICIO": "2024-01-01"}, "datasourceId": 1, "format": "PDF"}' \
  --output relatorio.pdf
```

### Fontes de Dados

| Método | Endpoint                          | Descrição              |
|--------|-----------------------------------|------------------------|
| GET    | `/api/datasources`                | Listar                 |
| POST   | `/api/datasources`                | Criar                  |
| PUT    | `/api/datasources/{id}`           | Atualizar              |
| DELETE | `/api/datasources/{id}`           | Excluir                |
| POST   | `/api/datasources/{id}/test`      | Testar conexão         |

---

## Estrutura do Projeto

```
jasper-runner/
├── src/main/java/com/seudominio/jasperrunner/
│   ├── JasperRunnerApplication.java
│   ├── config/          ← SecurityConfig, JasperRunnerProperties
│   ├── controller/      ← ReportController, DatasourceController, FolderController
│   │   └── api/         ← ReportApiController, DatasourceApiController
│   ├── dto/             ← ReportParameterInfo, ConnectionTestResult
│   ├── model/           ← ReportFolder, ReportDefinition, DataSourceConfig
│   ├── repository/      ← Interfaces JPA
│   ├── service/         ← FolderService, DatasourceService, ReportService
│   └── util/            ← EncryptionUtil (AES-128)
├── src/main/resources/
│   ├── templates/       ← Thymeleaf HTML (login, index, params, datasources, forms)
│   ├── static/css/      ← app.css (tema JasperServer)
│   └── application.yml
├── jasper-runner.service ← Arquivo systemd
├── deploy.sh             ← Script de deploy
└── pom.xml
```

---

## Observações Técnicas

- **Cache de compilação:** relatórios `.jrxml` são compilados na primeira execução e mantidos em `ConcurrentHashMap` — sem recompilação a cada chamada.
- **SUBREPORT_DIR:** definido automaticamente para o diretório do relatório pai.
- **Senhas JDBC:** criptografadas com AES-128/CBC antes de persistir no banco H2.
- **H2:** banco de metadados interno armazenado em `./data/jasperrunner.mv.db` — não conflita com bancos dos relatórios.
- **Drivers JDBC:** incluídos como dependências Maven (`runtime`) — PostgreSQL, MySQL, SQL Server e H2.
