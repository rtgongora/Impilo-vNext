# MADI Permissions

> Enforced via TSHEPO `PolicyEngine` on every BFF and domain request. Mobile and web surfaces inherit the same permission codes.

## Actor contexts

| Actor | Typical MADI actions |
|-------|---------------------|
| **Citizen (CLIENT)** | Register as donor, view own profile/history, find drives, submit feedback, update comms preferences |
| **Provider (PROVIDER)** | Create/submit blood orders, start/complete transfusions, capture drive donations, report reactions |
| **Blood bank operator** | Processing, stock, issue, central bank transfers (web ops — planned) |
| **Platform operator** | Dashboards, haemovigilance case management, audit |

## Permission codes (representative)

| Code | Description |
|------|-------------|
| `madi.donor.register` | Register self or client as donor |
| `madi.donor.read.self` | Read own donor profile and history |
| `madi.donor.screen` | Record eligibility screening |
| `madi.donor.feedback.submit` | Submit post-donation feedback |
| `madi.drive.read` | List published drives |
| `madi.drive.register` | Register attendance at drive |
| `madi.drive.capture` | Field screening and collection (provider) |
| `madi.order.create` | Create blood order |
| `madi.order.submit` | Submit order for crossmatch |
| `madi.order.issue` | Issue reserved unit |
| `madi.transfusion.start` | Start transfusion episode |
| `madi.transfusion.observe` | Record transfusion observations |
| `madi.transfusion.complete` | Complete episode |
| `madi.haemovigilance.report` | Report adverse reaction |
| `madi.haemovigilance.investigate` | Investigate/close case |
| `madi.stock.read` | View blood bank inventory |
| `madi.dashboard.read` | Programme dashboards |

## Purpose of use

| Purpose | When required |
|---------|---------------|
| `TREATMENT` | Transfusion, order for active patient |
| `DONATION` | Donor registration and drive attendance |
| `PUBLIC_HEALTH` | Aggregated dashboards and surveillance exports |
| `OPERATIONS` | Blood bank stock and central bank coordination |

## Consent

Donor registration and transfusion documentation may require Mvumo consent references. BFF returns `consentStatus` when gating applies; mobile surfaces must not bypass consent prompts.

## Audit

All MADI mutations emit audit events via outbox. Haemovigilance reports and transfusion completions are **always** audited with correlation ID chain.
