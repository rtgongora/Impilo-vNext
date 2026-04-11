# Impilo vNext: Health Operating System for Masvingo City Council

**Prepared for**: Masvingo City Council Health Services Department
**Date**: April 2026

---

## Executive Summary

Impilo is a **Health Operating System** — a single, governed digital environment that unifies clinical care, public health surveillance, financial management, citizen engagement, pharmacy and supply chain, and workforce governance into one coherent platform.

For Masvingo City Council, Impilo replaces fragmented paper registers, disconnected systems, and manual reporting with an integrated digital backbone that works across every clinic, health centre, and outreach point in the municipality — **including areas with limited or no connectivity**.

> **One system. One patient identity. Many facilities. Many roles. Complete accountability.**

---

## Why Impilo for Masvingo?

### The Challenge
Municipal health services face compounding pressures:
- Paper-based records that fragment patient histories across facilities
- Manual disease surveillance that delays outbreak detection
- Revenue leakage from untracked billing and exemptions
- No real-time visibility into facility queues, stock levels, or workforce deployment
- Citizens with no convenient way to access their health information or book services
- Reporting burdens that divert clinical staff from patient care

### The Impilo Answer
Impilo addresses **all of these** as a single, coherent platform — not a patchwork of point solutions stitched together with integrations.

---

## What Masvingo Gets

### 1. Clinical Care Delivery

| Capability | What It Does |
|---|---|
| **Patient Journey Tracking** | Tracks every patient from arrival through triage, consultation, admission, treatment, and discharge — with real-time queue visibility for facility managers |
| **Electronic Health Records** | FHIR R4-compliant longitudinal records accessible across all municipal facilities, ending the "lost file" problem |
| **Orders & Results** | Digital ordering of lab tests, imaging, procedures, and pharmacy items with tracked fulfilment and result delivery |
| **Pharmacy & Dispensing** | Complete dispensing workflow with stock management, expiry tracking (FEFO), barcode verification, and substitution guidance aligned to EDLIZ |
| **Inpatient Management** | Bed allocation, ward transfers, nursing assignments, and discharge planning |

**Impact**: Clinicians spend less time on paperwork and more time with patients. Patient records follow the person, not the paper folder.

---

### 2. Public Health & Surveillance

| Capability | What It Does |
|---|---|
| **Real-Time Disease Surveillance** | Automatically detects outbreak patterns from clinical encounters — cholera, typhoid, measles — and raises alerts before manual reporting would catch them |
| **Campaign Management** | Plan and execute immunisation drives, maternal health campaigns, screening programmes, and community outreach with enrollment tracking and outcome measurement |
| **Epidemiological Reporting** | Integration-ready for DHIS2 and national reporting obligations, reducing the manual burden on facility staff |
| **Catchment Area Mapping** | Geographic assignment of populations to facilities (Province > District > Constituency > Ward) for targeted health interventions |

**Impact**: Masvingo moves from reactive to proactive public health — detecting disease signals days or weeks earlier and targeting interventions where they are needed most.

---

### 3. Financial Management & Sustainability

| Capability | What It Does |
|---|---|
| **Automated Billing** | Every service delivered is costed and billed automatically — no more lost revenue from unrecorded consultations |
| **Cost Engine** | Calculates the true cost of every clinical service, from a blood test to an inpatient stay, enabling evidence-based pricing |
| **Claims Processing** | Submit and track insurance claims, manage exemptions for vulnerable populations, and reconcile payments |
| **Financial Reporting** | Double-entry accounting ledger with real-time dashboards for municipal leadership — revenue, expenses, exemptions, outstanding claims |
| **Cost Exemption Tracking** | Transparent management of subsidised care for chronically ill, elderly, and poverty-exempted patients without budget surprises |

**Impact**: Revenue recovery improves. Council leadership gains financial visibility. Subsidised care is tracked and accountable rather than invisible.

---

