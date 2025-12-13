@echo off
echo Iniciando 6 clientes (cada uno a un servidor diferente)...
echo.

start cmd /k "cd bin && java client.ClientMain 1099"
timeout /t 2
start cmd /k "cd bin && java client.ClientMain 1100"
timeout /t 2
start cmd /k "cd bin && java client.ClientMain 1101"
timeout /t 2
start cmd /k "cd bin && java client.ClientMain 1102"
timeout /t 2
start cmd /k "cd bin && java client.ClientMain 1103"
timeout /t 2
start cmd /k "cd bin && java client.ClientMain 1104"

echo 6 clientes iniciados.