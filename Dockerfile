# InsightAgent · 可移植部署
# 第一阶段先在本地（或任意一台有 Docker 的机器）构建镜像，
# 不依赖本机装的 JDK / Maven / MySQL / Redis。
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

# 第二阶段只留运行环境，镜像更小，换机器也不用重装 JDK。
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
