@echo off
SETLOCAL

REM --- 1. Verificación de argumentos ---
IF "%~1"=="" GOTO ErrorArgs
IF "%~2"=="" GOTO ErrorArgs

SET ID=%1
SET PORT=%2

echo ==================================================
echo   LANZADOR AUTOMATICO: NODO %ID% (Puerto %PORT%)
echo ==================================================

REM --- 2. Verificar que existe la carpeta bin ---
IF NOT EXIST bin (
    echo [ERROR] No encuentro la carpeta 'bin'. 
    echo Por favor, ejecuta primero el script de compilacion.
    pause
    GOTO Fin
)

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