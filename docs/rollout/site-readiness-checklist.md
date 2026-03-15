# Site Readiness Checklist — Impilo vNext

> Wave 24 Deliverable | Status: Draft | Date: 2026-03-15

## 1. Purpose

This checklist is the gating document for each site before Impilo vNext deployment begins. A site that fails any **mandatory** item may not proceed to cutover until the deficiency is remediated. Items marked **conditional** apply only to specific site tiers.

## 2. Site Classification Reference

| Tier | Type | Infrastructure Model | Connectivity Model | Pod Required |
|------|------|---------------------|-------------------|-------------|
| 1 | Provincial Hospital | Full Pod (local K8s cluster + DB) | Always-on, dual-link, federation | Yes |
| 2 | District Hospital | Full Pod (local K8s cluster + DB) | Always-on, dual-link, federation | Yes |
| 3 | Primary Health Centre | Thin client (browser-based) | Online-first + offline fallback | No (central pod) |
| 4 | Community Outpost | Mobile device only | Offline-first + periodic sync | No (edge sync) |

## 3. Connectivity Assessment

### 3.1 Mandatory — All Tiers

| # | Check | Minimum Requirement | Evidence Required | Pass |
|---|-------|---------------------|-------------------|------|
| C-01 | Primary internet link | ≥ 10 Mbps symmetric (Tier 1/2), ≥ 2 Mbps (Tier 3/4) | ISP SLA + 7-day speed test log | [ ] |
| C-02 | Link uptime | ≥ 99% monthly (Tier 1/2), ≥ 95% (Tier 3/4) | ISP uptime report or monitoring data | [ ] |
| C-03 | DNS resolution | Resolves platform domains within 50ms | `dig` test results | [ ] |
| C-04 | TLS capability | TLS 1.3 supported end-to-end | Connection test to Envoy gateway (port 10000) | [ ] |
| C-05 | Latency to central pod | ≤ 100ms RTT (Tier 1/2), ≤ 300ms (Tier 3/4) | `ping`/`mtr` trace over 24 hours | [ ] |

### 3.2 Conditional — Tier 1/2 Only

| # | Check | Minimum Requirement | Evidence Required | Pass |
|---|-------|---------------------|-------------------|------|
| C-06 | Backup internet link | ≥ 5 Mbps, different ISP or last-mile technology | ISP contract + failover test log | [ ] |
| C-07 | Automatic failover | Link failover within ≤ 30 seconds | Failover test timestamp evidence | [ ] |
| C-08 | VPN tunnel to national spine | IPsec/WireGuard tunnel established | Tunnel status + federation handshake test | [ ] |

### 3.3 Conditional — Tier 3/4 Only

| # | Check | Minimum Requirement | Evidence Required | Pass |
|---|-------|---------------------|-------------------|------|
| C-09 | Offline capability verified | Device can operate ≥ 48 hours offline | Offline workflow test report | [ ] |
| C-10 | Sync mechanism tested | CRDT reconciliation completes without conflict errors | offline-sync-service test log | [ ] |

## 4. Power Assessment

| # | Check | Minimum Requirement | Applies To | Evidence Required | Pass |
|---|-------|---------------------|-----------|-------------------|------|
| P-01 | Primary power source | Grid power or dedicated generator | All | Utility bill or generator spec sheet | [ ] |
| P-02 | UPS for server + network | ≥ 30 min runtime at full load | Tier 1/2 | UPS spec + load test report | [ ] |
| P-03 | UPS for client devices | ≥ 15 min runtime (or device battery) | Tier 3/4 | Device battery spec or UPS report | [ ] |
| P-04 | Generator (auto-start) | ≥ 8 hours fuel capacity, auto-start ≤ 15 sec | Tier 1/2 | Generator spec + test log | [ ] |
| P-05 | Surge protection | Surge protectors on all server/network equipment | Tier 1/2 | Installation photo + spec | [ ] |
| P-06 | Power stability | ≤ 2 unplanned outages per month | All | 3-month power log | [ ] |

## 5. Hardware Assessment

### 5.1 Server (Tier 1/2 Pod Sites)

