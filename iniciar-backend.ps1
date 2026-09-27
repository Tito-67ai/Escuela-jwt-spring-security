# ============================================================
#  Inicia todo el backend de microservicios desde la raiz del
#  proyecto. Al ejecutarse desde aqui, las banderas jdbc:h2:file:./data/*
#  apuntan a la carpeta data\ de la raiz (bases H2 reutilizables).
# ============================================================

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

# Orden importante: registry y config-server son la base. Si arrancan los
# microservicios antes, no encuentran la configuracion centralizada (caen en los
# valores de respaldo de su application.yml) y el gateway no puede resolver las
# rutas lb:// hasta que Eureka los registra.
$orden = @(
    @{ nombre = 'registry';      jar = 'registry-0.0.1-SNAPSHOT.jar';       puerto = 8761 },
    @{ nombre = 'config-server'; jar = 'config-server-0.0.1-SNAPSHOT.jar';  puerto = 8888 }
)

$logDir = Join-Path $root 'backend\logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

Write-Host "Levantando backend. Requiere RabbitMQ activo:  docker compose up -d" -ForegroundColor Yellow

function Levantar($s) {
    $jarPath = Join-Path $root "backend\$($s.nombre)\target\$($s.jar)"
    if (-not (Test-Path $jarPath)) {
        Write-Warning "No se encontro el jar de $($s.nombre). Ejecuta:  backend\$($s.nombre)\mvnw.cmd package -DskipTests"
        return
    }
    # Comillas en la ruta: sin ellas java no encuentra el jar si la carpeta
    # del proyecto tiene espacios (ej. "Default Project").
    Start-Process java -ArgumentList "-jar `"$jarPath`"" -WorkingDirectory $root `
        -RedirectStandardOutput (Join-Path $logDir "$($s.nombre).log") `
        -RedirectStandardError  (Join-Path $logDir "$($s.nombre).err") `
        -WindowStyle Hidden
    Write-Host "  -> $($s.nombre) iniciado" -ForegroundColor Green
}

function Esperar($puerto, $segundos) {
    for ($i = 0; $i -lt $segundos; $i++) {
        if (Test-NetConnection -ComputerName localhost -Port $puerto -InformationLevel Quiet -WarningAction SilentlyContinue) {
            return $true
        }
        Start-Sleep -Seconds 1
    }
    return $false
}

foreach ($s in $orden) {
    Levantar $s
    if (-not (Esperar $s.puerto 90)) {
        Write-Warning "  $($s.nombre) no respondio en el puerto $($s.puerto). Revisá backend\logs\$($s.nombre).log"
    }
}

# A partir de aqui el resto de los servicios.
foreach ($s in @(
    @{ nombre = 'alumnos';        jar = 'alumnos-0.0.1-SNAPSHOT.jar' },
    @{ nombre = 'administracion'; jar = 'administracion-0.0.1-SNAPSHOT.jar' },
    @{ nombre = 'gateway';        jar = 'gateway-0.0.1-SNAPSHOT.jar' },
    @{ nombre = 'admin-server';   jar = 'admin-server-0.0.1-SNAPSHOT.jar' }
)) {
    Levantar $s
}

Write-Host ""
Write-Host "URLs:"
Write-Host "  Eureka (Registry):  http://localhost:8761"
Write-Host "  Config Server:      http://localhost:8888" 
Write-Host "  API Gateway:        http://localhost:8080"
Write-Host "  Alumnos directo:    http://localhost:8101/api/alumnos"
Write-Host "  RabbitMQ UI:        http://localhost:15672  (guest/guest)"
Write-Host "  Spring Boot Admin:  http://localhost:9090"
Write-Host "Logs en: backend\logs"