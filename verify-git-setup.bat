@echo off
echo === GIT CONFIGURATION VERIFICATION ===
echo.
echo Checking Git user configuration:
git config user.name
git config user.email
echo.
echo === REPOSITORY STATUS ===
echo.
echo Current branch and status:
git status
echo.
echo === RECENT COMMITS ===
echo.
echo Last 5 commits:
git log --oneline -5
echo.
echo === REMOTE STATUS ===
echo.
echo Remote repository status:
git remote -v
echo.
echo Checking if commits are pushed:
git status -v
echo.
echo === SUMMARY ===
echo If you see your commits in the log above, everything worked!
echo If you see "Your branch is ahead of 'origin/main' by X commits", run: git push
pause