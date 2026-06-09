#!/bin/sh
set -e

# Volumes montados pelo Easypanel sobrescrevem permissões do build — corrige antes de iniciar
mkdir -p /app/reports /app/logs
chown -R appuser:appgroup /app/reports /app/logs

exec gosu appuser java -jar /app/jasper-runner.jar \
  --spring.config.additional-location=file:/app/application.yml
