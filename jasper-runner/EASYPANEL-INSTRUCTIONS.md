# Instalação do JasperRunner no EasyPanel

Este guia mostra como publicar o JasperRunner no EasyPanel usando Docker, com persistência de dados e configuração de produção.

## 1) Pré-requisitos

- EasyPanel funcionando no servidor
- Domínio (opcional) apontando para o servidor
- Projeto JasperRunner versionado no Git (GitHub/GitLab) ou disponível localmente para build
- Banco externo para relatórios (MySQL/PostgreSQL/SQL Server), se aplicável

## 2) Estrutura persistente recomendada

No container, a aplicação usa por padrão:

- `./data` (H2 da aplicação)
- `./reports` (JRXML/JASPER e recursos)
- `./logs` (logs)

No EasyPanel, mapeie para volumes persistentes:

- `/app/data`
- `/app/reports`
- `/app/logs`

## 3) Dockerfile para EasyPanel

Crie um arquivo `Dockerfile` na raiz do projeto com o conteúdo abaixo:

```dockerfile
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app

# Pasta de runtime
RUN mkdir -p /app/data /app/reports /app/logs

# Jar final
COPY --from=build /src/target/jasper-runner-1.0.0.jar /app/jasper-runner.jar

# application.yml default (pode ser sobrescrito por variaveis)
COPY src/main/resources/application.yml /app/application.yml
COPY src/main/resources/jasperreports.properties /app/jasperreports.properties

EXPOSE 8090

ENTRYPOINT ["java","-jar","/app/jasper-runner.jar","--spring.config.location=file:/app/application.yml"]
```

## 4) Criar app no EasyPanel

No EasyPanel:

1. Crie um novo projeto/app
2. Escolha deploy via Git
3. Selecione o repositório do JasperRunner
4. Build Type: Dockerfile
5. Porta interna: `8090`
6. Domínio: configure se desejar (ex: `relatorios.seudominio.com`)

## 5) Variáveis de ambiente importantes

Você pode manter `application.yml` no container ou parametrizar via env vars do Spring.

Sugestão de env vars no EasyPanel:

- `SERVER_PORT=8090`
- `SPRING_DATASOURCE_URL=jdbc:h2:file:/app/data/jasperrunner`
- `SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver`
- `SPRING_DATASOURCE_USERNAME=sa`
- `SPRING_DATASOURCE_PASSWORD=`
- `JASPERRUNNER_REPORTS_ROOT_PATH=/app/reports`
- `JASPERRUNNER_ADMIN_USER=admin`
- `JASPERRUNNER_ADMIN_PASSWORD=troque_esta_senha`
- `JASPERRUNNER_ENCRYPTION_KEY=troque_chave_forte_aqui_32_chars`
- `LOGGING_FILE_NAME=/app/logs/jasper-runner.log`
- `SPRING_THYMELEAF_CACHE=false`

Se usar env vars, ajuste o `ENTRYPOINT` para:

```dockerfile
ENTRYPOINT ["java","-jar","/app/jasper-runner.jar"]
```

## 6) Volumes persistentes no EasyPanel

Adicione mapeamentos:

- Volume `jasperrunner-data` -> `/app/data`
- Volume `jasperrunner-reports` -> `/app/reports`
- Volume `jasperrunner-logs` -> `/app/logs`

Assim os dados nao se perdem em redeploy.

## 7) Healthcheck (recomendado)

Adicione no EasyPanel (se suportado) ou no Dockerfile:

```dockerfile
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8090/login >/dev/null || exit 1
```

## 8) Primeiro acesso

- URL: `http://seu-dominio-ou-ip:8090`
- Login padrão: `admin / changeme` (ou conforme env)

Troque imediatamente:

- `JASPERRUNNER_ADMIN_PASSWORD`
- `JASPERRUNNER_ENCRYPTION_KEY`

## 9) Upload de relatórios e recursos

Após subir:

1. Crie pastas no repositório interno (UI)
2. Faça upload de `.jrxml`, `.jasper`, imagens e recursos
3. Cadastre Data Source JDBC
4. Teste conexão
5. Execute relatório

## 10) Dependências já tratadas no projeto

Seu projeto já inclui libs necessárias para cenários comuns:

- Barcode4J
- ZXing
- Batik (SVG)
- JasperReports + exportadores

Se surgir `ClassNotFoundException`, verifique no log qual classe faltou e adicione no `pom.xml`.

## 11) Troubleshooting rápido

### Erro de fonte (Calibri não encontrada)

Já foi mitigado com `jasperreports.properties`:

- `net.sf.jasperreports.awt.ignore.missing.font=true`
- fallback para `DejaVu Sans`

Se quiser fidelidade visual exata, instale fontes Microsoft no host/container.

### Subrelatório não encontrado

Verifique:

- Caminho relativo no JRXML (`../templates/...`)
- Se o arquivo existe em `/app/reports/...`
- Se o upload do recurso foi feito

### Falha de conexão JDBC

- Driver correto no Data Source
- URL/porta/liberação de firewall
- Usuário/senha

## 12) Fluxo de atualização no EasyPanel

1. Commit/push no repositório
2. No EasyPanel, clique em Redeploy
3. Aguarde build + start
4. Verifique logs
5. Valide execução de um relatório de teste

## 13) Segurança mínima recomendada em produção

- Use HTTPS no domínio
- Altere credenciais padrão
- Restrinja acesso por IP se possível
- Faça backup periódico dos volumes:
  - `/app/data`
  - `/app/reports`
  - `/app/logs`

## 14) Checklist final

- App online em `:8090` (ou via domínio)
- Login funcionando
- Data Source criado e testado
- Relatório com subrelatório gerando PDF
- Volumes persistentes configurados
- Senhas/chaves de produção aplicadas
