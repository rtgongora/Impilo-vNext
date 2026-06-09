# wellness-service — deprecated module

**Do not deploy.** Simba (`simba-service`, port **8125**) is the single wellness product runtime.

This Maven module remains in the repo only until the sunset date (`2026-12-31`) for reference during
code archaeology. All active development, compose, preview, and BFF proxy traffic targets
`simba-service`.

Migrated into Simba (2026-06):

- Health Connect `connect/v1`
- Citizen My Life `/internal/v1/mobile/citizen/**`
- Monitoring device ingest
- Public-schema JDBC tables (`V004__citizen_mylife_health_connect.sql` on simba DB)
