@echo off
echo Setting up Git user configuration...
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"
echo.
echo Current Git configuration:
git config --list | findstr user
echo.
echo Git user setup complete. You can now commit your changes.
pause