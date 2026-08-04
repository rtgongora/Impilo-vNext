**Version 2.0 - Controlled Successor Draft**

**Status: Supersedes vNext V3 v1.2 as a controlling architecture; subordinate to Target Architecture v1.3.2; controlled successor draft pending PO architecture freeze.**

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><strong>DOCUMENT AUTHORITY<br />
</strong>This document is subordinate to the Impilo vNext Hybrid / Federated Target Architecture. Where any conflict exists, the controlling target architecture and its ADRs prevail. This successor draft becomes frozen only after the Target Architecture v1.3.2 is frozen by Product Owner sign-off.</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

*Source lineage: vNext V3 v1.2 (Rewritten Canonical - Production-Grade) + Hybrid / Federated Target Architecture v1.3.2.*

# Contents

> 1\. Document purpose and authority
>
> 2\. Product vision and scope
>
> 3\. Architecture mental model
>
> 4\. Rings: dependency and maturity, not deployment
>
> 5\. Named products and deployment relationship
>
> 6\. Capability architecture by plane
>
> 7\. Cross-plane product laws
>
> 8\. Experience architecture interface
>
> 9\. Ecosystem and dual-mode participation
>
> 10\. Production readiness and evidence
>
> 11\. Product governance
>
> Appendix A. Legacy V3 disposition register
>
> Appendix B. Document hierarchy

# 1. Document purpose and authority

This document defines what Impilo vNext is as a national product: its planes, capability families, named products, ownership boundaries, maturity rings, and reusable platform principles. It replaces the former vNext V3 architectural definition as the authoritative product-and-capability layer.

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><strong>SUPERSESSION NOTICE<br />
</strong>The former “Impilo vNext: National Health Operating System - Architectural Definition &amp; Implementation Standard, Version 1.2” is retained as a historical design baseline. It is superseded as a controlling architecture. Content survives only where this successor explicitly retains or amends it.</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

| **Document layer**                   | **Question answered**                                                                                                                   | **Controlling artefact**                                                                      |
|--------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| Target architecture                  | Where capabilities run, whose authority applies, how nodes federate, how organisations consume services, and how experience is resolved | Hybrid / Federated Target Architecture                                                        |
| Product architecture                 | What Impilo contains and how capabilities fit together                                                                                  | This document                                                                                 |
| Technical standards                  | How APIs, events, trust context, FHIR, federation and operations conform                                                                | Technical Standards Catalogue                                                                 |
| Domain and experience specifications | How each domain and journey behaves in detail                                                                                           | Experience Completion Packs, clinical packs, regulatory packs and domain specifications       |
| Implementation artefacts             | How conformance is expressed in code and operations                                                                                     | ADRs, OpenAPI, AsyncAPI, JSON Schema, migrations, policies, Helm profiles, tests and runbooks |

# 2. Product vision and scope

Impilo vNext is Zimbabwe’s sovereign National Health Operating System: a governed digital public infrastructure for health identity, trust, clinical care, public health, financing, logistics, intelligence, interoperability and user experience. It serves public, private, mission, local-authority, academic, security-sensitive and other accredited participants through one product and multiple governed profiles.

- One national product, not a collection of unrelated apps.

- One codebase and signed artefact set, with declared profile-specific behaviour rather than forks.

- National truth where national authority is required; local operational authority where care is delivered.

- Public-health, payer, clinical and operational capabilities reuse shared primitives rather than rebuilding identity, registries, terminology, audit or workflow foundations.

- Human users experience the platform through intention-led, audience-bound journeys rather than architecture vocabulary.

## 2.1 Scope boundary

This document does not duplicate federation schemas, trust-domain law, node commissioning, session contracts, detailed user journeys or wire protocols. It defines the capability portfolio and points to the controlling artefact for those concerns.

# 3. Architecture mental model

The product is organised into seven capability planes. Integration and execution/resilience are cross-cutting frameworks rather than additional business planes. The Experience Plane composes the others but does not become a source of truth for them.

