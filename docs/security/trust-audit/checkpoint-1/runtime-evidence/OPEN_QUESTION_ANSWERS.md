# Runtime answers to source-audit open questions
Captured: 2026-08-01T08:45:19Z

## tshepo-authz-service
  IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=false
  SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://impilo.mohcc.gov.zw/realms/impilo
  TSHEPO_CONSENT_URL=http://tshepo-consent-service:8182
  TSHEPO_WORK_CONTEXT_MODE=SHADOW
## tshepo-identity-service
  IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true
## experience-bff
  AUTH_FALLBACK_ENABLED=false
  CONSENT_SERVICE_BASE_URL=http://tshepo-consent-service:8182
  IMPILO_SECURITY_ALLOW_ANONYMOUS=false
  KEYCLOAK_BACKEND_CLIENT_ID=impilo-backend
  KEYCLOAK_BACKEND_SECRET=<from secret>
  KEYCLOAK_INTERNAL_ISSUER=http://keycloak:8080/realms/impilo
  KEYCLOAK_ISSUER_URI=https://impilo.mohcc.gov.zw/realms/impilo
## fhir-gateway-service
  CONSENT_SERVICE_BASE_URL=http://tshepo-consent-service:8182
  IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true
## mvumo-service
  IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true
## tshepo-consent-service
  IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true

## fhir-gateway-service present?
fhir-gateway-service   1/1   1     1     15d
## envoy extAuthz helm value (from live configmap already captured)
0

## PolicyEngine/OPA follow-up 2026-08-01T08:46:37Z
- OPA workloads in impilo-full-preview: none
- Confidentiality / OPA env on tshepo-authz-service (see above capture)
env hits: {'IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS': 'false', 'TSHEPO_WORK_CONTEXT_MODE': 'SHADOW'}


## Audit/recovery follow-up
- Keycloak event ingestion: IMPILO_KEYCLOAK_EVENTS_ENABLED=true on tshepo-audit-service (MFA cohort)
- Recovery-code AAL2 defect: confirmed in source; runtime AMR decode still open

## BFF/headers follow-up
- Traefik→BFF confirmed; Envoy off path
- Web session enabled; Actor-ID overridden at BFF; Assurance-Level and Provider-ID still client-supplied on preview path
- JWT audience validation ABSENT

