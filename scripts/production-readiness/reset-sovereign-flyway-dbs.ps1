# Reset sovereign Flyway databases after migration renumbering conflicts.
# Usage (PowerShell): .\scripts\production-readiness\reset-sovereign-flyway-dbs.ps1

$ErrorActionPreference = "Stop"
$DbContainer = if ($env:EXPERIENCE_DB_CONTAINER) { $env:EXPERIENCE_DB_CONTAINER } else { "experience-experience-db-1" }

$databases = @("surv", "impilo_nhume", "impilo_ndila")

foreach ($db in $databases) {
  Write-Host "Resetting database $db ..."
  docker exec $DbContainer psql -U impilo -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$db' AND pid <> pg_backend_pid();" | Out-Null
  docker exec $DbContainer psql -U impilo -d postgres -c "DROP DATABASE IF EXISTS `"$db`";"
  docker exec $DbContainer psql -U impilo -d postgres -c "CREATE DATABASE `"$db`" OWNER impilo;"
}

Write-Host "Sovereign Flyway databases reset. Re-run: .\tools\dev\up.ps1 -SovereignHost -Build"
