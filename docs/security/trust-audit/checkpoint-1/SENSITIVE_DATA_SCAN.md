# Sensitive data scan — Checkpoint 1 evidence pack

**Scope:** `docs/security/trust-audit/checkpoint-1/**`  
**Rule:** Committed evidence must contain no tokens, credentials, secrets, private keys, recovery codes, or unnecessary personal/clinical information.

## Scan method

1. Pattern search for high-risk literals: `Bearer `, `eyJ`, `BEGIN PRIVATE KEY`, `BEGIN RSA`, `password=`, `client_secret`, `recovery code`, `otp=`, raw JWTs.
2. Manual review of runtime-evidence captures (`RUNTIME_EVIDENCE.md`, `OPEN_QUESTION_ANSWERS.md`, `BYPASS_INVENTORY*.md`, `IMAGE_DIGEST_PROVENANCE.md`, `deployed-envoy.yaml`).
3. Confirm env dumps redact secret-bearing values (`(from secret)` / omitted).

## Result

| Class | Result |
|---|---|
| Access/refresh/ID tokens | **NONE found** |
| Private keys / keystores | **NONE found** |
| Recovery codes / OTPs | **NONE found** |
| Client secrets / passwords | **NONE found** (references name flags only; values redacted) |
| Unnecessary patient/clinical payloads | **NONE found** |
| Image digests / commit SHAs / flag names | Present (allowed — not credentials) |

**Confirmation:** Checkpoint 1 committed evidence is clean of secrets and unnecessary PII/clinical content as of this closure commit.
