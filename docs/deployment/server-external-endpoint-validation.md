# Impilo vNext — Server External Endpoint Validation

**Date**: 2026-03-18
**Validator**: Claude Code (Principal Deployment + Runtime Validation Engineer)

---

## External Endpoint Map

| Service | Internal Port | External Validation URL | Expected Response |
|---------|---------------|-------------------------|-------------------|
| Experience UI | 3020 | http://197.221.242.150:13020 | HTML page (Next.js) |
| Envoy Gateway | 10000 | http://197.221.242.150:13021 | Envoy response |
| Keycloak | 8080 | http://197.221.242.150:13022 | Keycloak login page |
| HAPI FHIR | 8090 | http://197.221.242.150:13023/fhir | FHIR CapabilityStatement |
| TSHEPO | 8081 | http://197.221.242.150:13024/actuator/health | `{"status":"UP"}` |
| MinIO Console | 9001 | http://197.221.242.150:13025 | MinIO console page |

## Port Mapping (Internal → External)

| Internal Port | External Port | Offset |
|---------------|---------------|--------|
| 3020 | 13020 | +10000 |
| 10000 | 13021 | +3021 |
| 8080 | 13022 | +4942 |
| 8090 | 13023 | +4933 |
| 8081 | 13024 | +4943 |
| 9001 | 13025 | +4024 |

---

## Validation Results (2026-03-18)

### Status: ❌ ALL ENDPOINTS UNREACHABLE FROM THIS ENVIRONMENT

| Service | URL | Result | Details |
|---------|-----|--------|---------|
| Experience UI | :13020 | ❌ 403 | `host_not_allowed` via egress proxy |
| Envoy Gateway | :13021 | ❌ 403 | `host_not_allowed` via egress proxy |
| Keycloak | :13022 | ❌ 403 | `host_not_allowed` via egress proxy |
| HAPI FHIR | :13023/fhir | ❌ 403 | `host_not_allowed` via egress proxy |
| TSHEPO | :13024/actuator/health | ❌ 403 | `host_not_allowed` via egress proxy |
| MinIO Console | :13025 | ❌ 403 | `host_not_allowed` via egress proxy |

### Cause

All requests are intercepted by the Claude Code sandbox's egress proxy at `21.0.0.77:15004`. The proxy returns:
```
HTTP/1.1 403 Forbidden
x-deny-reason: host_not_allowed
Body: "Host not allowed"
```

### Note

These 403 responses are **NOT from the target server**. They are from the Anthropic egress proxy. The actual health of the server endpoints is **unknown** from this environment.

---

## Manual Validation Commands

To validate from a machine with direct access, run:

```bash
# Experience UI
curl -sS -o /dev/null -w "%{http_code}" http://197.221.242.150:13020

# Envoy Gateway
curl -sS -o /dev/null -w "%{http_code}" http://197.221.242.150:13021

# Keycloak
curl -sS -o /dev/null -w "%{http_code}" http://197.221.242.150:13022

# HAPI FHIR
curl -sS http://197.221.242.150:13023/fhir/metadata | jq .resourceType

# TSHEPO Health
curl -sS http://197.221.242.150:13024/actuator/health | jq .status

# MinIO Console
curl -sS -o /dev/null -w "%{http_code}" http://197.221.242.150:13025
```
