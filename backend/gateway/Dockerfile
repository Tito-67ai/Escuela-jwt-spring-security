# ---------- Etapa 1: compilar el servicio ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- Etapa 2: imagen minima de ejecucion ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# El usuario "app" no es root: si alguien explota el servicio, no obtiene
# privilegios de administrador dentro del contenedor.
RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /build/target/*.jar app.jar
RUN chown -R app:app /app

# El gateway no guarda nada en disco: es puro enrutado y validacion de tokens.
USER app
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=20s --timeout=5s --start-period=45s --retries=5 \
  CMD ["sh", "-c", "wget -qO- http://localhost:8080/actuator/health || exit 1"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
