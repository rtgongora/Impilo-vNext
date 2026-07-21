# Provider Experience & Reputation — Foundational Doctrine

> **Canonical summary**: Provider ratings are fundamentally client feedback, service quality,
> safety, complaints, commendations and experience monitoring — so they are owned by **Rito**,
> as a bounded capability ("Rito Experience & Reputation"), and NOT by Varapi. Varapi remains the
> authoritative registry of *provider truth* (identity, cadre, qualifications, licence and
> registration status, scope of practice, restrictions and sanctions, affiliations) and may
> display only a governed, read-only, Rito-sourced experience summary. Ratings SHALL be
> multi-dimensional — never one universal five-star score — distinguishing client experience,
> access and process, professional-quality indicators, and safety and accountability, which SHALL
> NOT be casually blended. Ratings SHALL be contextual (bound to provider, facility, service
> point, encounter, role, modality, date, specialty and verified-interaction status) and SHALL
> NOT automatically modify any regulatory or employment status. **PCT** proves the interaction
> happened; **Tuso** explains where and under what service conditions; **TSHEPO** controls who may
> see identifiable feedback and protects clients from provider-level access to their identities.

> **Short doctrine line**: Varapi says who the provider is and whether they are authorised; Rito
> says how people experienced the service and whether concerns exist; PCT proves it happened; a
> rating never rewrites a licence.

This doctrine refines the Impilo foundational doctrine
([`health-os-doctrine.md`](health-os-doctrine.md)) and applies the ownership backbone of the
[service-relationship doctrine](service-relationship-doctrine.md) to one contested capability:
provider ratings. It supersedes nothing; §7 states what it inherits.

---

## 1. Ownership: Rito Owns Reputation; Varapi Owns Provider Truth

**Rito SHALL be the system of record** for the rating system, including: rating questionnaires and
scoring models; free-text feedback; compliments, concerns and complaints; moderation and dispute
workflows; anonymous versus identified feedback; verified-interaction status; provider responses;
appeals and correction requests; quality trends, alerts and escalation; aggregated provider,
facility and service scores; and protection against manipulation, retaliation and review bombing.

**Varapi SHALL NOT own or store ratings or reputation.** Varapi remains authoritative for provider
identity, professional category and cadre, qualifications, licence and registration status, scope
of practice, restrictions and sanctions, facility affiliations, employment/deployment context and
specialist credentials. Varapi MAY expose a **read-only, Rito-sourced** rating summary on a
provider profile; the underlying ratings continue to come from Rito and are source-tagged.

> Do not turn Varapi into a mixture of professional registry, review website and disciplinary
> platform. Varapi composes and displays a governed summary; it never becomes the reputation
> system of record.

The capability is a **bounded capability within Rito** ("Rito Experience & Reputation"); it does
not need to become a separate microservice.

---

## 2. Four Non-Blendable Rating Domains

A single star rating is too blunt for healthcare — it can unfairly punish emergency clinicians,
providers managing complicated cases, or professionals in understaffed facilities. The system
SHALL distinguish at least four domains, which SHALL NOT be casually blended:

| Domain | Examples |
|---|---|
| **Client experience** | Respect and dignity, communication, privacy, explanation of care, shared decision-making |
| **Access and process** | Waiting time, appointment availability, continuity, responsiveness |
| **Professional quality indicators** | Guideline adherence, documentation, follow-up completion |
| **Safety and accountability** | Complaints, incidents, upheld findings, corrective actions |

> Patient experience is not the same as clinical competence, and neither may be collapsed into one
> unexplained score.

### 2.1 Public versus restricted disclosure (SHALL)

- The **public-facing profile** SHALL primarily show **verified experience** measures (client
  experience; selected access-and-process).
- **Professional-quality and safety/accountability** indicators SHALL generally be restricted to
  authorised providers, facility leadership, regulators and quality teams — gated by TSHEPO.
- Named-provider discovery SHALL be governed by policy, provider consent and the nature of the
  service; public discovery SHALL prefer *"paediatric services are available"* over an
  unrestricted directory of individual workers.

---

## 3. Ratings SHALL Be Contextual

Every rating SHALL be connected to: provider; facility; service point; encounter or episode;
provider role during that encounter; modality (physical, virtual, outreach or emergency); date and
reporting period; specialty or service; and whether the interaction was verified.