| # | Check | Minimum Requirement | Evidence Required | Pass |
|---|-------|---------------------|-------------------|------|
| H-01 | CPU | ≥ 16 cores (AMD EPYC or Intel Xeon) | Hardware inventory | [ ] |
| H-02 | RAM | ≥ 64 GB ECC | Hardware inventory | [ ] |
| H-03 | Storage | ≥ 1 TB NVMe SSD (RAID-1 or better) | Storage config report | [ ] |
| H-04 | OS | Ubuntu 22.04 LTS or RHEL 9 | `cat /etc/os-release` | [ ] |
| H-05 | Container runtime | Docker 24+ or containerd 1.7+ | `docker version` or `containerd --version` | [ ] |
| H-06 | Kubernetes | K3s 1.29+ or K8s 1.29+ | `kubectl version` | [ ] |
| H-07 | Helm | Helm 3.14+ | `helm version` | [ ] |
| H-08 | PostgreSQL 16 | Running, accessible, backup configured | `psql --version` + backup job evidence | [ ] |
| H-09 | Redis 7 | Running, persistence enabled | `redis-cli info server` | [ ] |
| H-10 | Kafka (KRaft) | Running, 3-broker minimum for Tier 1 | `kafka-metadata.sh` status | [ ] |

### 5.2 Client Devices (All Tiers)

| # | Check | Minimum Requirement | Evidence Required | Pass |
|---|-------|---------------------|-------------------|------|
| H-11 | Workstations (Tier 1/2/3) | Modern browser (Chrome 120+, Edge 120+, Firefox 120+) | Browser version check | [ ] |
| H-12 | Screen resolution | ≥ 1366×768 | Device spec | [ ] |
| H-13 | Mobile devices (Tier 4) | Android 12+ with ≥ 4 GB RAM, ≥ 64 GB storage | Device model/spec | [ ] |
| H-14 | Barcode scanners (if pharmacy) | USB HID or Bluetooth, 1D+2D capable | Scanner model + test scan | [ ] |
| H-15 | Label printers (if pharmacy) | ZPL-compatible, network-attached | Printer model + test print | [ ] |
| H-16 | Card printer (if VITO card site) | Smart card printer per card-print-agent spec | Printer model + test card | [ ] |

### 5.3 Network Infrastructure

| # | Check | Minimum Requirement | Evidence Required | Pass |
|---|-------|---------------------|-------------------|------|
| H-17 | LAN switches | Managed, ≥ 1 Gbps ports | Switch model + port count | [ ] |
| H-18 | WiFi access points | Full clinical area coverage, WPA3, ≥ 802.11ac | Heat map survey | [ ] |
| H-19 | Firewall | Stateful firewall; required ports open (see port list below) | Firewall rule export | [ ] |

## 6. Required Network Ports

### 6.1 Inbound (to Pod server)

| Port | Protocol | Service | Source |
|------|----------|---------|--------|
| 10000 | HTTPS | Envoy gateway | Client devices, federation peers |
| 9901 | HTTPS | Envoy admin (restricted) | Admin workstations only |
| 443 | HTTPS | UI applications (via ingress) | Client devices |

### 6.2 Internal (Pod server ↔ services)

| Port Range | Services |
|------------|----------|
| 8080–8089 | Ring 0 kernel services |
| 8090–8099 | FHIR, pharmacy, inventory |
| 8100–8199 | Ring 1 clinical services |
| 8110–8160 | Ring 2 platform services |
| 3000–3019 | UI applications |
| 5432 | PostgreSQL |
| 6379 | Redis |
| 9092 | Kafka |

### 6.3 Outbound (Pod server → external)

| Destination | Port | Purpose |
|-------------|------|---------|
| National spine | 443 | Federation sync, TSHEPO key exchange |
| Keycloak (if centralized) | 8080/443 | Authentication |
| SMS gateway | 443 | channels-service notifications |
| eLMIS | 443 | inventory-elmis-adapter, pharmacy-elmis-adapter |
| DHIS2 | 443 | surveillance-service eIDSR reporting |

## 7. Environment Assessment

