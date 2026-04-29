# Etapa 1: Construcción (Maven)
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
# Definimos el directorio de trabajo
WORKDIR /build
# Copiamos los archivos al directorio /build
COPY . .
# Ahora sí, ejecutamos Maven dentro de /build
RUN mvn clean package -DskipTests

# Etapa 2: Ejecución (Runtime)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Buscamos el jar en la carpeta de la etapa anterior
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]