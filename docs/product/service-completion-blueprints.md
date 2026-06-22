# Service Completion Blueprints

> Generated: 2026-06-20T13:50:43.992Z
> End-to-end product expectation per service for mature vNext.

Each blueprint defines personas, workflows, CRUD, UI minimum, production UI, and tests required.

## ai-model-registry-service

**Product names:** Ai Model Registry
**Plane/domain:** data / intelligence
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## analytics-pipeline-service

**Product names:** Analytics Pipeline
**Plane/domain:** integration / platform-ops
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## asset-registry-service

**Product names:** Asset Registry
**Plane/domain:** integration / platform-ops
**Current status:** real

### Primary personas
- Operators and domain users for platform-ops plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Asset Registry records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## audit-ledger-service

**Product names:** Audit Ledger
**Plane/domain:** integration / platform-ops
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## booking-service

**Product names:** Booking
**Plane/domain:** experience / workflow-orchestration
**Current status:** real

### Primary personas
- Operators and domain users for workflow-orchestration plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Booking records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity **required**
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## butano-fhir

**Product names:** BUTANO FHIR
**Plane/domain:** clinical / care-delivery
**Current status:** real

### Primary personas
- Operators and domain users for care-delivery plane capabilities
- Sovereign boundary: **BUTANO**

### Main workflows
- List/search BUTANO FHIR records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## butano-service

**Product names:** BUTANO, HAPI SHR
**Plane/domain:** clinical / care-delivery
**Current status:** real

### Primary personas
- Operators and domain users for care-delivery plane capabilities
- Sovereign boundary: **BUTANO**

### Main workflows
- List/search BUTANO records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## campaigns-service

**Product names:** Campaigns
**Plane/domain:** data / public-health-campaigns
**Current status:** real

### Primary personas
- Operators and domain users for public-health-campaigns plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Campaigns records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity **required**
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## card-print-agent

**Product names:** Card Print Agent
**Plane/domain:** integration / interoperability
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## channels-service

**Product names:** Channels
**Plane/domain:** integration / interoperability
**Current status:** real

### Primary personas
- Operators and domain users for interoperability plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Channels records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## clinical-knowledge-platform-service

**Product names:** Clinical Knowledge Platform
**Plane/domain:** clinical / clinical-knowledge
**Current status:** real

### Primary personas
- Operators and domain users for clinical-knowledge plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Clinical Knowledge Platform records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## community-service

**Product names:** Community
**Plane/domain:** experience / workflow-orchestration
**Current status:** real

### Primary personas
- Operators and domain users for workflow-orchestration plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Community records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity **required**
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## connector-fhir-adapter

**Product names:** Connector FHIR
**Plane/domain:** integration / interoperability
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## costing-engine-service

**Product names:** COSTA
**Plane/domain:** enterprise / finance
**Current status:** real

### Primary personas
- Operators and domain users for finance plane capabilities
- Standard governed service consumer

### Main workflows
- List/search COSTA records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## coverage-service

**Product names:** Coverage
**Plane/domain:** enterprise / finance
**Current status:** real

### Primary personas
- Operators and domain users for finance plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Coverage records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity **required**
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## credential-verification-service

**Product names:** Credential Verification
**Plane/domain:** enterprise / finance
**Current status:** real

### Primary personas
- Operators and domain users for finance plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Credential Verification records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## data-access-governance-service

**Product names:** DAGS
**Plane/domain:** data / intelligence
**Current status:** real

### Primary personas
- Operators and domain users for intelligence plane capabilities
- Standard governed service consumer

### Main workflows
- List/search DAGS records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## data-governance-service

**Product names:** Data Governance
**Plane/domain:** data / intelligence
**Current status:** real

### Primary personas
- Operators and domain users for intelligence plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Data Governance records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## data-ingestion-service

**Product names:** Data Ingestion
**Plane/domain:** data / intelligence
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## data-pipeline-service

**Product names:** Data Pipeline
**Plane/domain:** data / intelligence
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## data-warehouse-service

**Product names:** Data Warehouse
**Plane/domain:** data / intelligence
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## developer-portal-service

**Product names:** Developer Portal
**Plane/domain:** integration / platform-ops
**Current status:** real

### Primary personas
- Operators and domain users for platform-ops plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Developer Portal records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## dispatch-service

**Product names:** Dispatch
**Plane/domain:** integration / platform-ops
**Current status:** real

### Primary personas
- Operators and domain users for platform-ops plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Dispatch records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## document-service

**Product names:** Document Store
**Plane/domain:** clinical / care-delivery
**Current status:** real

### Primary personas
- Operators and domain users for care-delivery plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Document Store records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## experience-bff

**Product names:** Experience BFF
**Plane/domain:** experience / workflow-orchestration
**Current status:** real