| **Plane**                        | **Mandate**                                                                                                                       | **Representative capability family**                                                                     |
|----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| Kernel - Trust & Truth           | Identity, consent, policy, registries, terminology, catalogues, longitudinal record primitives, audit, keys and authority anchors | TSHEPO, VITO, VARAPI, TUSO, INDAWO, MSIKA, ZIBO, BUTANO, UBOMI, MUSHEX and mandatory platform primitives |
| Clinical - Care Execution        | Arrival, encounters, triage, orders, results, referrals, inpatient, theatre, maternity, telemedicine and care pathways            | PCT, OROS, diagnostic and departmental modules, telemedicine execution                                   |
| Public Health, Data & Governance | Surveillance, inspections, complaints, incidents, campaigns, oversight, data stewardship, analytics and research controls         | Reusable operational objects and jurisdiction packs                                                      |
| Coverage, Financing & Payer      | Membership, benefits, eligibility, contracting, preauthorization, claims, contributions, remittance, settlement and disputes      | Coverage services, Ruvimbo surfaces, COSTA/MUSHEX and payer workspaces                                   |
| Supply, Assets & Logistics       | Medicines, stock, procurement, dispatch, devices, equipment and asset operations                                                  | Dura, Nhume, Madi and related supply capabilities                                                        |
| Intelligence, Automation & AI    | Governed analytics, prediction, summarisation, anomaly detection, recommendation, optimisation and model governance               | Model registry, inference gateway, feature views, monitoring and explanation/override                    |
| Experience                       | Public front door, My Life, My Professional, Work, administration, regulation, commissioning, Nompilo, notifications and help     | Experience resolver, One UI shell, citizen/provider apps and Experience Completion Packs                 |

## 3.1 Cross-cutting frameworks

- Integration framework: stable internal and external contracts, FHIR and other standards, developer portal, certification and ecosystem onboarding.

- Execution and resilience framework: node profiles, offline execution, observability, security hardening, support, release governance, backup and disaster recovery.

- Configuration framework: trust domain -\> organisation -\> node -\> facility -\> department -\> service point, with versioned jurisdiction and capability packs.

# 4. Rings: dependency and maturity, not deployment

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><strong>RING RULE<br />
</strong>Rings describe capability dependency, stability, evidence and release discipline. Profiles describe deployment. Trust domains describe authority. A Ring 0 component may be national authority, a node-local projection, a local enforcement component or a signed cache without becoming a second National Core.</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

| **Ring**                                           | **Purpose**                                            | **Representative scope**                                                                                                                            | **Exit evidence**                                                                               |
|----------------------------------------------------|--------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| Ring 0 - Trust, truth and platform primitives      | Establish national and federated authority foundations | Trust, registries, terminology, audit, key management, governed FHIR, schema registry, contract harness, federation identity, finance rail baseline | Policy enforcement, provenance, contracts, audit, backup/restore and compatibility gates proven |
| Ring 1 - National care and financing steel threads | Prove end-to-end care and finance execution            | PCT, orders/results, referral, scheduling, basic inpatient, eligibility, claims, preauthorisation, remittance and self-service baseline             | Care -\> record -\> eligibility -\> claim -\> settlement and offline care drills pass           |
| Ring 2 - Scale, public health and intelligence     | Expand operations without destabilising core care      | Public-health operations, advanced workflow/content, analytics, supply ecosystems, research governance, advanced AI and integrity controls          | Independent scaling, governed data access and field-proven workflows                            |

Ring sequencing permits minimum enabling dependencies. Forms, workflow, notification and assistive intelligence needed for a safe Ring 1 journey may be delivered at baseline maturity before their broader Ring 2 platform is complete.

# 5. Named products and deployment relationship

