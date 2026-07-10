# Introduction to Impilo vNext
### Monthly Meeting — Presentation Master Document
*Audience: Facility-based users · District managers · Provincial managers · HQ leadership · Partners*

---

> **How to use this document**
> Each numbered block below = one slide. The **Slide** line is your title, the bullets
> are what goes on screen (keep them short — the audience reads, you talk), and
> **Speaker notes** are what you say. Aim to *talk to* the bullets, not read them.
> A suggested 20-slide flow runs ~20–25 minutes, leaving room for Q&A.
> Colour cue: use one calm primary colour throughout; the wellness/citizen slides
> can go lighter/warmer, the trust/governance slides darker/serious.

---

## SLIDE 1 — Title

**Impilo vNext**
*A National Health Operating System for Zimbabwe*

- Monthly Programme Meeting
- [Date] · [Presenter name & role]

**Speaker notes:**
Good morning everyone. Thank you for being here — facility teams, district and provincial colleagues, HQ leadership, and our partners. Over the next 20 minutes I want to give you a shared, plain-language picture of what Impilo vNext is, why we're building it this way, and — most importantly — what it means for *you*, wherever you sit in the system. This is an introduction, not a technical deep-dive. Questions welcome at the end.

---

## SLIDE 2 — The problem we all live with today

**Health information is fragmented**

- A patient's story is scattered across many systems that don't talk to each other
- The same person is registered many times, many ways
- Data is entered many times, trusted by no one, reconciled by hand
- Managers wait weeks for numbers; frontline staff carry the burden

**Speaker notes:**
Let's start with something everyone in this room recognises. Today, a single patient can exist in a dozen different registers — one at the clinic, another at the hospital, another in a programme database, another in a spreadsheet at district. None of them agree. Frontline staff re-enter the same data over and over. Managers get reports late and can't fully trust them. Partners plug in their own tools that create yet another island. We're not short of systems — we're short of *coherence*. That's the problem Impilo vNext exists to solve.

---

## SLIDE 3 — What is Impilo vNext? (the one-line answer)

> **Impilo is a Health Operating System** — a trusted, governed, interoperable,
> person-centered national digital environment where health identities, records,
> workflows, services, and communities operate **coherently**.

- Not "another app." A shared foundation that *all* health apps run on.

**Speaker notes:**
Here's the single most important idea in this whole presentation. Impilo is not another application competing with your existing tools. Think of it the way your phone works: your phone has an operating system — Android or iOS — and then many apps run on top of it. The apps are different, but they share one identity, one set of rules, one secure foundation. Impilo vNext is the *operating system for health* in Zimbabwe. Facility systems, programme tools, partner applications — they become apps on a common, governed foundation instead of disconnected islands.

---

## SLIDE 4 — The doctrine in one sentence

> **One Health Operating System. One experience shell. One person anchor.**
> **Many roles, many IDs, many contexts, many connected entities — one governed runtime.**

**Speaker notes:**
This is our guiding sentence — our "north star." Read slowly, it says: there is one system and one look-and-feel; every record ties back to one real person; but that person can be a patient in one moment and, if they're a nurse, a provider in another; facilities, districts, programmes are all different contexts; and everything runs under one governed, auditable engine. Keep this sentence in mind — everything else today is just unpacking it.

---

## SLIDE 5 — "Operating System," not "application" — why it matters

**A single application** | **A Health OS (Impilo)**
--- | ---
Solves one problem | Provides a foundation for many
Its own login & data | One identity, one trust model
New tool = new island | New tool = a governed extension
Trust is assumed | Trust is enforced on every request

**Speaker notes:**
Why do we insist on the word "operating system"? Because it changes how we grow. When someone needs a new capability — say a new screening programme — we don't build a new island with its own login and its own patient list. We add a governed module that already knows who the patient is, already enforces consent, already writes to the shared record, already produces an audit trail. That's the difference between a system that gets *more coherent* as it grows versus one that gets *more fragmented*. Impilo is designed to get stronger as we add to it.

---

## SLIDE 6 — One person, one Health ID

**Person-centered identity**

