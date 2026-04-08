@echo off
cd "c:\Users\rgong\OneDrive\Documents\EHR\vNext\Repo\Impilo-vNext"
echo Checking git status...
git status
echo.
echo Checking recent commits...
git log --oneline -5
echo.
echo Checking if changes are staged...
git diff --cached --name-only
echo.
echo Checking if there are unstaged changes...
git diff --name-only
echo.
pause