### 4. Pharmacy & Supply Chain

| Capability | What It Does |
|---|---|
| **Inventory Management** | Real-time stock tracking across all facilities with automated reorder alerts |
| **FEFO Enforcement** | First-Expiry-First-Out dispensing prevents medication waste |
| **Requisition Workflows** | Digital stock requests, approvals, and supplier handovers with full audit trail |
| **eLMIS Integration** | Connects to the national Electronic Logistics Management System for coordinated supply chain |
| **Consumption Forecasting** | Usage-based predictions prevent stockouts of critical medications and supplies |

**Impact**: No more expired drugs on shelves while patients are turned away. Stock levels visible in real time across the municipal network.

---

### 5. Citizen & Community Engagement

| Capability | What It Does |
|---|---|
| **Citizen Mobile App** | Android & iOS app giving patients access to their health records, test results, appointment booking, prescription refills, and secure messaging with their care team |
| **Service Discovery** | Citizens find nearby clinics, pharmacies, and health services by location and need |
| **Health Marketplace** | Access wellness products, health services, and membership programmes through the platform |
| **Multi-Channel Notifications** | Appointment reminders, campaign messages, and health alerts via SMS, email, and push notifications |
| **Wellness & Lifestyle** | Integrated fitness, nutrition, and sleep guidance supporting preventive health |
| **Caregiver Access** | Family members can manage health for dependants (children, elderly parents) |

**Impact**: Citizens become active participants in their health. Clinic congestion decreases as self-service improves. Preventive care uptake rises.

---

### 6. Workforce & Facility Governance

| Capability | What It Does |
|---|---|
| **Facility Registry** | Complete inventory of all municipal health facilities with service capabilities, operating hours, and resource calendars |
| **Provider Registry** | Licensed healthcare worker directory with credentialing, scope of practice, council validation, and schedule management |
| **Performance Dashboards** | Workforce engagement, workload distribution, and facility utilisation trends |
| **Location & Address Registry** | Standardised geocoded addressing for facility mapping and catchment area planning |

**Impact**: Council leadership has a real-time picture of infrastructure, staffing, and service delivery capacity across the municipality.

---

## Built for Masvingo's Reality

### Offline-First
Impilo's mobile apps and facility workstations function **fully offline** with automatic synchronisation when connectivity returns. Outreach teams, rural health posts, and mobile clinics are never cut off.

### Low-Bandwidth Optimised
The platform is engineered for slow networks — mobile apps designed for 2G/3G, efficient data transfer, and minimal bandwidth consumption.

### SMS & USSD Ready
For communities without smartphones, campaigns and notifications reach citizens through SMS. USSD integration enables basic health interactions from any phone.

### Graduated Security
Impilo applies friction proportional to risk:
- **Minimal friction** for wellness, search, and information access
- **Moderate friction** for booking and personal health data
- **Maximum friction** for prescribing, financial transactions, and clinical decisions

---

## One Identity, Complete Continuity

Every person in Masvingo's health system receives **one Health ID** — a single anchor that links all their interactions across facilities, roles, and time.

- A mother visiting Clinic A for antenatal care has her full history available when she delivers at Hospital B
- A child immunised during a community outreach campaign has that record linked to their clinic visits
- A chronic patient refilling medication at any pharmacy in the network has their prescription history verified

No duplicate registrations. No lost records. No starting over at each facility.

---

## Architecture: Trusted, Governed, Standards-Compliant

| Principle | Implementation |
|---|---|
| **Trust-First** | Every transaction flows through the TSHEPO governance engine — access control, consent enforcement, and cryptographic audit |
| **Privacy by Design** | No personally identifiable information in the shared health record; PII stays in the identity vault (VITO) |
| **FHIR R4 Compliance** | International healthcare data standards enabling interoperability with provincial, national, and global health systems |
| **Immutable Audit Trail** | SHA-256 hash-chain ledger — every access decision is recorded and exportable for compliance |
| **Consent Enforcement** | Patient consent preferences respected across all data access — citizens control who sees their information |
| **Open Standards** | No vendor lock-in; all technology is open-source or vendor-agnostic |