- Every person has **one Health ID** — their single, lifelong anchor
- Attached to it: role IDs, context IDs, record IDs — but always **one person**
- No more "which register is the real one?"

**Speaker notes:**
At the heart of the OS is the person. Each individual gets one Health ID — a single, permanent anchor for their health identity. Everything else — their clinic visits, their prescriptions, their lab results, their programme enrolments — attaches to that one anchor. This is the end of "the same patient registered five times." It's also the foundation of a true longitudinal record: for the first time, a person's health story can follow them from a rural clinic to a provincial hospital to a pharmacy, as one continuous thread.

---

## SLIDE 7 — Many roles, many contexts — one governed model

**Sign in as a person → practise as a provider**

- **Who** you are: Health ID, Provider ID, Staff ID
- **Where** you're working: facility, department, ward, programme
- **What** you're allowed to do depends on *all* of it — checked every time

**Speaker notes:**
A real person is more than one thing. A nurse is a citizen with her own health record *and* a provider when she's on shift. So Impilo separates "who you are as a person" from "the professional capacity you're acting in right now." To prescribe or to open a patient's record, the system checks a whole picture: your identity, your professional licence, your organisation, the facility you're in, your purpose, the patient's consent, and the workflow state. Not once at login — *every single time*. That's what makes the system both flexible and safe.

---

## SLIDE 8 — The architecture, simply: 7 planes

**Impilo is organised into seven coherent "planes"**

1. **Trust** — identity, consent, "are you allowed?"
2. **Registry** — the canonical lists (people, facilities, products)
3. **Clinical** — the health record and care workflows
4. **Data** — longitudinal records, analytics, reporting
5. **Integration** — standards-based connection to other systems
6. **Experience** — the unified shell everyone actually uses
7. **Enterprise** — audit, governance, operations

**Speaker notes:**
For those who like to see the machine, here's the shape of it — seven planes, each with a clear job. Trust decides who's allowed to do what. Registry keeps the master lists so we all mean the same thing by "Chitungwiza Clinic" or "amoxicillin." Clinical is the actual care and records. Data turns all of it into insight. Integration is how we speak standard languages — FHIR — to partners and national systems. Experience is the screen you and I touch. And Enterprise is the audit and governance underneath. You don't need to memorise these — the point is that there's a deliberate, non-negotiable structure, not a pile of features.

---

## SLIDE 9 — Trust-first: privacy and consent are enforced, not assumed

**Every request passes through the trust plane before anything happens**

- Access decisions weigh **10 dimensions** (identity, role, consent, purpose, context…)
- **No personal identifying information** sits in the shared clinical record store
- Every meaningful action leaves an **audit trail**

**Speaker notes:**
This is the slide I'd ask our partners and our HQ colleagues to pay special attention to. Privacy in Impilo is not a policy on paper — it is enforced by the software on every request. Before any service does anything, the request is checked against ten dimensions: who you are, your role, your organisation, the patient's consent, your purpose, and more. And we deliberately keep personal identifying details *separate* from the shared clinical record — the record uses a coded person ID, and the names and details live behind a stricter gate. Everything meaningful is logged. This is how we earn the public's trust to run a national system.

---

## SLIDE 10 — One experience, many roles

**The Unified Experience Shell**

- One coherent shell — not fragmented portals
- Adapts what you see and can do to your **role and context**
- "Unified" ≠ "identical" — a nurse's workspace differs from a manager's, but shares one trust model

**Speaker notes:**
Everybody works in one shell — one consistent, learnable experience — rather than a different portal for every programme. But unified doesn't mean everyone sees the same thing. The shell adapts: a facility nurse sees her patients and her tasks; a district manager sees her facilities and her indicators; a partner sees the slice they're authorised for. Same foundation, same rules, tailored view. That means training is easier, mistakes are fewer, and moving between roles or facilities doesn't mean learning a whole new system.

---

## SLIDE 11 — More than clinical: a consumer-grade wellness layer

**Health is not only what happens in a facility**

- Genuine wellness pillars: diet, sleep, fitness, coaching, clubs & communities
- **Graduated friction**: light-touch for wellness & search → maximum rigour for prescribing & claims
- Meets people where they are — proactive, conversational, person-centred

