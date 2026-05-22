#!/bin/bash

# NovaHub SkyWalking Agent 启动脚本
# 用法: ./start.sh [可选: JAVA_OPTS]
# 前提条件: SkyWalking Agent JAR 包已挂载到 /opt/skywalking-agent/skywalking-agent.jar

set -e

SKYWALKING_AGENT_PATH="${SKYWALKING_AGENT_PATH:-/opt/skywalking-agent/skywalking-agent.jar}"
SERVICE_NAME="${SERVICE_NAME:-nova-hub-web}"
OAP_HOST="${SKYWALKING_OAP_HOST:-skywalking-oap}"
OAP_PORT="${SKYWALKING_OAP_PORT:-11800}"
INSTANCE_NAME="${HOSTNAME:-nova-hub}"
APP_JAR="${APP_JAR:-/app/nova-web.jar}"

if [ ! -f "$SKYWALKING_AGENT_PATH" ]; then
    echo "[WARN] SkyWalking Agent JAR not found at $SKYWALKING_AGENT_PATH, starting without tracing"
    exec java -jar "$APP_JAR" "$@"
fi

JAVA_OPTS="-javaagent:${SKYWALKING_AGENT_PATH} \
  -Dskywalking.agent.service_name=${SERVICE_NAME} \
  -Dskywalking.collector.backend_service=${OAP_HOST}:${OAP_PORT} \
  -Dskywalking.logging.output=FILE \
  -Dskywalking.agent.instance_name=${INSTANCE_NAME} \
  -Dskywalking.agent.sample_n_per_3_secs=10"

echo "[INFO] Starting NovaHub with SkyWalking Agent"
echo "[INFO] Service: ${SERVICE_NAME}, OAP: ${OAP_HOST}:${OAP_PORT}"

exec java $JAVA_OPTS -jar "$APP_JAR" "$@"
