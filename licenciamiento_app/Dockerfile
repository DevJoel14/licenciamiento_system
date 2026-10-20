FROM eclipse-temurin:17-jdk-jammy
ARG JAR_FILE=target/licenciamiento_app-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} licenciamiento_app.jar
EXPOSE 8086
ENTRYPOINT ["java","-jar","licenciamiento_app.jar"]