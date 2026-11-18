@echo off
setlocal
pushd "%~dp0.."
uv run --no-project --python 3.12 --with pillow tools/assembly_editor.py
set "editor_exit=%ERRORLEVEL%"
popd
if not "%editor_exit%"=="0" pause
exit /b %editor_exit%
