@echo off
SETLOCAL

REM --- Crear carpeta bin si no existe ---
IF NOT EXIST bin (
    echo [INFO] Creando carpeta 'bin'...
    mkdir bin
    IF ERRORLEVEL 1 (
        echo [ERROR] No se pudo crear la carpeta 'bin'
        pause
        GOTO Fin
    )
)

echo Compilando cliente y servidor en carpeta /bin
javac -encoding UTF-8 -d bin src/common/*.java src/client/*.java src/server/core/*.java src/server/infra/*.java 

:Fin
ENDLOCAL