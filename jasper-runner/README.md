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
- **API REST** para integrações externas (HTTP Basic, stateless)
- **Gestão de usuários** persistida em MySQL com perfil e troca de senha
- **Recuperação de senha** por e-mail com link temporário
- **Spring Security** com duas cadeias: API (`/api/**`) e interface web
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
| MySQL (metadados) | runtime |
| Maven             | 3.8+    |

---

## Compilar

```bash
cd jasper-runner
mvn clean package -DskipTests
```

O fat JAR é gerado em `target/jasper-runner-1.0.0.jar`.

---

## Configuração MySQL por variáveis de ambiente

Os metadados da aplicação (pastas, relatórios e fontes de dados) são persistidos em MySQL. Configure as variáveis antes de iniciar:

```bash
export DB_HOST=localhost
export DB_PORT=3306
export DB_DATABASE=jasper_runner
export DB_USER=root
export DB_PASSWORD=sua_senha
```

Copie o arquivo de exemplo e preencha os valores localmente (não commite o `.env`):

```bash
cp .env.example .env
# edite .env com suas credenciais
```

O Spring Boot lê as variáveis de ambiente automaticamente. Valores padrão (quando omitidos): `DB_HOST=localhost`, `DB_PORT=3306`, `DB_DATABASE=jasper_runner`, `DB_USER=root`, `DB_PASSWORD=` (vazio).

### E-mail (recuperação de senha)

Configure o SMTP no `.env` para habilitar o envio de links de reset:

```bash
MAILER_HOST=smtp.hostinger.com
MAILER_PORT=465
MAILER_SECURE=true
MAILER_USER=seu@email.com
MAILER_PASS=sua_senha_smtp
```

Defina também a URL pública da aplicação (usada nos links do e-mail):

```bash
export APP_BASE_URL=https://seu-dominio.com
```

---

## Executar localmente

```bash
export DB_HOST=localhost
export DB_PORT=3306
export DB_DATABASE=jasper_runner
export DB_USER=root
export DB_PASSWORD=sua_senha

java -jar target/jasper-runner-1.0.0.jar
```

Acesse: [http://localhost:8090](http://localhost:8090)

Credenciais padrão (criadas automaticamente no primeiro start, se não existir usuário admin):
- **Usuário:** `admin`
- **Senha:** `changeme`

> ⚠️ **Altere obrigatoriamente as credenciais em produção!**

As mesmas credenciais servem para login na interface web e para autenticação HTTP Basic na API.

---

## Usuários e recuperação de senha

### Modelo de usuário

Usuários são persistidos na tabela `users` do MySQL com senha em BCrypt (`password_hash`). Campos: nome, e-mail (único), username (único), papel (`ADMIN` ou `USER`), status ativo e timestamps.

No primeiro start, se não houver usuário com o username configurado em `jasperrunner.admin-user`, um administrador é criado automaticamente com os valores de `admin-user`, `admin-password`, `admin-name` e `admin-email` do `application.yml`.

### Perfil (`/profile`)

Após login na interface web, acesse **Perfil** no menu do usuário para:

- Editar **nome** e **e-mail**
- **Alterar senha** (exige senha atual; nova senha com mínimo de 8 caracteres)

### Recuperação de senha

1. Na tela de login, clique em **Esqueceu sua senha?**
2. Informe o **username** — a resposta é sempre genérica (não revela se o usuário existe)
3. Se o usuário existir e tiver e-mail cadastrado, um link é enviado para `/password/reset?token=...` (válido por 30 minutos por padrão)
4. Defina a nova senha na tela de reset; o token é invalidado após o uso

Requisitos: `MAILER_USER` e `MAILER_PASS` configurados, e `APP_BASE_URL` apontando para a URL acessível pelos usuários.

---

## Configuração (`application.yml`)

```yaml
server:
  port: 8090

jasperrunner:
  reports-root-path: ./reports       # pasta onde os .jrxml são armazenados
  admin-user: admin
  admin-password: SUA_SENHA_FORTE    # altere isso!
  admin-name: Administrator
  admin-email: admin@seudominio.com
  base-url: http://localhost:8090    # ou APP_BASE_URL em produção
  password-reset-expiration-minutes: 30
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

Todos os endpoints em `/api/**` exigem autenticação **HTTP Basic** (stateless). Sem credenciais válidas, a API retorna `401 Unauthorized` e **não executa** a operação solicitada.

A interface web usa login por formulário com sessão — fluxos separados.

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
│   ├── config/          ← SecurityConfig, JasperRunnerProperties, AdminUserSeeder
│   ├── controller/      ← ReportController, ProfileController, PasswordResetController
│   │   └── api/         ← ReportApiController, DatasourceApiController
│   ├── dto/             ← Formulários e DTOs de API
│   ├── model/           ← User, PasswordResetToken, ReportFolder, ReportDefinition, ...
│   ├── repository/      ← Interfaces JPA
│   ├── security/        ← UserPrincipal, DatabaseUserDetailsService
│   ├── service/         ← UserService, PasswordResetService, EmailService, ...
│   └── util/            ← EncryptionUtil, PasswordPolicy
├── src/main/resources/
│   ├── templates/       ← Thymeleaf HTML (login, profile, password-forgot/reset, ...)
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
- **Senhas JDBC:** criptografadas com AES-128/CBC antes de persistir no MySQL.
- **MySQL:** banco de metadados configurável via variáveis de ambiente (`DB_HOST`, `DB_PORT`, `DB_DATABASE`, `DB_USER`, `DB_PASSWORD`) — não conflita com bancos dos relatórios.
- **Drivers JDBC:** incluídos como dependências Maven (`runtime`) — PostgreSQL, MySQL, SQL Server e H2.
