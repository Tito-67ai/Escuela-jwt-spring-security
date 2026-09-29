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

RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /build/target/*.jar app.jar
RUN chown -R app:app /app

USER app
EXPOSE 9090

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

# Panel de monitorizacion: Grafana, Prometheus, Prometheus Alertmanager y Zipkin.
HEALTHCHECK --interval=20s --timeout=5s --start-period=45s --retries=5 \
  CMD ["sh", "-c", "wget -qO- http://localhost:9090/actuator/health || exit 1"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
