@echo off
rem stand up - create a desktop shortcut that points at this copy of the app.
rem Keep this file in the same folder as "stand up.exe" and double-click it once.
setlocal
set "STANDUP_EXE=%~dp0stand up.exe"
set "STANDUP_DIR=%~dp0"
if not exist "%STANDUP_EXE%" (
  echo [stand up] Could not find "stand up.exe" next to this file.
  echo [stand up] Keep this .bat in the same folder as the .exe and run it again.
  echo.
  pause
  exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -Command "$w = New-Object -ComObject WScript.Shell; $p = Join-Path ([Environment]::GetFolderPath('Desktop')) 'stand up.lnk'; $s = $w.CreateShortcut($p); $s.TargetPath = $env:STANDUP_EXE; $s.WorkingDirectory = $env:STANDUP_DIR; $s.IconLocation = $env:STANDUP_EXE + ',0'; $s.Description = 'stand up - take a break and walk'; $s.Save()"
if errorlevel 1 (
  echo [stand up] Could not create the shortcut. See the message above.
  echo.
  pause
  exit /b 1
)
echo [stand up] Desktop shortcut created: "stand up"
echo [stand up] You can now start the app from your desktop icon.
echo.
pause
endlocal
