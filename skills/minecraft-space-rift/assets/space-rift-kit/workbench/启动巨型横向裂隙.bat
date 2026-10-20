@echo off
chcp 65001 >nul
cd /d "%~dp0"
python serve.py --page index.html
pause
