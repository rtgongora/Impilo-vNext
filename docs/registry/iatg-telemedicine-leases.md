# IATG Leases — Telemedicine

Migration-number leases for telemedicine work, in the same registry as the clinical domain packs.

## 1. Bands

| Service | Band | Rationale |
|---|---|---|
| `pct-service` | **V500–V529** | Teleconsult sessions are modelled as referral packages, so telemedicine adds columns to `pct_referral_packages`. Every band below V460 is already leased — Paediatrics/IMAM `V400–V429`, RMNP `V430–V459` (with `V438–V459` reserved), Adult `V100–V129`, Surgery `V300–V329`, Emergency `V070–V099` + `V200s`. V500 is the first number in clearly unclaimed space, not the next free number. |

Telemedicine's original schema landed as `V009__virtual_encounter_referrals_telehealth.sql`, before
banding existed. That file stays where it is; everything from 2026-07-28 takes a number in V500–V529.

## 2. Taken

| Version | File | What |
|---|---|---|
| `V500` | `V500__teleconsult_session_lifecycle.sql` | `session_scheduled_at`, `session_started_at` on `pct_referral_packages` + partial index on consults under way |

`V501–V529` reserved.

## 3. Why session lifecycle needed columns at all

The referral model recorded `submitted_at` (paperwork sent) and `completed_at` (consult closed, which
already refuses to close without a note). It recorded nothing about the consultation itself, so the
telemedicine list could not distinguish a consult under way from one merely referred. The shell
declares `scheduled_at` and `started_at` and rendered them empty.

Filling them from `submitted_at` would have reported the referral's paperwork time as the
consultation's start — on a screen a clinician reads to know whether a consult has begun. So the
columns are real, and `session_started_at` is set on the first waiting-room admission and never moved
afterwards: a consult starts once, and a reconnect or a second participant joining must not reset it.

There is deliberately **no** `session_ended_at`. For a telemedicine referral, completion *is* the end
of the consultation. A second end timestamp would create two answers to one question.
