#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# deploy.sh — Script de implantação do JasperRunner
# Uso: ./deploy.sh [caminho/para/jasper-runner.jar]
# ─────────────────────────────────────────────────────────────
set -euo pipefail

SERVICE_NAME="jasper-runner"
DEPLOY_DIR="/opt/jasperrunner"
JAR_NAME="jasper-runner.jar"
SOURCE_JAR="${1:-target/${JAR_NAME}}"

echo "╔══════════════════════════════════════════╗"
echo "║   Deploy do JasperRunner                 ║"
echo "╚══════════════════════════════════════════╝"

# ─── 1. Valida o JAR de origem ───
if [ ! -f "${SOURCE_JAR}" ]; then
    echo "❌ Arquivo JAR não encontrado: ${SOURCE_JAR}"
    echo "   Execute 'mvn package' antes do deploy."
    exit 1
fi

# ─── 2. Para o serviço (se estiver rodando) ───
echo ""
echo "▶ Verificando serviço ${SERVICE_NAME}..."
if systemctl is-active --quiet "${SERVICE_NAME}"; then
    echo "  Parando ${SERVICE_NAME}..."
    sudo systemctl stop "${SERVICE_NAME}"
    echo "  ✓ Serviço parado."
else
    echo "  Serviço não está rodando. Continuando..."
fi

# ─── 3. Cria diretório de implantação ───
echo ""
echo "▶ Preparando diretório ${DEPLOY_DIR}..."
sudo mkdir -p "${DEPLOY_DIR}"/{data,reports,logs}
sudo chown -R jasperrunner:jasperrunner "${DEPLOY_DIR}" 2>/dev/null || true

# ─── 4. Faz backup do JAR anterior ───
if [ -f "${DEPLOY_DIR}/${JAR_NAME}" ]; then
    BACKUP="${DEPLOY_DIR}/${JAR_NAME}.$(date +%Y%m%d_%H%M%S).bak"
    echo "▶ Backup do JAR anterior: ${BACKUP}"
    sudo cp "${DEPLOY_DIR}/${JAR_NAME}" "${BACKUP}"
fi

# ─── 5. Copia o novo JAR ───
echo ""
echo "▶ Copiando ${SOURCE_JAR} → ${DEPLOY_DIR}/${JAR_NAME}..."
sudo cp "${SOURCE_JAR}" "${DEPLOY_DIR}/${JAR_NAME}"
echo "  ✓ JAR copiado."

# ─── 6. Copia application.yml (se ainda não existir no destino) ───
if [ ! -f "${DEPLOY_DIR}/application.yml" ]; then
    if [ -f "src/main/resources/application.yml" ]; then
        echo "▶ Copiando application.yml para ${DEPLOY_DIR}..."
        sudo cp "src/main/resources/application.yml" "${DEPLOY_DIR}/application.yml"
        echo "  ⚠  Edite ${DEPLOY_DIR}/application.yml e altere as senhas padrão!"
    fi
fi

# ─── 7. Instala o serviço systemd (se não instalado) ───
if [ ! -f "/etc/systemd/system/${SERVICE_NAME}.service" ]; then
    echo ""
    echo "▶ Instalando serviço systemd..."
    sudo cp "${SERVICE_NAME}.service" "/etc/systemd/system/"
    sudo systemctl daemon-reload
    sudo systemctl enable "${SERVICE_NAME}"
    echo "  ✓ Serviço instalado e habilitado."
fi

# ─── 8. Inicia o serviço ───
echo ""
echo "▶ Iniciando ${SERVICE_NAME}..."
sudo systemctl start "${SERVICE_NAME}"
sleep 2

if systemctl is-active --quiet "${SERVICE_NAME}"; then
    echo "  ✓ Serviço iniciado com sucesso!"
    echo ""
    echo "  URL: http://$(hostname -I | awk '{print $1}'):$(grep 'port:' ${DEPLOY_DIR}/application.yml | awk '{print $2}' || echo 8090)"
    echo "  Logs: sudo journalctl -u ${SERVICE_NAME} -f"
else
    echo "  ❌ O serviço falhou ao iniciar. Verifique os logs:"
    echo "     sudo journalctl -u ${SERVICE_NAME} --no-pager -n 50"
    exit 1
fi

echo ""
echo "╔══════════════════════════════════════════╗"
echo "║  Deploy concluído com sucesso! ✓          ║"
echo "╚══════════════════════════════════════════╝"
