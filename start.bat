@echo off
setlocal
cd /d "%~dp0"
echo =================================================================
echo  Iniciando NexusFlow via PowerShell All-in-One script...
echo =================================================================
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1"
if %ERRORLEVEL% neq 0 (
    echo.
    echo Ocorreu um erro durante a execucao.
)
pause
