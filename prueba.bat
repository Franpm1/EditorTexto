@echo off
REM Compilar (opcional, ajusta según tu estructura)
javac -d bin src/common/*.java src/server/infra/*.java src/server/core/*.java src/client/*.java

REM Lanzar 3 Servidores
start "Server 0" cmd /k "java -cp bin server.core.ServerMain 0 1099"
timeout /t 2
start "Server 1" cmd /k "java -cp bin server.core.ServerMain 1 1100"
timeout /t 2
start "Server 2 (LIDER)" cmd /k "java -cp bin server.core.ServerMain 2 1101"

REM Lanzar 2 Clientes
timeout /t 4
start "Client A -> Server 0" cmd /k "java -cp bin client.ClientMain 1099"
start "Client B -> Server 1" cmd /k "java -cp bin client.ClientMain 1100"