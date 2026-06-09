# JasperRunner no EasyPanel

Guia rápido para publicar o JasperRunner como container Docker no EasyPanel.

> **Java:** já vem instalado no container (`eclipse-temurin:17-jre`). Não é preciso Java no servidor host.

---

## 1. Configurar fonte Git

| Campo | Valor |
|-------|-------|
| Proprietário | `Tommendes` |
| Repositório | `JasperRunner` |
| Ramo | `main` |
| **Caminho de Build** | **`/jasper-runner`** |
| Tipo de build | Dockerfile |

O `Dockerfile` fica em `jasper-runner/Dockerfile` na raiz do repositório — por isso o caminho de build **não** é `/`.

**Porta interna do container:** `8090`

---

## 2. Banco MySQL (obrigatório)

Os metadados da aplicação (usuários, pastas, relatórios, datasources) ficam em **MySQL**.

Crie um serviço MySQL no EasyPanel (ou use um banco externo) e configure as variáveis de ambiente no app:

| Variável | Exemplo |
|----------|---------|
| `DB_HOST` | nome do serviço MySQL no EasyPanel (ex: `mysql`) |
| `DB_PORT` | `3306` |
| `DB_DATABASE` | `jasper_runner` |
| `DB_USER` | `jasper` |
| `DB_PASSWORD` | senha forte |

> **MariaDB no EasyPanel:** se o serviço de banco for MariaDB (padrão em muitos hosts), adicione também:
> `JPA_DATABASE_PLATFORM=org.hibernate.dialect.MariaDBDialect`

Crie o banco antes do primeiro deploy:

```sql
CREATE DATABASE jasper_runner CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

O Hibernate cria as tabelas automaticamente (`ddl-auto: update`).

---

## 3. Variáveis de ambiente (produção)

Além do MySQL, configure no EasyPanel:

| Variável | Descrição |
|----------|-----------|
| `APP_BASE_URL` | URL pública (ex: `https://relatorios.seudominio.com`) |
| `JASPERRUNNER_ADMIN_PASSWORD` | Senha do admin (padrão: `changeme`) |
| `JASPERRUNNER_ENCRYPTION_KEY` | Chave AES para senhas JDBC (mín. 16 caracteres) |
| `JASPERRUNNER_REPORTS_ROOT_PATH` | `/app/reports` |
| `LOGGING_FILE_NAME` | `/app/logs/jasper-runner.log` |

### E-mail (opcional — recuperação de senha)

| Variável | Exemplo |
|----------|---------|
| `MAILER_HOST` | `smtp.hostinger.com` |
| `MAILER_PORT` | `465` |
| `MAILER_SECURE` | `true` |
| `MAILER_USER` | `seu@email.com` |
| `MAILER_PASS` | senha SMTP |

---

## 4. Volumes persistentes

Mapeie no EasyPanel para não perder dados em redeploy:

| Volume | Caminho no container |
|--------|---------------------|
| `jasperrunner-reports` | `/app/reports` |
| `jasperrunner-logs` | `/app/logs` |

---

## 5. Domínio e HTTPS

Configure o domínio no EasyPanel (ex: `relatorios.seudominio.com`) e ative HTTPS. Atualize `APP_BASE_URL` com a URL final.

---

## 6. Primeiro acesso

- **URL:** seu domínio ou IP do servidor
- **Login padrão:** `admin` / `changeme`
- Troque a senha imediatamente via `JASPERRUNNER_ADMIN_PASSWORD` ou pelo menu **Perfil**

---

## 7. Atualizar após mudanças no código

1. `git push` para `main`
2. No EasyPanel: **Redeploy**
3. Verifique os logs do container

---

## 8. Troubleshooting

| Problema | Solução |
|----------|---------|
| Build falha com "pom.xml not found" | Caminho de Build deve ser `/jasper-runner` |
| App não sobe / erro de conexão | Verifique `DB_HOST`, `DB_PORT` e se o MySQL está acessível na rede interna |
| `Unknown column 'RESERVED'` / erro de Dialect | Já corrigido no Dockerfile; confirme redeploy com código atual |
| Tabelas / admin não criados | App precisa subir até o fim; nos logs deve aparecer `Usuário admin inicial criado`. Tabela: `app_users` |
| Relatórios sumiram após redeploy | Configure volume em `/app/reports` |
| Fonte Calibri ausente | Já mitigado em `jasperreports.properties`; use DejaVu Sans ou instale a fonte |
| Subrelatório não encontrado | Verifique caminhos relativos no JRXML e se os arquivos estão em `/app/reports` |

---

## Checklist

- [ ] Caminho de Build: `/jasper-runner`
- [ ] Porta: `8090`
- [ ] MySQL criado e variáveis `DB_*` configuradas
- [ ] `JASPERRUNNER_ADMIN_PASSWORD` e `JASPERRUNNER_ENCRYPTION_KEY` alterados
- [ ] Volumes em `/app/reports` e `/app/logs`
- [ ] `APP_BASE_URL` com URL pública correta
- [ ] Login e execução de um relatório de teste OK
