@echo off
echo Iniciando 3 clientes conectados a servidores diferentes...
echo.
echo Cliente 1 -> Servidor 0 (puerto 1099)
echo Cliente 2 -> Servidor 1 (puerto 1100)
echo Cliente 3 -> Servidor 2 (puerto 1101)
echo.

start cmd /k "cd bin && java client.ClientMain 1099"
timeout /t 2
start cmd /k "cd bin && java client.ClientMain 1100"
timeout /t 2
start cmd /k "cd bin && java client.ClientMain 1101"

echo Los 3 clientes se estan iniciando...