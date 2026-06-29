# ========================================
# CampusShare Backend - Multi-stage Build
# ========================================

# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy Maven settings with Aliyun mirror for faster downloads in China
COPY settings.xml /usr/share/maven/conf/settings.xml

# Copy pom files
COPY pom.xml .
COPY campushare-common/pom.xml campushare-common/
COPY campushare-user/pom.xml campushare-user/
COPY campushare-post/pom.xml campushare-post/
COPY campushare-gateway/pom.xml campushare-gateway/

# Download dependencies (this layer will be cached)
RUN mvn dependency:go-offline -B

# Copy source code
COPY campushare-common/src campushare-common/src
COPY campushare-user/src campushare-user/src
COPY campushare-post/src campushare-post/src
COPY campushare-gateway/src campushare-gateway/src

# Build the project
RUN mvn clean package -DskipTests

# ========================================
# Stage 2: User Service
# ========================================
FROM eclipse-temurin:17-jre-alpine AS user-service

WORKDIR /app

RUN apk add --no-cache curl tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

ENV TZ=Asia/Shanghai

# 下载 OpenTelemetry Java Agent
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.3.0/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

COPY --from=builder /build/campushare-user/target/campushare-user-*.jar app.jar

# Expose port
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8081/actuator/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=docker -jar app.jar"]

# ========================================
# Stage 3: Post Service
# ========================================
FROM eclipse-temurin:17-jre-alpine AS post-service

WORKDIR /app

RUN apk add --no-cache curl tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

ENV TZ=Asia/Shanghai

# 下载 OpenTelemetry Java Agent
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.3.0/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

COPY --from=builder /build/campushare-post/target/campushare-post-*.jar app.jar

# Expose port
EXPOSE 8082

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8082/actuator/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=docker -jar app.jar"]

# ========================================
# Stage 4: Gateway Service
# ========================================
FROM eclipse-temurin:17-jre-alpine AS gateway-service

WORKDIR /app

RUN apk add --no-cache curl tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

ENV TZ=Asia/Shanghai

# 下载 OpenTelemetry Java Agent
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.3.0/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

COPY --from=builder /build/campushare-gateway/target/campushare-gateway-*.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=docker -jar app.jar"]
