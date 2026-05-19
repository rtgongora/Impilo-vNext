# Client, Provider, and Platform Journey Map

## Client Journey (Canonical)

Find Care -> Identify Me -> Book/Check In/Join Queue -> Receive Care -> Pay/Claim/Exemption -> Know What Next -> Continue Care and Wellness -> Manage Consent and Sharing -> Manage Dependents -> View Personal Health Record.

The journey is always mapped to a transaction state and next-action set.

## Provider Journey (Canonical)

Start Duty -> Select Facility/Workspace/Role -> See My Work -> Open Client Context -> Deliver Care -> Order Actions -> Complete Transaction -> Review Results -> Follow Up -> Supervise/Mentor -> Learn/Maintain Competency.

## Platform / Back-of-House Journey (Canonical)

Receive Trigger -> Resolve Identity -> Establish Trust Context -> Resolve Service/Workflow -> Apply Cost/Coverage/Payment Rules -> Manage State Machine -> Compose Experience View -> Execute Clinical/Financial Flows -> Update Continuity -> Emit Events/Audit -> Handle Failure/Offline/Reconciliation.

## Platform Questions That Must Always Be Answered

1. Who is the client?
2. Who is the provider/actor?
3. Where is the service context?
4. What service is requested/delivered?
5. What state is the transaction in?
6. What trust/consent basis applies?
7. What record and event were created?
8. What financial and follow-up actions remain?
9. What is the next safe action?
10. What Nompilo guidance is relevant now?

## UX Doctrine

Users should never be lost:

- timeline must show where they are;
- state badge must show current transaction state;
- next-action panel must show allowed actions;
- failure and offline states must be visible and recoverable.