### Primary personas
- Operators and domain users for workflow-orchestration plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Experience BFF records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## fhir-gateway-service

**Product names:** FHIR Gateway
**Plane/domain:** clinical / care-delivery
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## forms-service

**Product names:** Forms
**Plane/domain:** clinical / clinical-knowledge
**Current status:** real

### Primary personas
- Operators and domain users for clinical-knowledge plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Forms records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## general-ledger-service

**Product names:** General Ledger
**Plane/domain:** enterprise / enterprise-resource
**Current status:** real

### Primary personas
- Operators and domain users for enterprise-resource plane capabilities
- Standard governed service consumer

### Main workflows
- List/search General Ledger records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## guidance-service

**Product names:** Guidance
**Plane/domain:** clinical / clinical-knowledge
**Current status:** real

### Primary personas
- Operators and domain users for clinical-knowledge plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Guidance records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## hr-payroll-service

**Product names:** Hr Payroll
**Plane/domain:** enterprise / enterprise-resource
**Current status:** real

### Primary personas
- Operators and domain users for enterprise-resource plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Hr Payroll records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## identity-assurance-service

**Product names:** Identity Assurance
**Plane/domain:** trust / identity-governance
**Current status:** real

### Primary personas
- Operators and domain users for identity-governance plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Identity Assurance records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## indawo-service

**Product names:** INDAWO
**Plane/domain:** registry / registry-spine
**Current status:** real

### Primary personas
- Operators and domain users for registry-spine plane capabilities
- Standard governed service consumer

### Main workflows
- List/search INDAWO records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## inpatient-service

**Product names:** Inpatient
**Plane/domain:** clinical / care-delivery
**Current status:** real

### Primary personas
- Operators and domain users for care-delivery plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Inpatient records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## integration-hub

**Product names:** Integration Hub
**Plane/domain:** integration / interoperability
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## inventory-elmis-adapter

**Product names:** Inventory eLMIS
**Plane/domain:** clinical / care-delivery
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## inventory-service

**Product names:** Inventory
**Plane/domain:** clinical / care-delivery
**Current status:** real

### Primary personas
- Operators and domain users for care-delivery plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Inventory records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## iot-ingestion-service

**Product names:** IoT Ingestion
**Plane/domain:** integration / platform-ops
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## jobs-service

**Product names:** Jobs
**Plane/domain:** integration / interoperability
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## landela-adapter-service

**Product names:** Landela
**Plane/domain:** integration / interoperability
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## learning-service

**Product names:** Learning
**Plane/domain:** experience / workflow-orchestration
**Current status:** real

### Primary personas
- Operators and domain users for workflow-orchestration plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Learning records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity **required**
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## live-service

**Product names:** Impilo Live
**Plane/domain:** experience / live-events-broadcast
**Current status:** real

### Primary personas
- Operators and domain users for live-events-broadcast plane capabilities
- Sovereign boundary: **LIVE**

### Main workflows
- List/search Impilo Live records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity **required**
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## llm-orchestration-service

**Product names:** Llm Orchestration
**Plane/domain:** integration / platform-ops
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## madi-service

**Product names:** Madi
**Plane/domain:** clinical / platform-ops
**Current status:** real

### Primary personas
- Operators and domain users for platform-ops plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Madi records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## msika-apps-service

**Product names:** Msika Apps
**Plane/domain:** enterprise / marketplace
**Current status:** real

### Primary personas
- Operators and domain users for marketplace plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Msika Apps records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## msika-flow-service

**Product names:** Msika Flow
**Plane/domain:** enterprise / marketplace
**Current status:** real

### Primary personas
- Operators and domain users for marketplace plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Msika Flow records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## msika-service

**Product names:** MSIKA
**Plane/domain:** enterprise / marketplace
**Current status:** real

### Primary personas
- Operators and domain users for marketplace plane capabilities
- Sovereign boundary: **MSIKA**

### Main workflows
- List/search MSIKA records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## mushe-wallet-service

**Product names:** Mushe Wallet
**Plane/domain:** enterprise / finance
**Current status:** real

### Primary personas
- Operators and domain users for finance plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Mushe Wallet records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## mushex-service

**Product names:** MUSheX
**Plane/domain:** enterprise / finance
**Current status:** real

### Primary personas
- Operators and domain users for finance plane capabilities
- Sovereign boundary: **MUSheX**

### Main workflows
- List/search MUSheX records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## mvumo-service

**Product names:** Mvumo
**Plane/domain:** trust / identity-governance
**Current status:** real

### Primary personas
- Operators and domain users for identity-governance plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Mvumo records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## national-data-repository-service

**Product names:** National Data Repository
**Plane/domain:** data / intelligence
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## ndila-service

**Product names:** Ndila
**Plane/domain:** integration / interoperability
**Current status:** real

