# 多阶段构建：构建阶段用 Maven 镜像，运行阶段只保留 JRE。
FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY springboot-core/pom.xml springboot-core/pom.xml
COPY springboot-core/src springboot-core/src
COPY frontend frontend

RUN cd springboot-core \
    && mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=build /workspace/springboot-core/target/jitong-spring-core-*.jar /app/app.jar

RUN addgroup -S jitong \
    && adduser -S jitong -G jitong \
    && mkdir -p /app/data \
    && chown -R jitong:jitong /app

USER jitong
EXPOSE 8080

ENV JITONG_PORT=8080 \
    JITONG_DATA_DIR=/app/data

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/api/health >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