This prevents a rating for one difficult emergency-department experience from becoming an
unexplained permanent judgement on the provider's entire career, and (with the
[service-relationship snapshot rule](service-relationship-doctrine.md#5-preserve-historical-context-the-snapshot-rule))
preserves the correct historical context when a provider later transfers, changes employer or
changes name.

---

## 4. The Regulation Firewall

A poor rating SHALL NOT automatically modify:

- Provider licence status
- Scope of practice
- Employment status
- TSHEPO access
- Professional registration standing

Rito MAY detect a repeated pattern and refer a case, through a **governed workflow**, to: facility
quality management; the Quality Assurance & Patient Safety department; Human Resources; the relevant
professional council; or HPA where applicable. **Only the appropriate authority may make a
regulatory or disciplinary determination.**

> A rating is evidence for a governed human process — never itself a sanction. Rito raises a
> referral; the authority decides.

---

## 5. Trust, Verification & Anti-Manipulation

- **PCT verifies the interaction.** PCT confirms that the client actually had a consultation,
  admission, procedure, telemedicine session or referral involving the provider. Feedback SHALL be
  labelled by trust class: verified interaction, unverified public feedback, anonymous internal
  report, staff report, caregiver/guardian report, or regulatory referral.
- **Verified-interaction gating.** Ratings that carry weight in public summaries SHALL be tied to a
  PCT-verified interaction; one verified rating per encounter.
- **Retaliation and identity protection.** TSHEPO SHALL control who may see identifiable feedback
  and SHALL protect clients from provider-level access to their identities. Anonymous and
  identified feedback pathways SHALL both be supported.
- **Review-bombing and manipulation protection.** The system SHALL detect and moderate coordinated
  manipulation, retaliation and review bombing before such feedback affects a summary.
- **Provider response.** Providers SHALL be able to respond to feedback; responses are part of the
  record.

---

## 6. How the Services Work Together

```text
PCT / Clinical Service
       │  confirms a real completed interaction (ENCOUNTER_COMPLETED)
       ▼
Rito Experience & Reputation
       ├── records rating and narrative feedback
       ├── moderates and investigates
       ├── aggregates scores (per provider × facility × service point × domain × period)
       └── generates quality alerts + governed referrals
       │
       ▼
Varapi provider profile   ─┐
Tuso facility profile      ├── read-only, Rito-sourced, governed summaries
Ruvimbo provider-network   │
Impilo Performance         ─┘
```

Supporting roles: **Tuso** provides facility, service-point and organisational context (the same
provider may perform differently in different environments); **Khuluma** sends the post-visit
feedback request (SMS/WhatsApp/app/email/USSD); **Nompilo** guides the person through the questions
and explains what happens with their feedback; **Ruvimbo** may use carefully governed, validated
ratings in discovery, network monitoring and payer quality programmes — a poor public rating alone
SHALL NOT automatically remove a provider from a network; **Impilo Performance** presents trends
without owning the underlying feedback.

### 6.1 The composed public provider card

```text
Dr Tariro Moyo
Registered Medical Practitioner · Licence: Active          (source: Varapi)
Current service locations: Chinhoyi Provincial Hospital,
  Impilo Virtual Clinic                                     (source: Vashandi + Tuso)
Patient experience: 4.6 / 5 from 184 verified interactions  (source: Rito)
  Communication 4.7 · Respect & dignity 4.8 ·
  Waiting experience 4.1 · Explanation of care 4.6
Medical-aid networks: …                                     (source: Ruvimbo)
```

Every line is composed from its authoritative service and source-tagged. Varapi displays the
"Patient experience" block; Rito owns it.

---

## 7. Design Consequences

1. **Rito is the reputation system of record; Varapi is not.** Ratings live in Rito's schema;
   Varapi and Tuso surface read-only, source-tagged summaries only.
2. **Four domains, never one score.** Client experience, access/process, professional quality and
   safety/accountability are stored and displayed separately; public sees verified experience,
   authorised roles see the rest via TSHEPO.
3. **Every rating is contextual and mostly verified.** Provider + facility + service point +
   encounter + role + modality + date + specialty + verified flag; PCT is the verification gate.
4. **The regulation firewall is absolute.** No rating write ever mutates licence, scope,
   employment, TSHEPO access or registration; patterns become governed referrals to the authority.
5. **Manipulation is designed against.** Verified-interaction gating, one-rating-per-encounter,
   retaliation and review-bombing protection, and TSHEPO identity shielding are first-class, not
   afterthoughts.
6. **Bounded capability, not a new service.** "Rito Experience & Reputation" is built inside Rito
   (schema `rito`, tables `rit_*`), reusing its case/party/link/survey substrate.
