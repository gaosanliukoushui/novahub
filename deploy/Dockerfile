# NovaHub Application Dockerfile
# Skip builder stage - use pre-built JAR from local nova-web/target/
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories && \
    apk add --no-cache curl

# Download SkyWalking Agent from Huawei Cloud mirror (~43 MB, much faster than Apache archive)
RUN curl -o /tmp/apache-skywalking-apm.tar.gz \
        https://repo.huaweicloud.com/repository/apache/skywalking/java-agent/9.6.0/apache-skywalking-java-agent-9.6.0.tgz && \
    mkdir -p /opt/skywalking/agent && \
    tar -xzf /tmp/apache-skywalking-apm.tar.gz -C /opt/skywalking/agent --strip-components=1 && \
    rm /tmp/apache-skywalking-apm.tar.gz

# Copy the verified application package built from nova-web
COPY nova-web/target/nova-web-1.0.0.jar app.jar

ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError"
ENV SKYWALKING_OPTS="-javaagent:/opt/skywalking/agent/skywalking-agent.jar \
    -Dskywalking.agent.service_name=nova-hub-web \
    -Dskywalking.collector.backend_service=skywalking-oap:11800"
ENV ENABLE_SKYWALKING=false
ENV TZ=Asia/Shanghai

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=5 \
    CMD curl -sf http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "if [ \"$ENABLE_SKYWALKING\" = \"true\" ]; then exec java $JAVA_OPTS $SKYWALKING_OPTS -jar app.jar --spring.profiles.active=docker; else exec java $JAVA_OPTS -jar app.jar --spring.profiles.active=docker; fi"]
