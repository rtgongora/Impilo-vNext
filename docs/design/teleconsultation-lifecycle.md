# Teleconsultation Lifecycle — 7-Stage Specification

> Captured from domain expert input, 2026-04-14.
> Implementation status tracked per stage.

## Stage 1 — Case Identified
**"The clinician decides they need help."**

### Clinical Triggers
- Case exceeds clinician's scope
- Second opinion needed
- Diagnostic uncertainty
- Management plan confirmation needed
- Escalation from lower-level facility
- CHW/midwife/nurse needs medical input
- Board review (M&M, MDT, specialist board)

### System Entry Points
1. **Patient Record** — "Start Teleconsultation" button across OPD, Ward, ED, ANC, PNC, specialty
2. **Emergency Panel** — for emergency consults
3. **Standalone Portal** — patient lookup → Start Teleconsult (non-EHR facilities)

### Preconditions
- Patient must have valid Health ID (HID)
- User must have valid Provider Registry ID
- Current encounter must exist (auto-created if not)

### Implementation Status
- [x] Start Teleconsult button on consults page
- [x] Patient/encounter context loaded
- [ ] Provider Registry ID validation
- [ ] Auto-encounter creation

---

## Stage 2 — Build the Referral Package
**"The referrer constructs the digital clinical handover."**

### Six Components
1. **Referral Letter** (user-entered, required)
2. **Patient Summary** (auto-generated panel)
3. **Visit Summary** (auto-generated panel)
4. **Attachments** (uploader + preview)
5. **Routing / Targeting** (mandatory)
6. **Consent** (mandatory via Trust Layer)

### Key Behaviors
- Auto-save every 3s or on blur
- Draft mode until "Send Referral"
- At least one presenting problem required
- Consent token required before send

### Digital Consent Flow
1. FE displays consent modal
2. Provider captures consent (digital, verbal, proxy, emergency)
3. FE calls Trust Layer → POST /consent
4. Trust Layer verifies patient, referrer, receiver
5. Trust Layer issues Consent Token
6. FE attaches token to referral payload
7. If token missing → SEND disabled

### Routing Logic
Target types: Practitioner, Workspace, On-Call Team, Unit/Ward, Facility Clinical Service, General/Specialty Pool.

Routing engine checks: existence, availability, capacity/queue, access/credentials, escalation path.

### Implementation Status
- [x] Referral letter composition
- [x] Basic routing (specialty + facility selection)
- [ ] Multi-step layout with left navigation
- [ ] Patient summary auto-panel
- [ ] Visit summary auto-panel
- [ ] Attachment uploader + preview
- [ ] Full routing engine with availability/capacity
- [ ] Digital consent modal + Trust Layer integration
- [ ] Auto-save with indicator

---

## Stage 3 — Routing & Worklists
**"The referral travels to the correct queue."**

### Worklist Types
1. My Referrals (Sent)
2. Referrals Assigned to Me (Receiver)
3. Workspace Queue
4. Unit/Ward Queue
5. Team Queue (On-call)
6. Facility Service Queue
7. General/Specialty Pool

### Worklist Item Fields
Patient name/age/sex, facility of origin, referring clinician, urgency badge, time waiting, routing type icon.

### Implementation Status
- [x] Referrals tab with sent/received filtering
- [x] Status badges (PENDING, ACCEPTED, etc.)
- [ ] Full worklist with all 7 queue types
- [ ] Real-time updates via websockets
- [ ] Urgency/time-waiting indicators

---

## Stage 4 — Review & Accept
**"The receiver reads the full package and takes responsibility."**

### Full Review Screen
Referral letter, patient summary, visit summary, attachments, routing details, consent badge, history & timestamps.

### Acceptance Options
- Accept & Become Primary
- Assign to Self (if workspace/team routed)
- Reassign (to more appropriate receiver)
- Decline (with mandatory reason)

### Implementation Status
- [x] Accept/Decline buttons
- [x] Response notes field
- [ ] Full-page referral review
- [ ] Reassign workflow
- [ ] Mandatory decline reason

---

## Stage 5 — Teleconsultation Session
**"Real-time or asynchronous clinical collaboration."**

### 3-Pane Workspace
- **LEFT**: Chat, Audio/Video call, Board/MDT mode, call logs
- **CENTER**: Response note draft (structured form, auto-save)
- **RIGHT**: Patient summary, visit summary, attachments, orders, consent, timeline

### Communication Modes
- Chat (WebSocket)
- Audio/Video (WebRTC)
- Async store-and-forward
- Board / MDT mode (multi-participant)

### Implementation Status
- [x] Basic session page with video/audio placeholders
- [x] Chat panel
- [ ] 3-pane workspace layout
- [ ] WebRTC integration
- [ ] Auto-save response draft
- [ ] Board/MDT mode

---

## Stage 6 — Submit Response Package
**"Receiver sends back a structured, clinically actionable response."**

### Response Package Components
1. Consultation response note (diagnosis, interpretation, action plan, red flags)
2. Orders (ServiceRequest, medications, imaging, labs, procedures)
3. Attachments (annotated images, PDFs, audio notes)
4. Follow-up block (timeframe, mode, instructions, risk notes)
5. System summary (auto-generated metadata)

### Implementation Status
- [x] Response notes field
- [ ] Structured response form with coded diagnoses
- [ ] Orders integration
- [ ] Attachment upload from response
- [ ] Follow-up block
- [ ] Submission lock

---

## Stage 7 — Completion Note & Loop Closure
**"Referrer documents what happened next."**

### Completion Note Components
1. Actions taken (meds, tests, procedures, monitoring, counseling)
2. Patient outcome (improved, deteriorated, stable, referred, discharged)
3. Follow-up execution (completed, partial, not completed with reason)
4. Outstanding issues
5. Case closure narrative

### Implementation Status
- [x] Basic loop closure status
- [ ] Structured completion note form
- [ ] Outcome tracking
- [ ] Follow-up execution verification
- [ ] Case archival to patient timeline