| **Named product**              | **Product role**                                                                      | **Capability relationship**                                                                     |
|--------------------------------|---------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| Impilo National Core           | National federation hub and national systems of record                                | Hosts national authority, ecosystem services, national experiences and federation coordination  |
| Impilo Hospital Node           | Federation-autonomous clinical instance for an institution and one or more facilities | Runs local operational care, node trust enforcement and declared local capabilities             |
| Impilo Federation Gateway      | Only sanctioned cross-site exchange path                                              | Moves signed, versioned envelopes over mTLS; never raw cross-site Kafka or database replication |
| Tshepo Local Enforcement Node  | Node-local trust plane                                                                | Envoy PEP, local PDP/OPA, bundle agent, session context and local audit                         |
| Impilo Fleet & Release Service | National node operations plane                                                        | Node registry, health, certificates, capabilities, releases and upgrade rings                   |
| Impilo Node Bootstrap Agent    | Transient signed installer and enrolment agent                                        | Consumes a Bootstrap Manifest; does not create institutional authority                          |
| Impilo Facility Edge           | Reduced continuity profile for smaller facilities                                     | Local capture, cache and store-and-forward within a declared limited scope                      |

Service-consumption profiles, compute locality, site continuity and trust-domain authority are defined only in the controlling Target Architecture. Product architecture must never infer ownership or access from where software is hosted.

# 6. Capability architecture by plane

## 6.1 Kernel - Trust & Truth

| **Capability**       | **Canonical responsibility**                                                                                                        | **Boundary**                                                                                                               |
|----------------------|-------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| TSHEPO trust layer   | Identity, authentication, policy decision and enforcement, consent, delegation, context, federation trust, sessions, audit and risk | TSHEPO is a trust layer containing Keycloak, OPA, Envoy, Mvumo and supporting components; it is not one monolithic service |
| VITO                 | Client identity, Health ID, deduplication, merge and linkage                                                                        | Separates identity truth from clinical workflow; cross-domain linkage is governed                                          |
| VARAPI               | Provider identity, standing, qualifications, scope, restrictions and regulatory truth                                               | Employer Work receives only the minimum authorised standing projection                                                     |
| TUSO                 | Clinical and service-delivery facility registry                                                                                     | Facility identity is stable when deployment routing changes                                                                |
| INDAWO               | Non-clinical regulated premises and public-health sites                                                                             | Do not force premises into the facility model when semantics do not fit                                                    |
| MSIKA                | Products, services, orderables, billables, tariffs and benefit references                                                           | One service with separately authorised personal, professional and Work surfaces where applicable                           |
| ZIBO                 | Terminology, value sets, maps, confidentiality vocabularies and semantic governance                                                 | Versioned bundles support node-local execution                                                                             |
| BUTANO               | Governed FHIR projections and longitudinal memory                                                                                   | Origin authority remains with the source fact; projections never silently overwrite origin                                 |
| UBOMI                | Births, deaths and civil-registration interface                                                                                     | Reconciliation rather than hidden identity replacement                                                                     |
| MUSHEX               | Finance switching, authorisation, remittance, settlement and reconciliation rails                                                   | Irreversible financial actions require authoritative proof and reversibility                                               |
| Mandatory primitives | Schema registry, contract tests, audit ledger, KMS/HSM integration, developer portal and AI governance registry                     | These are operating foundations, not deferred conveniences                                                                 |

## 6.2 Clinical - Care Execution

- Patient arrival, identity resolution and registration.

- Queue, sorting, triage, emergency, outpatient and virtual care.

- Adaptive clinical encounter cockpit, structured forms, pathways and deterministic decision support.

- Orders, specimens, results, prescribing, dispensing and medication administration.

- Admission, beds, ward rounds, nursing, transfer, discharge, theatre and procedures.

- Maternity, newborn, paediatrics, adult medicine, surgery and specialty pathways.

