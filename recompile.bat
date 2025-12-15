@echo off
echo ==========================================
echo   LIMPIANDO Y RECOMPILANDO PROYECTO
echo ==========================================

REM 1. Limpiar: Borrar carpeta bin si existe
if exist bin (
    echo [1/3] Borrando archivos antiguos (.class)...
    rmdir /s /q bin
)

REM 2. Crear carpeta bin de nuevo
echo [2/3] Creando carpeta de destino (bin)...
mkdir bin

REM 3. Compilar
echo [3/3] Compilando codigo fuente...
REM Ajusta la ruta 'src' si tu codigo esta en otro lado
javac -d bin -sourcepath src src/common/*.java src/server/infra/*.java src/server/core/*.java src/client/*.java

if %errorlevel% neq 0 (
    echo.
    echo !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    echo [ERROR] LA COMPILACION FALLO. REVISA EL CODIGO.
    echo !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    pause
    exit /b %errorlevel%
)

echo.
echo ==========================================
echo   EXITO: TODO LISTO PARA EJECUTAR
echo ==========================================
pause