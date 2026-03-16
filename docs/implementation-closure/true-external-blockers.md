# True External Blockers — Implementation Closure Wave

Generated: 2026-03-16

## Classification Criteria

A blocker is classified as BLOCKED_EXTERNAL only if:
1. It requires a running external system that cannot be created from repo code alone
2. The external system is a third-party product or cloud service
3. No reasonable in-repo substitute exists

---

## BLOCKED_EXTERNAL Items

### 1. Keycloak Realm Configuration
- **Component**: Authentication / Identity Provider
- **Blocker**: Realm JSON import file (realm clients, roles, scopes) is not committed to the repo
- **Evidence**: `docker-compose.runtime.yml` starts Keycloak but no realm auto-import
- **Impact**: Services can start but OAuth2 token validation requires a configured realm
- **Resolution**: Create and commit `infra/keycloak/impilo-realm.json` with service clients

### 2. HAPI FHIR Server
- **Component**: Clinical data repository (BUTANO writes to HAPI FHIR)
- **Blocker**: HAPI FHIR is an external Java application (not built from this repo)
- **Evidence**: `butano-fhir` service proxies to external HAPI FHIR instance
- **Impact**: FHIR resource storage/retrieval requires running HAPI FHIR server
- **Resolution**: Already configured in `docker-compose.runtime.yml` as Docker image

### 3. Orthanc PACS Server
- **Component**: Medical imaging (PACS adapter service)
- **Blocker**: Orthanc is an external DICOM/PACS server
- **Evidence**: `pacs-adapter-service` connects to external Orthanc instance
- **Impact**: DICOM image storage requires running Orthanc server
- **Resolution**: Already configured in `docker-compose.runtime.yml` as Docker image

---

## NOT External Blockers (resolved in this wave)

| Previously claimed | Resolution |
|-------------------|------------|
| "Vault integration is a stub" | Implemented real HTTP client with env fallback |
| "MinIO integration is a stub" | Implemented real S3 API with Sig V4 auth |
| "Email/SMS providers are stubs" | Implemented SMTP + HTTP SMS providers |
| "Report execution is a stub" | Implemented real SQL execution engine |
| "Network printer is a stub" | Implemented real IPP protocol |
| "EHR UI is a scaffold" | Implemented full clinical workspace |
| "Missing databases" | Added all databases to init script |
