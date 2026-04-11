# Impilo service index (generated)

**Registry version:** `1`  
**Generated:** 2026-04-11T16:15:58 UTC  
**Source:** [`services-registry.yaml`](./services-registry.yaml)  

Regenerate:

```bash
cd scripts/registry && npm install && npm run generate
```

---

## Libraries (Maven reactor, not deployable)

| ID | Maven module | Path |
|----|--------------|------|
| `contract-tests` | `contract-tests` | `libs/contract-tests` |
| `federation-connector` | `federation-connector` | `libs/federation-connector` |
| `offline-sdk` | `offline-sdk` | `libs/offline-sdk` |
| `ops-instrumentation` | `ops-instrumentation` | `libs/ops-instrumentation` |
| `security-baseline` | `security-baseline` | `libs/security-baseline` |
| `shared-core` | `shared-core` | `services/shared-core` |
| `shared-kernel-java` | `shared-kernel-java` | `libs/shared-kernel-java` |
| `tech-companion` | `tech-companion` | `libs/tech-companion` |
| `tech-companion-harness` | `tech-companion-harness` | `libs/tech-companion-harness` |
| `tech-companion-mock` | `tech-companion-mock` | `libs/tech-companion-mock` |
| `tshepo-contracts` | `tshepo-contracts` | `libs/tshepo-contracts` |
| `tshepo-sdk` | `tshepo-sdk` | `libs/tshepo-sdk` |

---

## Services & agents

