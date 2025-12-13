@echo off
echo Iniciando 6 servidores...
echo Puertos: 1099-1104
echo.

start cmd /k "cd bin && java server.core.ServerMain 0 1099 6"
timeout /t 5
start cmd /k "cd bin && java server.core.ServerMain 1 1100 6"
timeout /t 5
start cmd /k "cd bin && java server.core.ServerMain 2 1101 6"
timeout /t 5
start cmd /k "cd bin && java server.core.ServerMain 3 1102 6"
timeout /t 5
start cmd /k "cd bin && java server.core.ServerMain 4 1103 6"
timeout /t 5
start cmd /k "cd bin && java server.core.ServerMain 5 1104 6"

echo Esperando 20 segundos para estabilizacion completa...
timeout /t 20
echo 6 servidores iniciados y estables.