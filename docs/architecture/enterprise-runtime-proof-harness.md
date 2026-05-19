# Enterprise Runtime Proof Harness

Date: 2026-05-15  
Scope: Enterprise Plane residual blocker closure (runtime-evidence depth).

## Purpose

Provide a repeatable, CI-grade runtime harness for enterprise-plane operational truth:

1. verify enterprise service health for required owners (MusheX, COSTA, Coverage, GL, Procurement, HR/Payroll, Msika Flow, Experience BFF),
2. assert explicit fail-close behavior for known unwired financial shortcuts (mobile billing),
3. probe long-tail enterprise BFF surfaces for non-5xx runtime reachability.

## Harness Artifacts

- `test/integration/enterprise-fullstack-runtime.sh`
- `test/integration/enterprise-fullstack-runtime.ps1`

## Required Runtime Endpoints

Default health endpoints are:

- `EXPERIENCE_BFF_BASE_URL` -> `http://localhost:8160/actuator/health`
- `MUSHEX_BASE_URL` -> `http://localhost:8086/actuator/health`
- `COSTA_BASE_URL` -> `http://localhost:8087/actuator/health`
- `COVERAGE_BASE_URL` -> `http://localhost:8088/actuator/health`
- `GL_BASE_URL` -> `http://localhost:8089/actuator/health`
- `PROCUREMENT_BASE_URL` -> `http://localhost:8090/actuator/health`
- `HR_PAYROLL_BASE_URL` -> `http://localhost:8091/actuator/health`
- `MSIKA_FLOW_BASE_URL` -> `http://localhost:8092/actuator/health`

Override these environment variables in CI or local orchestration where ports differ.

## Assertions

1. **Health gating:** all required enterprise owners must report `status=UP`.
2. **Fail-close honesty:**  
   - `GET /internal/v1/mobile/provider/billing/charges` returns `501` + `BILLING_ROUTE_UNAVAILABLE`  
   - `POST /internal/v1/mobile/provider/billing/charge` returns `501` + `BILLING_ROUTE_UNAVAILABLE`
3. **Long-tail runtime parity reachability (non-5xx):**  
   - `GET /internal/v1/erp/procurement/suppliers`  
   - `GET /internal/v1/erp/hr/employees`  
   - `GET /internal/v1/finance/patient-accounts/{cpid}`  
   - `GET /internal/v1/finance/payment-plans?patient_cpid=...`

## Execution

Linux/macOS:

```bash
bash test/integration/enterprise-fullstack-runtime.sh
```

Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File test/integration/enterprise-fullstack-runtime.ps1
```

## Current Limitation

This harness closes the enterprise runtime-proof scaffolding blocker, but READY still requires:

1. first green CI execution evidence for this harness,
2. deeper transactional state assertions across encounter-to-charge, claim-to-remittance, and procurement-to-pay,
3. additional service-level test depth (ledger/procurement/hr-payroll).
