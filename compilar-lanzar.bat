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
REM --- 1. Verificación de argumentos ---
IF "%~1"=="" GOTO ErrorArgs
IF "%~2"=="" GOTO ErrorArgs

SET ID=%1
SET PORT=%2

echo ==================================================
echo   LANZADOR AUTOMATICO: NODO %ID% (Puerto %PORT%)
echo ==================================================

REM --- 3. Iniciar SERVIDOR ---
echo [1/2] Iniciando Servidor %ID%...
REM "start" abre una ventana nueva. 
REM El primer parametro entre comillas es el Titulo de la ventana.
start "SERVIDOR %ID% (%PORT%)" java -cp bin server.core.ServerMain %ID% %PORT%

REM --- 4. Espera de 3 segundos ---
echo [WAIT] Esperando 3 segundos para estabilizacion...
timeout /t 3 /nobreak >nul

REM --- 5. Iniciar CLIENTE ---
echo [2/2] Iniciando Cliente...
start "CLIENTE Usuario%PORT%" java -cp bin client.ClientMain %PORT%

echo.
echo [EXITO] Nodo %ID% desplegado completamente.
GOTO Fin

:ErrorArgs
echo.
echo [ERROR] Faltan argumentos.
echo ------------------------------------------
echo Uso correcto:   lanzar.bat ^<ID^> ^<PUERTO^>
echo Ejemplo:        lanzar.bat 0 1099
echo ------------------------------------------
pause

:Fin
ENDLOCAL