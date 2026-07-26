# Core Transaction Doctrine

## Foundational Truth

Impilo vNext is a sovereign Health Operating System whose primary unit of value is the trusted health service transaction:

Person + Need + Context + Trust + Service + Provider + Workflow + Record + Cost + Follow-Up + Accountability = Trusted Health Transaction.

This transaction is expressed through three synchronized journeys:

1. Person / Client Journey
2. Provider / Health Worker Journey
3. Platform / Back-of-House Journey

These are three views of the same transaction, never three separate systems.

## Canonical Transaction Journey

Need/Trigger -> Entry Point -> Identity Resolution -> Trust/Consent/Context -> Service Selection -> Costing/Coverage/Payment Gate (where required) -> Scheduling/Queue/Task -> Triage/Eligibility -> Provider Assignment -> Encounter/Delivery -> Orders/Ancillary -> Additional Billing/Claims/Settlement (where applicable) -> Record Update/SHR -> Instructions/Notifications -> Follow-Up/Continuity -> Reporting/Analytics/Audit.

## Doctrine Commitments

1. Anchor every capability to the Core Transaction.
2. Preserve seven-plane architecture; Core Transaction is the operational spine across planes.
3. Preserve source-of-truth discipline:
   - Registry truth in Vito/Varapi/Tuso/Zibo/Msika/Indawo.
   - Trust truth in Tshepo/Mvumo/audit.
   - Clinical truth in Butano and clinical services.
   - Financial truth in Costa/MusheX/coverage/GL.
   - Experience truth limited to orchestration/presentation state.
4. Every meaningful action must have state, event, permission, and audit meaning.
5. Offline and emergency pathways must remain governed, reconciled, and auditable.
6. Every relevant user-facing workflow must include Nompilo guidance consideration.
7. Accessibility and omnichannel feedback must be considered in completion criteria.

## Service Role Anchors

- **Vito**: person/client identity and reconciliation.
- **Varapi**: provider identity and standing.
- **Tuso**: facility/workspace context.
- **Tshepo/Mvumo**: trust, authorization, consent.
- **Butano**: longitudinal clinical contribution target.
- **PCT**: Care Continuum owner — the person's cradle-to-grave clinical journey; visit journeys, encounters, problems, care plans, referrals; every care-path transaction anchors into the continuum here (care-continuum-doctrine.md CC-1/CC-5).
- **Simba**: Wellness Continuum owner — peer in rank to PCT for the person's wellness and lifestyle journey.
- **Zibo**: canonical terminology.
- **Msika + Msika Flow**: service/product selection and workflow execution.
- **Costa + MusheX**: costing, payment, claims, remittances.
- **Ubomi/Indawo**: civil and public-health site context where applicable.
- **Fundo**: competency support for provider readiness.
- **Nompilo**: intelligent journey companion inside the transaction flow.

## Nompilo Doctrine

Nompilo is not a standalone chatbot. Nompilo is the platform's intelligent assistance layer across person, provider, and platform journeys.

Nompilo must:

- remain available but non-intrusive;
- explain decisions made by sovereign services (not invent hidden decisions);
- support accessibility options (simple language, audio, keyboard, screen reader, high contrast, low bandwidth);
- support feedback capture across in-app and omnichannel channels;
- support human handoff when confidence is low or risk is high.

## Experience and BFF Doctrine

- One UI Shell is the human orchestration surface of the transaction.
- Experience BFF composes sovereign truths; it does not own clinical/registry/trust/finance truth.
- Backend-only and frontend-only partials are both incomplete unless journey and truth are wired.
- Experience BFF supports `/internal/v1/core-transactions/*` and doctrine-facing alias `/experience/core-transactions/*`.

## Anti-Duplication Rule

No new feature may create parallel patient/provider/facility/service/terminology/consent/payment/clinical record truth outside canonical services.

## Completion Doctrine

A feature is complete only when it includes:

- doctrine mapping;
- journey mapping;
- contract + endpoint wiring;
- state transition and event meaning;
- permission + audit meaning;
- Nompilo guidance assessment;
- accessibility and feedback consideration;
- tests and documentation.