### Primary personas
- Operators and domain users for interoperability plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Ndila records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## ndr-service

**Product names:** NDR
**Plane/domain:** data / intelligence
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## nhume-service

**Product names:** Nhume
**Plane/domain:** integration / interoperability
**Current status:** real

### Primary personas
- Operators and domain users for interoperability plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Nhume records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## notification-service

**Product names:** Notification
**Plane/domain:** integration / interoperability
**Current status:** real

### Primary personas
- Operators and domain users for interoperability plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Notification records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity **required**
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## observability-service

**Product names:** Observability
**Plane/domain:** integration / platform-ops
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## offline-edge-service

**Product names:** Offline Edge
**Plane/domain:** integration / platform-ops
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## offline-sync-service

**Product names:** Offline Sync
**Plane/domain:** integration / interoperability
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## oros-service

**Product names:** OROS
**Plane/domain:** clinical / care-delivery
**Current status:** real

### Primary personas
- Operators and domain users for care-delivery plane capabilities
- Standard governed service consumer

### Main workflows
- List/search OROS records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity **required**
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## pacs-adapter-service

**Product names:** PACS Adapter
**Plane/domain:** clinical / care-delivery
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## pct-service

**Product names:** PCT
**Plane/domain:** clinical / care-delivery
**Current status:** real

### Primary personas
- Operators and domain users for care-delivery plane capabilities
- Standard governed service consumer

### Main workflows
- List/search PCT records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity **required**
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## pharmacy-elmis-adapter

**Product names:** Pharmacy eLMIS
**Plane/domain:** clinical / care-delivery
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## pharmacy-service

**Product names:** Pharmacy
**Plane/domain:** clinical / care-delivery
**Current status:** real

### Primary personas
- Operators and domain users for care-delivery plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Pharmacy records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity **required**
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## procurement-service

**Product names:** Procurement
**Plane/domain:** enterprise / enterprise-resource
**Current status:** real

### Primary personas
- Operators and domain users for enterprise-resource plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Procurement records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## product-registry-service

**Product names:** Product Registry
**Plane/domain:** registry / registry-spine
**Current status:** real

### Primary personas
- Operators and domain users for registry-spine plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Product Registry records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## referral-service

**Product names:** Referral
**Plane/domain:** integration / platform-ops
**Current status:** real

### Primary personas
- Operators and domain users for platform-ops plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Referral records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## reporting-service

**Product names:** Reporting
**Plane/domain:** data / intelligence
**Current status:** real

### Primary personas
- Operators and domain users for intelligence plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Reporting records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## rtc-gateway-service

**Product names:** Rtc Gateway
**Plane/domain:** integration / platform-ops
**Current status:** real

### Primary personas
- Operators and domain users for platform-ops plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Rtc Gateway records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## rules-service

**Product names:** Rules
**Plane/domain:** clinical / clinical-knowledge
**Current status:** real

### Primary personas
- Operators and domain users for clinical-knowledge plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Rules records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## scheduling-service

**Product names:** Scheduling
**Plane/domain:** clinical / care-delivery
**Current status:** real

### Primary personas
- Operators and domain users for care-delivery plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Scheduling records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## schema-registry-service

**Product names:** Schema Registry
**Plane/domain:** integration / platform-ops
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## search-service

**Product names:** Search
**Plane/domain:** data / intelligence
**Current status:** real

### Primary personas
- Operators and domain users for intelligence plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Search records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## security-hardening-service

**Product names:** Security Hardening
**Plane/domain:** integration / platform-ops
**Current status:** internal-only

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## share-slip-service

**Product names:** Share Slip
**Plane/domain:** enterprise / finance
**Current status:** real

### Primary personas
- Operators and domain users for finance plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Share Slip records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## simba-service

**Product names:** Simba
**Plane/domain:** enterprise / wellness-personal-health-data
**Current status:** real

### Primary personas
- Operators and domain users for wellness-personal-health-data plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Simba records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity **required**
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## support-service

**Product names:** Support
**Plane/domain:** integration / platform-ops
**Current status:** real

### Primary personas
- Operators and domain users for platform-ops plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Support records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## surveillance-service

**Product names:** Surveillance
**Plane/domain:** data / public-health-surveillance
**Current status:** real

### Primary personas
- Operators and domain users for public-health-surveillance plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Surveillance records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity **required**
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## tshepo-audit-service

**Product names:** TSHEPO Audit
**Plane/domain:** trust / identity-governance
**Current status:** real

### Primary personas
- Operators and domain users for identity-governance plane capabilities
- Sovereign boundary: **TSHEPO**

### Main workflows
- List/search TSHEPO Audit records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## tshepo-authz-service

**Product names:** TSHEPO Authz
**Plane/domain:** trust / identity-governance
**Current status:** real

