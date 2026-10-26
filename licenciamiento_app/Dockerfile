# --- ETAPA 1: Compilación con Maven ---
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# Como el Dockerfile está dentro de licenciamiento_app,
# copiamos todo el contenido del directorio actual (.) hacia /app
COPY . .

# Dar permisos al wrapper de Maven y compilar
RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

# --- ETAPA 2: Ejecución ligera ---
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app

# Copiar el JAR generado en la etapa anterior
COPY --from=build /app/target/licenciamiento_app-0.0.1-SNAPSHOT.jar licenciamiento_app.jar

EXPOSE 8086
ENTRYPOINT ["java", "-jar", "licenciamiento_app.jar"]