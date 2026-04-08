@echo off
echo Setting up Git user configuration for Robert Tawanda Gongora...
git config --global user.name "Robert Tawanda Gongora"
git config --global user.email "admin@impilo.io"
echo.
echo Git configuration set successfully!
echo.
echo Verifying configuration:
git config user.name
git config user.email
echo.
echo You can now commit your changes. Run these commands:
echo git add .
echo git commit -m "fix: resolve H2 test compatibility issues"
echo git push
pause