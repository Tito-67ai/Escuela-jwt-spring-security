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

# El Config Server guarda la configuracion compartida dentro de su propio JAR
# (carpeta config/). Se puede sustituir por un volumen montado sobre /config para
# cambiar rutas, secretos o usuarios sin reconstruir la imagen.
VOLUME ["/config"]

USER app
EXPOSE 8888

# El Config Server es el primero en arrancar: el resto de servicios lo consultan
# al iniciar. Por eso no lleva perfil docker; su configuracion no depende de si.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=20s --timeout=5s --start-period=30s --retries=5 \
  CMD ["sh", "-c", "wget -qO- http://localhost:8888/actuator/health || exit 1"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
