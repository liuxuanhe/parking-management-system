# 地下停车场管理系统 - 多阶段构建
# 阶段一：Maven 构建
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
# 先下载依赖（利用 Docker 缓存层）
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# 阶段二：运行时镜像
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 创建非 root 用户
RUN addgroup -S parking && adduser -S parking -G parking

# 复制构建产物
COPY --from=builder /app/target/underground-parking-management-1.0.0-SNAPSHOT.jar app.jar

# 创建日志目录
RUN mkdir -p /app/logs && chown -R parking:parking /app

USER parking

EXPOSE 8080

# JVM 参数：容器环境优化
ENTRYPOINT ["java", \
  "-Xms512m", "-Xmx1024m", \
  "-XX:+UseG1GC", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
