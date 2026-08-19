@echo off
setlocal
echo =================================================================
echo  Iniciando NexusFlow via PowerShell All-in-One script...
echo =================================================================
powershell -ExecutionPolicy Bypass -File "%~dp0start.ps1"
pause
