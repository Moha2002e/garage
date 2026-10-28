@echo off
rem Arrete Garage (les donnees restent dans le volume Docker).
cd /d "%~dp0"
docker compose --env-file backend\.env down
echo Garage arrete.
timeout /t 3 >nul
