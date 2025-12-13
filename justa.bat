@echo off
echo ================================
echo    PC2 - Servidor 1 + Cliente  
echo ================================
echo.

REM 1. Iniciar Servidor 1 (en puerto 1101)
echo Iniciando Servidor 1 en puerto 1101...
start cmd /k "cd bin && java server.core.ServerMain 2 1101 ..\config.txt"

echo Esperando 5 segundos para estabilizacion...
timeout /t 5

REM 2. Iniciar Cliente conectado a Servidor 1
echo Iniciando Cliente conectado a Servidor 1...
start cmd /k "cd bin && java client.ClientMain 1101"

echo.
echo ================================
echo    PC2 LISTO
echo ================================
echo Servidor 1: puerto 1100
echo Cliente: conectado a 127.0.0.1:1100
echo.
pause