- Referral, multidisciplinary collaboration, teleconsultation and remote diagnostics.

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><strong>CLINICAL COMPOSITION PRIORITY<br />
</strong>The principal gap is composition, not absence of domain content. The Clinical Work Experience Pack must connect arrival, patient worklists, care setting, cadre logic, form scopes, age-appropriate triage and pathway activation inside the ordinary encounter.</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

## 6.3 Public Health, Data & Governance

Public-health and local-authority variation is implemented through shared platform capabilities and versioned jurisdiction packs. City Health is a configured instance of a broader Public Health Operations capability, not a disconnected municipal product.

| **Canonical operational object**          | **Examples of use**                                                                                |
|-------------------------------------------|----------------------------------------------------------------------------------------------------|
| Premises / regulated site                 | Markets, schools, water points, waste sites, ports, food premises, abattoirs and similar locations |
| Geographic area / ward / zone / catchment | Jurisdiction, surveillance, deployment and reporting boundaries                                    |
| Public-health event / complaint / signal  | Surveillance, citizen reports, alerts and operational escalation                                   |
| Inspection / investigation / finding      | Environmental health, compliance, laboratory and enforcement workflows                             |
| Notice / enforcement action               | Corrective action, closure, follow-up and legal traceability                                       |
| Outbreak / incident / response task       | Coordination, deployment, logistics, communications and after-action review                        |
| Campaign / outreach session               | Immunisation, screening, community services and programme operations                               |

## 6.4 Coverage, Financing & Payer

- Scheme, plan, package and benefit administration.

- Membership, beneficiary enrolment, coverage periods, waiting periods, exclusions and limits.

- Provider contracting, network management and tariff schedules.

- Eligibility, preauthorisation, utilisation review and financial clearance.

- Claims capture, coding edits, adjudication, appeals and integrity review.

- Contributions, invoices, receipts, arrears, subsidies and co-financing.

- Provider payments, remittance, settlement, reversal, reconciliation and recovery.

Financing capabilities consume shared sovereign identity, provider, facility, terminology, clinical, pricing, audit and payment truth. They do not create private copies of the national master data merely because a payer operates a separate workspace.

## 6.5 Supply, Assets & Logistics

- Medicines, commodities, stock, procurement and distribution.

- Specimen, blood, document, courier and other dispatch workflows.

- Devices, equipment, maintenance, calibration and asset registries.

- Facility-specific integration with eLMIS, analysers, printers, PACS and other local systems.

## 6.6 Intelligence, Automation & AI

| **Class**                         | **Permitted role**                                                    | **Human control**                                 |
|-----------------------------------|-----------------------------------------------------------------------|---------------------------------------------------|
| I1 - Insight only                 | Dashboards, trends, summaries and anomaly hints                       | No direct operational effect                      |
| I2 - Recommendation               | Risk scoring, prioritisation, coding suggestions and decision support | Explicit human acceptance or override             |
| I3 - Governed low-risk automation | Queue routing, reminder prioritisation and low-risk classification    | Explicit approval, monitoring, rollback and audit |

No AI capability may silently finalise high-risk clinical care, enforcement, entitlement, claims or settlement decisions. Model identity, version, context, timestamp, confidence where relevant and human action must be recorded.

## 6.7 Experience

- Public experience and care discovery before sign-in.

- My Life: personal health, PHR, consent, coverage, payments, wellness and delegated care.

- My Professional: provider identity, licence, CPD, regulatory self-service, portfolio and invitations.

- Work: active organisation, facility, department, role, shift and operational workflows.

- Organisation, facility, regulatory, node-operations and platform-administration experiences.

- Nompilo guidance, journey persistence, the action centre, help and escalation.

# 7. Cross-plane product laws

