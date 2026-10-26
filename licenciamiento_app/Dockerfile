# Etapa 1: compilar el proyecto Java con Maven
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# Etapa 2: ejecutar la aplicación
FROM eclipse-temurin:17-jdk-jammy
ARG JAR_FILE=target/licenciamiento_app-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} licenciamiento_app.jar
EXPOSE 8086
ENTRYPOINT ["java","-jar","licenciamiento_app.jar"]