**Speaker notes:**
Impilo isn't only for the clinic. A big part of health happens in daily life, so the OS includes a real, consumer-grade wellness layer — diet, sleep, fitness, coaching, community clubs. And it's smart about friction: browsing wellness content should feel effortless, while prescribing a controlled medicine or submitting a claim should be deliberate and tightly controlled. Same system, appropriate rigour for the moment. This is how we move from a system people are *made* to use to one they *want* to use.

---

## SLIDE 12 — What it means for FACILITY teams

**Less re-typing. More care.**

- One patient, one record — found instantly, no duplicate registration
- Guided workflows with built-in safety checks (Nompilo guidance never overrides your judgement)
- Works with real-world constraints (offline / intermittent connectivity in scope)

**Speaker notes:**
To my facility colleagues — this is for you. The promise is simple: less time re-typing the same patient details, more time with patients. You register a person once. You find them instantly. The system guides you through workflows and flags safety issues — but it never overrides your clinical judgement, and it always records what it advised. And we know connectivity is real: offline and intermittent-network behaviour is part of the design, not an afterthought. This is meant to reduce your burden, not add to it.

---

## SLIDE 13 — What it means for DISTRICT & PROVINCIAL managers

**Trusted numbers, closer to real time**

- Indicators roll up from real transactions — not hand-compiled spreadsheets
- See your facilities, your programmes, your gaps — in one place
- Same data everyone else sees — one version of the truth

**Speaker notes:**
For district and provincial managers, the change is about *trust and timeliness*. Today your reports are assembled by hand from many sources and arrive late. In Impilo, indicators roll up automatically from the actual transactions happening at facilities — the same underlying data, aggregated for your level. You see your facilities and programmes in one place, and when you question a number, you can trace it back to its source. One version of the truth, from the clinic bench to the provincial office.

---

## SLIDE 14 — What it means for HQ & national leadership

**A governed national platform, built to last**

- National-scale identity, registries, and longitudinal data
- Governance, security, and audit built into the foundation
- Extensible: new programmes are modules, not new systems
- Standards-based (FHIR) — ready for interoperability commitments

**Speaker notes:**
For HQ and national leadership, Impilo vNext is strategic infrastructure. It gives us a national health identity, canonical registries, and a longitudinal data foundation — with governance, security and audit built in from the start rather than bolted on. Critically, it's *extensible*: when a new programme or donor initiative comes, it becomes a governed module on the platform, not another vertical silo we'll spend years trying to integrate. And because it speaks international standards like FHIR, it's built to honour our interoperability commitments and to plug into regional and global health data exchange.

---

## SLIDE 15 — What it means for PARTNERS

**Build on a trusted foundation — don't rebuild it**

- Standards-based integration points (FHIR) — connect, don't duplicate
- Your solution inherits identity, consent, and audit from the platform
- Clear extension model: add capability without creating a new island
- Governance ensures the person, and their consent, always come first

**Speaker notes:**
To our partners — this is an invitation. Instead of each initiative building its own patient list, its own login, its own island, you build *on* the platform. You connect through standard interfaces, and your solution automatically inherits the shared identity, consent enforcement, and audit trail. That means faster deployment for you and coherence for the country. The governance model protects everyone — the person and their consent always come first — and that's exactly what makes the platform something you can build a durable partnership on.

---

## SLIDE 16 — Governance & safety guardrails

**Coherence by design — enforced, not hoped for**

- One source of truth per domain — no duplicate patient/provider/facility models
- Every production capability: authorised, audited, observable, tested
- Nompilo guidance is advisory and **always auditable**
- Person-centred and consent-driven at every layer

**Speaker notes:**
A quick word on discipline, because it's what keeps a national system from decaying. We enforce single sources of truth — there is one patient model, one provider model, one facility model, and we don't allow duplicates to creep in. Every production capability must be authorised, audited, observable, and tested before it ships. Any intelligent guidance is advisory and fully traceable — it supports professionals, never replaces their accountability. These aren't slogans; they're rules the platform enforces on itself.