### Primary personas
- Operators and domain users for identity-governance plane capabilities
- Sovereign boundary: **TSHEPO**

### Main workflows
- List/search TSHEPO Authz records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## tshepo-consent-service

**Product names:** TSHEPO Consent
**Plane/domain:** trust / identity-governance
**Current status:** real

### Primary personas
- Operators and domain users for identity-governance plane capabilities
- Sovereign boundary: **TSHEPO**

### Main workflows
- List/search TSHEPO Consent records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## tshepo-identity-service

**Product names:** TSHEPO Identity
**Plane/domain:** trust / identity-governance
**Current status:** real

### Primary personas
- Operators and domain users for identity-governance plane capabilities
- Sovereign boundary: **TSHEPO**

### Main workflows
- List/search TSHEPO Identity records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## tshepo-keys-service

**Product names:** TSHEPO Keys
**Plane/domain:** trust / identity-governance
**Current status:** real

### Primary personas
- Operators and domain users for identity-governance plane capabilities
- Sovereign boundary: **TSHEPO**

### Main workflows
- List/search TSHEPO Keys records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## tshepo-offline-service

**Product names:** TSHEPO Offline
**Plane/domain:** trust / identity-governance
**Current status:** real

### Primary personas
- Operators and domain users for identity-governance plane capabilities
- Sovereign boundary: **TSHEPO**

### Main workflows
- List/search TSHEPO Offline records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## tshepo-service

**Product names:** TSHEPO, legacy monolith
**Plane/domain:** trust / identity-governance
**Current status:** real

### Primary personas
- Operators and domain users for identity-governance plane capabilities
- Sovereign boundary: **TSHEPO**

### Main workflows
- List/search TSHEPO records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## tuso-service

**Product names:** TUSO
**Plane/domain:** registry / registry-spine
**Current status:** real

### Primary personas
- Operators and domain users for registry-spine plane capabilities
- Sovereign boundary: **TUSO**

### Main workflows
- List/search TUSO records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## ubomi-service

**Product names:** UBOMI
**Plane/domain:** registry / registry-spine
**Current status:** real

### Primary personas
- Operators and domain users for registry-spine plane capabilities
- Sovereign boundary: **UBOMI**

### Main workflows
- List/search UBOMI records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## varapi-service

**Product names:** VARAPI
**Plane/domain:** registry / registry-spine
**Current status:** real

### Primary personas
- Operators and domain users for registry-spine plane capabilities
- Sovereign boundary: **VARAPI**

### Main workflows
- List/search VARAPI records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## vito-service

**Product names:** VITO
**Plane/domain:** registry / registry-spine
**Current status:** real

### Primary personas
- Operators and domain users for registry-spine plane capabilities
- Sovereign boundary: **VITO**

### Main workflows
- List/search VITO records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity **required**
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## wellness-service

**Product names:** Wellness
**Plane/domain:** enterprise / wellness-compatibility-alias
**Current status:** deprecated

- **Classification:** Internal-only platform service
- **Primary users:** Platform operators, integration engineers, SRE
- **Minimum viable surface:** Admin/ops API + documented internal-only rationale
- **Production complete:** Contract + implementation + observability + runbook
- **Tests:** Contract IT + smoke for primary endpoints

## workflow-service

**Product names:** Workflow
**Plane/domain:** integration / interoperability
**Current status:** real

### Primary personas
- Operators and domain users for interoperability plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Workflow records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## workforce-governance-service

**Product names:** Workforce Governance
**Plane/domain:** enterprise / workforce-operations
**Current status:** real

### Primary personas
- Operators and domain users for workforce-operations plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Workforce Governance records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## vashandi-workforce-service

**Product names:** Vashandi
**Plane/domain:** enterprise / workforce-operations
**Current status:** real

### Primary personas
- Operators and domain users for workforce-operations plane capabilities
- Standard governed service consumer

### Main workflows
- List/search Vashandi records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

## zibo-service

**Product names:** ZIBO
**Plane/domain:** registry / terminology
**Current status:** real

### Primary personas
- Operators and domain users for terminology plane capabilities
- Sovereign boundary: **ZIBO**

### Main workflows
- List/search ZIBO records
- Create and update governed transactions with TSHEPO authz
- Detail view with audit trail and status transitions where applicable

### Minimum viable complete UI
- List + detail routes backed by real BFF hooks
- Empty/loading/error states with honest maturity labels

### Production-grade complete UI
- Full CRUD where domain permits; search/filter; role-based visibility
- Mobile parity where user-facing
- Cross-service handoffs documented in core-transaction journey maps

### Tests required
- Backend: `*IT.java` or controller tests for primary workflows
- BFF: proxy/controller test for each exposed route family
- UI: vitest hook/page test + Playwright e2e for critical path