| **Law**                  | **Product rule**                                                                                                                                                                         |
|--------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Shared truth             | Identity, provider, facility, terminology and financial rails are reused wherever lawful; no domain silently creates competing masters.                                                  |
| Module-first             | Logical service names do not automatically require separate repositories, deployables or teams. Extraction requires a scaling, resilience, protection, ownership or operational trigger. |
| Configuration over forks | Jurisdictional, institutional and facility variation uses versioned configuration, forms, workflows, role maps and capability packs.                                                     |
| Clinical safety          | Every clinical action declares an authoritative decision and offline/reconciliation posture.                                                                                             |
| Financial integrity      | Money movement, reservation, reimbursement, remittance and settlement are idempotent, auditable, reversible or compensatable, and reconciled.                                            |
| AI integrity             | AI remains assistive by default, governed always, and autonomous only within approved low-risk classes.                                                                                  |
| Experience honesty       | Unavailable, empty, denied, degraded and stale are distinct product states; no false success or silent redirect.                                                                         |
| One product              | Hospital profiles are declared, tested configurations of one artefact set; independently maintained hospital forks are prohibited.                                                       |

# 8. Experience architecture interface

The Product Architecture defines the domains and capability families. The Experience Architecture defines how a person discovers them, enters the correct audience, resumes a journey, understands state and receives help. This document does not independently specify navigation or journey state.

| **Surface**                    | **Product question answered**                               | **Controlling specification**     |
|--------------------------------|-------------------------------------------------------------|-----------------------------------|
| Public                         | What can Impilo help me do before sign-in?                  | Experience Architecture + Pack P1 |
| My Life                        | What is happening with my health and what can I do next?    | Experience Architecture + Pack P1 |
| My Professional                | Am I professionally ready and what requires action?         | Experience Architecture + Pack P1 |
| Work                           | Where am I working and what needs attention now?            | Experience Architecture + Pack P3 |
| Organisation / facility / node | How do we claim, configure, commission and operate?         | Pack P2                           |
| Regulatory / oversight         | How do authorities operate complete casework and registers? | Pack P4                           |

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><strong>EXPERIENCE COMPLETION<br />
</strong>Seven packs govern the journey-level work: P1 public/personal/professional; P2 self-service and commissioning; P3 clinical Work; P4 regulatory; P5 accessibility/localisation/content; P6 design system; P7 human usability and field validation.</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

# 9. Ecosystem and dual-mode participation

National capabilities are designed for Impilo applications, accredited external applications, institutional nodes, payers, research and approved analytics/AI consumers. “Dual mode” is real only when supported by stable contracts, certification, developer tooling and governed deprecation.

- Developer portal, sandbox, mock services, SDKs and API registration.

- Internal and external API surfaces with explicit audiences and rights.

- Contract and schema compatibility testing for partners.

- Integration profiles for clinical, payer, settlement, public-health and analytics participants.

- Certification, conformance evidence, revocation and lifecycle management.

# 10. Production readiness and evidence

| **Evidence family** | **Minimum proof**                                                                                                             |
|---------------------|-------------------------------------------------------------------------------------------------------------------------------|
| Trust and safety    | Policy enforcement on the request path, consent and sensitivity controls, break-glass, authority freshness and audit evidence |
| Federation          | Node commissioning, signed exchange, provenance, conflict handling, revocation, reconnection and quarantine                   |
| Reliability         | SLOs, capacity assumptions, backup/restore, RPO/RTO, disconnection drills and release rollback                                |
| Clinical            | Arrival-to-discharge workflows, adaptive pathways, orders/results, medication, referral and offline care                      |
| Finance             | Eligibility, preauthorisation, claim, remittance, settlement, reversal, recovery and reconciliation                           |
| Experience          | Journey blueprints, web/mobile parity, accessibility, localisation, shared-workstation safety and field validation            |
| Ecosystem           | One external integration certified against current contracts and lifecycle rules                                              |
| AI                  | One governed workflow with traceable model use, human override, monitoring and rollback                                       |

# 11. Product governance

- Capability ownership is explicit: every canonical object, policy, workflow and projection has one accountable owner.

- The Target Architecture changes only through ADRs and named governance decisions.

- Product capability changes update this document; wire contracts update the Technical Standards Catalogue and machine-readable specifications.