---

## SLIDE 17 — Where we are today

**From doctrine to working system**

- Core planes and services are built and running in preview
- Identity, trust, clinical, data, experience, wellness surfaces live
- Continuous, verified delivery — small, safe, auditable increments
- *(Insert current milestone / metrics here for your audience)*

**Speaker notes:**
So where are we? This is not a concept on a whiteboard. The core planes and services are built and running in our preview environment — identity and trust, clinical records, the data layer, the experience shell, and the wellness surfaces. We deliver in small, verified increments so that every step is safe and auditable. *[Presenter: drop in your latest concrete milestone or metric here — e.g. number of services live, environments certified, or a recent demo — the audience will remember one real proof point more than any slide.]*

---

## SLIDE 18 — The roadmap ahead

**What's next**

- Deepen live cross-service workflows end-to-end
- Broaden facility rollout and user onboarding
- Expand partner integrations on the standard interfaces
- Strengthen offline/federated operation for the last mile

**Speaker notes:**
Looking ahead, our priorities are: closing the loop on live end-to-end workflows across services; broadening rollout and onboarding real users at facilities; opening the door wider to partner integrations; and hardening the last-mile experience so the system is dependable where connectivity is hardest. We'll keep bringing progress to this meeting — honestly, including the gaps — because trust is built by being straight about where we are.

---

## SLIDE 19 — Why this matters for Zimbabwe

**One coherent national health environment**

- A citizen's health story follows them, safely, wherever they go
- Managers lead with trusted, timely information
- Partners contribute without fragmenting the system
- A foundation we own, govern, and grow — for the long term

**Speaker notes:**
Let me zoom back out. What we're really building is a country where a person's health story follows them safely from clinic to hospital to pharmacy; where managers at every level lead with information they can trust; where partners strengthen the system instead of splintering it; and where the digital foundation of our health system is something we own and govern for the long term. That's the promise of a Health Operating System — and it's within reach.

---

## SLIDE 20 — Close & discussion

**Impilo vNext — one system, many roles, one person at the centre**

- Questions & discussion
- Contact: [name · role · email]
- Learn more: internal doctrine & architecture docs

**Speaker notes:**
To close on the sentence we started with: one Health Operating System, one experience, one person at the centre — many roles, many contexts, one governed runtime. Wherever you sit — facility, district, province, HQ, or partner — there's a place for you in this, and a benefit for you from it. Thank you. I'd love your questions and your thinking on where we go next.

---

## APPENDIX A — Quick glossary (for the diverse audience)

| Term | Plain meaning |
|------|---------------|
| Health OS | The shared foundation all health apps run on |
| Health ID | A person's single, lifelong health anchor |
| Plane | One of the 7 organised layers of the system |
| Trust plane | The part that decides "are you allowed?" |
| Longitudinal record | A person's health story over time, in one thread |
| FHIR | The international standard language for health data |
| Experience shell | The single screen/workspace everyone uses |
| Consent-driven | Nothing happens without a valid legal basis |
| Nompilo guidance | Advisory help that never overrides a professional |

## APPENDIX B — Audience-tailored one-liners (if you need to improvise)

- **Facility user:** "Register once, find instantly, be guided safely."
- **District manager:** "Your indicators, from real transactions, traceable to source."
- **Provincial manager:** "One version of the truth across all your facilities."
- **HQ leadership:** "National identity, governance, and data — built to last and to extend."
- **Partner:** "Build on our trusted foundation instead of rebuilding it."

## APPENDIX C — Anticipated tough questions

- *"Is this replacing my current system?"* → Over time it becomes the coherent home for what those systems do — but we migrate deliberately, not overnight, and never at the cost of care.
- *"Is patient data safe?"* → Privacy is enforced by the software on every request; identifying details are separated from the clinical record; everything is audited.
- *"What if there's no network?"* → Offline and intermittent-connectivity operation is part of the design for the last mile.
- *"Why not just buy a product?"* → We need a national, governed, extensible foundation we own — not another island we'll spend years integrating.
- *"How do I know it's real?"* → It's running in preview today; happy to show a live walkthrough after this session.
