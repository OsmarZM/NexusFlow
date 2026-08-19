@echo off
setlocal
cd /d "%~dp0"
echo =================================================================
echo  Encerrando NexusFlow via PowerShell script...
echo =================================================================
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop.ps1"
pause