---

## Technology Foundation

| Layer | Technology |
|---|---|
| Frontend | Next.js 14, React, TypeScript, Tailwind CSS |
| Mobile | React Native, Expo (single codebase: Android + iOS) |
| Backend | Java 21, Spring Boot 3.3 |
| Database | PostgreSQL 16 |
| Streaming | Apache Kafka (real-time event processing) |
| Clinical Standards | HAPI FHIR 7.x, DICOM/PACS |
| Identity | Keycloak, MOSIP-ready |
| Deployment | Kubernetes, Docker, Helm |

All open-source. No proprietary lock-in. Masvingo retains full operational sovereignty.

---

## Deployment Model

### City-Wide Spine
A central Kubernetes cluster runs core services — patient registry, health records, finance, surveillance, audit — serving all facilities under one data governance umbrella, with federation to provincial and national systems.

### Facility Workspaces
Each clinic and health centre runs optimised clinical workflows appropriate to its tier and capabilities, synchronising with the city-wide spine.

### Mobile & Outreach
Healthcare workers conducting vaccination drives, antenatal outreach, or disease surveillance use offline-capable mobile apps that synchronise when connectivity is available.

---

## Implementation Approach

| Phase | Duration | Scope |
|---|---|---|
| **Phase 0: Foundation** | 4 weeks | Environment setup, trust chain validation, stakeholder alignment |
| **Phase 1: Pilot** | 8 weeks | Deploy at 1-2 high-volume facilities; clinical workflows, patient registry, pharmacy |
| **Phase 2: Expansion** | 12 weeks | Roll out to all primary care centres; add financial management, surveillance, campaigns |
| **Phase 3: Full Network** | 16+ weeks | Complete municipal network operational; citizen app launch; mature support model |

Training programmes included:
- **Clinical staff training** (Fundo-200) — EHR, orders, pharmacy workflows
- **Administrative training** (Fundo-600) — facility management, finance, reporting

---

## Business Outcomes

| Outcome | How Impilo Delivers |
|---|---|
| **Improved Access to Care** | Appointment booking, service discovery, reduced queue times |
| **Better Care Quality** | Clinical decision support, shared records, EDLIZ-aligned workflows |
| **Earlier Disease Detection** | Automated surveillance alerts for rapid outbreak response |
| **Financial Accountability** | Automated billing, exemption tracking, real-time financial dashboards |
| **Data-Driven Planning** | Facility utilisation, population health trends, workforce analytics |
| **Workforce Efficiency** | Digital workflows replace paper, scheduling and performance tracking |
| **Citizen Satisfaction** | Mobile app, convenient booking, transparent pricing, wellness engagement |
| **Regulatory Compliance** | Complete audit trail, consent management, standards-based data |

---

## Summary

Impilo is not another health IT project. It is a **platform investment** — a governed operating system upon which Masvingo City Council builds a modern, accountable, and citizen-centered health service.

**What makes it different**:
- **Integrated**: One platform replaces dozens of disconnected tools and registers
- **Offline-capable**: Works everywhere in Masvingo, not just where there is connectivity
- **Citizen-facing**: Patients are participants, not passive recipients
- **Financially sustainable**: Automated billing and cost tracking prevent revenue leakage
- **Surveillance-ready**: Public health is built in, not bolted on
- **Trust-first**: Every transaction governed, audited, and accountable
- **Standards-compliant**: FHIR R4, open-source, no vendor lock-in
- **Scalable**: From one clinic to a national health system on the same platform

**Impilo gives Masvingo City Council a health system that is digital, governed, connected, and ready for the future.**

---

*For technical deep-dives, demonstrations, or implementation planning discussions, contact the Impilo implementation team.*
