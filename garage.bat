@echo off
rem Lanceur de Garage : double-clic. Demarre Docker Desktop si besoin, lance PostgreSQL + back + front, ouvre l'application.
rem Ports reserves a Garage : front 4201, back 8082, base 5434 (Banque utilise 4200 / 8081 / 5433).
setlocal
cd /d "%~dp0"
title Garage
set URL=http://garage.localhost:4201

call :en_ligne
if not errorlevel 1 (
    start "" msedge --app=%URL%
    exit /b 0
)

echo Demarrage de Garage...

rem 1. Docker Desktop
docker info >nul 2>&1
if not errorlevel 1 goto docker_pret
echo Demarrage de Docker Desktop, patientez...
start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"
set /a essais=0
:attendre_docker
timeout /t 3 /nobreak >nul
docker info >nul 2>&1
if not errorlevel 1 goto docker_pret
set /a essais+=1
if %essais% lss 60 goto attendre_docker
echo Docker Desktop n'a pas demarre. Lancez-le a la main, puis reessayez.
pause
exit /b 1
:docker_pret

rem 2. Les ports de Garage doivent etre libres (sauf s'ils sont deja utilises par les conteneurs Garage).
for %%P in (4201 8082 5434) do call :verifier_port %%P
if defined port_occupe (
    echo.
    echo Le port %port_occupe% est deja utilise par un autre programme.
    echo Lancez PortLiberator.exe sur le Bureau pour le liberer, puis relancez Garage.
    pause
    exit /b 1
)

rem 3. La base du mode IntelliJ (meme volume) ne doit pas tourner en meme temps.
docker rm -f garage-postgres >nul 2>&1

rem 4. La pile (--build : ne reconstruit que ce qui a change)
docker compose --env-file backend\.env up -d --build
if errorlevel 1 (
    echo.
    echo Echec du demarrage. Voir : docker compose logs
    pause
    exit /b 1
)

rem 5. Attente que le front et le back repondent
set /a essais=0
:attendre_app
call :en_ligne
if not errorlevel 1 goto app_prete
set /a essais+=1
if %essais% geq 60 (
    echo L'application ne repond pas. Voir : docker compose logs backend
    pause
    exit /b 1
)
timeout /t 2 /nobreak >nul
goto attendre_app
:app_prete

start "" msedge --app=%URL%
exit /b 0

:en_ligne
rem Front ET back doivent repondre (le back renvoie 401/403/405 sans jeton : c'est bon signe).
powershell -NoProfile -Command "try { Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 http://garage.localhost:4201 | Out-Null } catch { exit 1 }; try { Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 http://garage.localhost:8082/api/auth/login | Out-Null; exit 0 } catch { if ($_.Exception.Response) { exit 0 } else { exit 1 } }"
exit /b %errorlevel%

:verifier_port
rem Port occupe par autre chose qu'un conteneur Garage ? (docker publie les ports via com.docker.backend / wslrelay)
set "occupe="
for /f "tokens=5" %%A in ('netstat -ano ^| findstr /R /C:":%1 .*LISTENING"') do set occupe=%%A
if not defined occupe exit /b 0
docker ps --filter "name=garage-app" --format "{{.Ports}}" | findstr /C:":%1->" >nul 2>&1
if not errorlevel 1 exit /b 0
set port_occupe=%1
exit /b 0
