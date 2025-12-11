@echo off
echo Iniciando 3 servidores...
start cmd /k "cd bin && java server.core.ServerMain 0 1099 3"
timeout /t 5
start cmd /k "cd bin && java server.core.ServerMain 1 1100 3"
timeout /t 5
start cmd /k "cd bin && java server.core.ServerMain 2 1101 3"
echo Servidores iniciados.