| # | Check | Minimum Requirement | Applies To | Evidence Required | Pass |
|---|-------|---------------------|-----------|-------------------|------|
| E-01 | Server room / closet | Dedicated, lockable room or rack | Tier 1/2 | Photo + access log policy | [ ] |
| E-02 | Temperature control | ≤ 27°C sustained, ventilation or AC | Tier 1/2 | Temperature monitoring data (7 days) | [ ] |
| E-03 | Fire suppression | Fire extinguisher within 5m of server equipment | Tier 1/2 | Photo evidence | [ ] |
| E-04 | Physical security | Restricted access; visitor log maintained | Tier 1/2 | Access policy document | [ ] |
| E-05 | Cable management | Labeled, organized; no trip hazards | All | Photo evidence | [ ] |

## 8. Staffing Assessment

| # | Check | Minimum Requirement | Applies To | Evidence Required | Pass |
|---|-------|---------------------|-----------|-------------------|------|
| S-01 | Designated IT contact | ≥ 1 named person with contact details | All | Name + contact details registered | [ ] |
| S-02 | Clinical champions | ≥ 2 staff who completed Fundo-600 | All | Fundo LMS completion certificates | [ ] |
| S-03 | Facility manager trained | Completed Fundo-300 | All | Fundo LMS certificate | [ ] |
| S-04 | IT staff trained | Completed Fundo-500 (Tier 1/2) | Tier 1/2 | Fundo LMS certificate | [ ] |
| S-05 | Staff roster for go-live | On-call roster for cutover day + T+3 | All | Published roster | [ ] |

## 9. Data Readiness

| # | Check | Requirement | Evidence Required | Pass |
|---|-------|-------------|-------------------|------|
| D-01 | Facility registered in TUSO | Facility code, name, GPS coordinates, tier, parent hierarchy | TUSO API query result | [ ] |
| D-02 | Providers registered in VARAPI | All clinical staff with licensure data | VARAPI record count | [ ] |
| D-03 | Terminology packs loaded (ZIBO) | ICD-10, LOINC, SNOMED-CT subsets for site's clinical scope | ZIBO pack manifest | [ ] |
| D-04 | Formulary loaded (MSIKA) | Drug catalog for site's dispensary | MSIKA product count | [ ] |
| D-05 | Legacy data migration plan | Defined scope, mapping, validation criteria | Migration plan document | [ ] |
| D-06 | Test patients created | ≥ 5 test patient records for end-to-end workflow verification | Test patient CPID list | [ ] |

## 10. Security Assessment

| # | Check | Requirement | Evidence Required | Pass |
|---|-------|-------------|-------------------|------|
| X-01 | TSHEPO trust chain | Site registered in TSHEPO; signing keys provisioned via tshepo-keys-service | Key provisioning log | [ ] |
| X-02 | Keycloak realm configured | Site realm with roles, client IDs, redirect URIs | Keycloak admin export | [ ] |
| X-03 | TLS certificates | Valid certificates for all exposed endpoints | Certificate expiry check | [ ] |
| X-04 | Envoy ext_authz | Authorization calls succeed for test users | ext_authz test log | [ ] |
| X-05 | Audit chain | tshepo-audit-service recording events with SHA-256 hash chain | Audit query showing chain integrity | [ ] |
| X-06 | Consent default | Default consent policy loaded in tshepo-consent-service | Consent policy list | [ ] |

## 11. Assessment Outcome

### Scoring

| Result | Criteria |
|--------|----------|
| **PASS** | All mandatory items for the site's tier pass |
| **CONDITIONAL PASS** | ≤ 2 non-critical items pending with remediation plan and date |
| **FAIL** | Any mandatory item fails; site re-queued after remediation |

### Sign-Off

| Role | Name | Date | Result |
|------|------|------|--------|
| Site Assessment Lead | _________________ | _______ | PASS / CONDITIONAL / FAIL |
| Infrastructure Lead | _________________ | _______ | PASS / CONDITIONAL / FAIL |
| Security Lead | _________________ | _______ | PASS / CONDITIONAL / FAIL |
| Rollout Program Lead | _________________ | _______ | PASS / CONDITIONAL / FAIL |

### Remediation Tracking (if Conditional Pass)

| Item # | Deficiency | Remediation Action | Owner | Due Date | Status |
|--------|-----------|-------------------|-------|----------|--------|
| | | | | | |