- Journey or visual changes belong to Experience Completion Packs and the versioned Design System unless they alter an architecture invariant.

- Any legacy document marked “canonical” or “frozen” is subordinate once a successor is ratified and must carry a supersession notice.

# Appendix A. Legacy V3 disposition register

| **Legacy section**               | **Disposition**  | **Successor treatment**                                                                                                                                        |
|----------------------------------|------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 0\. Purpose of rewrite           | RETAIN / REFRAME | Retained as historical rationale; successor purpose is product-and-capability governance.                                                                      |
| 1\. Executive summary            | AMEND            | Retain National Health OS vision; replace National Spine/Sovereign Pod terminology with National Core, trust domains, Hospital Nodes and consumption profiles. |
| 2.1-2.6 capability planes        | RETAIN / AMEND   | Retain mandates; clarify cross-cutting integration/execution frameworks and current named products.                                                            |
| 2.7 Experience Plane             | MOVE             | Keep only product role and pointer; detailed model belongs to Experience Architecture and packs.                                                               |
| 2.8 Public Health reuse          | RETAIN           | Binding product law.                                                                                                                                           |
| 2.9 Financing reuse              | RETAIN           | Binding product law.                                                                                                                                           |
| 2.10 AI governance               | RETAIN           | Binding product law, refined by Technical Standards Catalogue.                                                                                                 |
| 3\. Architecture laws            | DISTRIBUTE       | Product-level laws retained here; request context, federation, event and enforcement details move to technical standards and target architecture.              |
| 4\. Ring 0 service catalogue     | RETAIN / UPDATE  | Retain capability ownership; correct Tshepo as trust layer and Butano as governed projections.                                                                 |
| 5\. Pod model                    | SUPERSEDE        | Replaced by trust domains, nodes, service profiles, commissioning and federation gateway.                                                                      |
| 5.1A Jurisdiction packs          | RETAIN           | Remain versioned configuration artefacts, never forks.                                                                                                         |
| 5.2 Federation protocol          | MOVE / SUPERSEDE | Product principle retained; protocol moves to Target Architecture and Technical Standards Catalogue.                                                           |
| 6\. Clinical safety model        | RETAIN / AMEND   | Keep classes and evidence; “synchronous Kernel” becomes authoritative local or national decision according to trust source.                                    |
| 7\. Data/events/read models      | DISTRIBUTE       | Canonical object catalogues stay here; envelopes and projection rules move to technical standards.                                                             |
| 8\. Operational architecture     | MOVE             | Reliability principles remain; implementation standards move to technical catalogue and node profiles.                                                         |
| 9\. Dual-mode services           | RETAIN / MOVE    | Product ecosystem principle remains; APIs and certification move to technical standards.                                                                       |
| 10\. Ring strategy               | RETAIN / AMEND   | Rings are dependency/maturity, never deployment or authority.                                                                                                  |
| 11\. Component model             | RETAIN / UPDATE  | Retain module-first discipline and align to current product names.                                                                                             |
| 12\. One UI                      | MOVE             | Three domains retained; all navigation, state and journeys governed elsewhere.                                                                                 |
| 13\. Production-ready definition | RETAIN / EXPAND  | Recast as evidence families aligned to current target acceptance gates.                                                                                        |
| 14\. Final position              | RETAIN           | Impilo is national DPI, not a large app.                                                                                                                       |

# Appendix B. Document hierarchy

**1.** Hybrid / Federated Target Architecture - controlling federation, trust, deployment, experience-model and commissioning architecture.

**2.** Product, Capability and Plane Architecture - this document; defines product scope and capability ownership.

**3.** Technical Standards Catalogue - governs machine and operational conformance.

**4.** Experience Completion Packs and domain specifications - complete journeys and domain behaviour.

**5.** Machine-readable and operational artefacts - ADRs, APIs, events, schemas, policies, migrations, Helm, tests and runbooks.