| Maven module | ID | Plane | Sovereign | Group | Protocol | HTTP port | Product names |
|--------------|-----|-------|-----------|-------|----------|-----------|---------------|
| `asset-registry-service` | `asset-registry-service` | ops | — | — | rest | 8310 | Asset Registry |
| `audit-ledger-service` | `audit-ledger-service` | ops | — | — | rest | 8350 | Audit Ledger |
| `butano-fhir` | `butano-fhir` | clinical | yes | BUTANO | fhir | 8289 | BUTANO FHIR |
| `butano-service` | `butano-service` | clinical | yes | BUTANO | fhir | 8090 | BUTANO, HAPI SHR |
| `campaigns-service` | `campaigns-service` | data | — | — | rest | 8190 | Campaigns |
| `card-print-agent` | `card-print-agent` | integration | — | — | rest | 8291 | Card Print Agent |
| `channels-service` | `channels-service` | integration | — | — | rest | 8130 | Channels |
| `clinical-knowledge-platform-service` | `clinical-knowledge-platform-service` | knowledge | — | — | rest | 8270 | Clinical Knowledge Platform |
| `connector-fhir-adapter` | `connector-fhir-adapter` | integration | — | — | fhir | 8151 | Connector FHIR |
| `costing-engine-service` | `costing-engine-service` | finance | — | — | rest | 8101 | COSTA |
| `coverage-service` | `coverage-service` | finance | — | — | rest | 8140 | Coverage |
| `credential-verification-service` | `credential-verification-service` | finance | — | — | rest | 8094 | Credential Verification |
| `data-access-governance-service` | `data-access-governance-service` | data | — | — | rest | 8170 | DAGS |
| `data-governance-service` | `data-governance-service` | data | — | — | rest | 8220 | Data Governance |
| `data-ingestion-service` | `data-ingestion-service` | data | — | — | rest | 8210 | Data Ingestion |
| `data-pipeline-service` | `data-pipeline-service` | data | — | — | rest | 8215 | Data Pipeline |
| `data-warehouse-service` | `data-warehouse-service` | data | — | — | rest | 8233 | Data Warehouse |
| `developer-portal-service` | `developer-portal-service` | ops | — | — | rest | 8370 | Developer Portal |
| `dispatch-service` | `dispatch-service` | ops | — | — | rest | 8320 | Dispatch |
| `document-service` | `document-service` | clinical | — | — | rest | 8093 | Document Store |
| `experience-bff` | `experience-bff` | experience | — | — | rest | 8160 | Experience BFF |
| `fhir-gateway-service` | `fhir-gateway-service` | clinical | yes | BUTANO | fhir | 8091 | FHIR Gateway |
| `forms-service` | `forms-service` | knowledge | — | — | rest | 8240 | Forms |
| `guidance-service` | `guidance-service` | knowledge | — | — | rest | 8260 | Guidance |
| `identity-assurance-service` | `identity-assurance-service` | trust | — | — | rest | 8201 | Identity Assurance |
| `indawo-service` | `indawo-service` | registry | — | — | rest | 8150 | INDAWO |
| `inpatient-service` | `inpatient-service` | clinical | — | — | rest | 8121 | Inpatient |
| `integration-hub` | `integration-hub` | integration | — | — | rest | 8110 | Integration Hub |
| `inventory-elmis-adapter` | `inventory-elmis-adapter` | clinical | — | — | rest | 8108 | Inventory eLMIS |
| `inventory-service` | `inventory-service` | clinical | — | — | rest | 8098 | Inventory |
| `iot-ingestion-service` | `iot-ingestion-service` | ops | — | — | rest | 8330 | IoT Ingestion |
| `jobs-service` | `jobs-service` | integration | — | — | rest | 8109 | Jobs |
| `landela-adapter-service` | `landela-adapter-service` | integration | — | — | rest | 8092 | Landela |
| `msika-flow-service` | `msika-flow-service` | marketplace | — | — | rest | 8100 | Msika Flow |
| `msika-service` | `msika-service` | registry | yes | MSIKA | rest | 8086 | MSIKA |
| `mushex-service` | `mushex-service` | finance | yes | MUSheX | rest | 8102 | MUSheX |
| `national-data-repository-service` | `national-data-repository-service` | data | — | — | rest | 8152 | National Data Repository |
| `ndr-service` | `ndr-service` | data | — | — | rest | 8232 | NDR |
| `notification-service` | `notification-service` | integration | — | — | rest | 8200 | Notification |
| `observability-service` | `observability-service` | ops | — | — | rest | 8211 | Observability |
| `offline-edge-service` | `offline-edge-service` | ops | — | — | rest | 8360 | Offline Edge |
| `offline-sync-service` | `offline-sync-service` | integration | — | — | rest | 8095 | Offline Sync |
| `oros-service` | `oros-service` | clinical | — | — | rest | 8089 | OROS |
| `pacs-adapter-service` | `pacs-adapter-service` | clinical | — | — | rest | 8113 | PACS Adapter |
| `pct-service` | `pct-service` | clinical | — | — | rest | 8088 | PCT |
| `pharmacy-elmis-adapter` | `pharmacy-elmis-adapter` | clinical | — | — | rest | 8099 | Pharmacy eLMIS |
| `pharmacy-service` | `pharmacy-service` | clinical | — | — | rest | 8096 | Pharmacy |
| `product-registry-service` | `product-registry-service` | registry | — | — | rest | 8097 | Product Registry |
| `reporting-service` | `reporting-service` | data | — | — | rest | 8176 | Reporting |
| `rules-service` | `rules-service` | knowledge | — | — | rest | 8241 | Rules |
| `schema-registry-service` | `schema-registry-service` | ops | — | — | rest | 8371 | Schema Registry |
| `search-service` | `search-service` | knowledge | — | — | rest | 8230 | Search |
| `security-hardening-service` | `security-hardening-service` | ops | — | — | rest | 8221 | Security Hardening |
| `share-slip-service` | `share-slip-service` | finance | — | — | rest | 8104 | Share Slip |
| `support-service` | `support-service` | ops | — | — | rest | 8340 | Support |
| `surveillance-service` | `surveillance-service` | data | — | — | rest | 8180 | Surveillance |
| `tshepo-audit-service` | `tshepo-audit-service` | trust | yes | TSHEPO | rest | 8183 | TSHEPO Audit |
| `tshepo-authz-service` | `tshepo-authz-service` | trust | yes | TSHEPO | mixed | 8081 | TSHEPO Authz |
| `tshepo-consent-service` | `tshepo-consent-service` | trust | yes | TSHEPO | rest | 8182 | TSHEPO Consent |
| `tshepo-identity-service` | `tshepo-identity-service` | trust | yes | TSHEPO | rest | 8181 | TSHEPO Identity |
| `tshepo-keys-service` | `tshepo-keys-service` | trust | yes | TSHEPO | rest | 8184 | TSHEPO Keys |
| `tshepo-offline-service` | `tshepo-offline-service` | trust | yes | TSHEPO | rest | 8185 | TSHEPO Offline |
| `tshepo-service` | `tshepo-service` | trust | yes | TSHEPO | rest | 8079 | TSHEPO, legacy monolith |
| `tuso-service` | `tuso-service` | registry | yes | TUSO | rest | 8084 | TUSO |
| `ubomi-service` | `ubomi-service` | registry | yes | UBOMI | rest | 8087 | UBOMI |
| `varapi-service` | `varapi-service` | registry | yes | VARAPI | rest | 8083 | VARAPI |
| `vito-service` | `vito-service` | registry | yes | VITO | rest | 8082 | VITO |
| `workflow-service` | `workflow-service` | integration | — | — | rest | 8250 | Workflow |
| `zibo-service` | `zibo-service` | registry | yes | ZIBO | rest | 8085 | ZIBO |

---

*Sovereign* marks membership in the nine national dual-mode boundaries (see `TODO.md` / doctrine). *Group* is the logical sovereign name when one product spans multiple modules.
