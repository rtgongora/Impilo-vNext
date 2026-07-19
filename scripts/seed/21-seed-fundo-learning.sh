#!/usr/bin/env bash
# =============================================================================
# 21 — Fundo (learning-service) starter catalogue seed.
#
# API-DRIVEN on purpose: every course/module/lesson/assessment/pathway goes
# through the live ingress (Traefik → envoy → experience-bff → learning-service)
# as the trainer persona, so running this seed IS a write-path proof — a broken
# authoring lane fails the seed instead of being papered over by SQL inserts.
#
# Idempotent: course `code` is the marker — codes already present in the
# catalogue are skipped, so re-runs are safe (per-row guard, not a global one).
#
# Requires: trainer.chikafu persona (14-seed-persona-truth-pack-varapi.sql /
# realm import) with the TRAINER realm role.
#
# Env:
#   PREVIEW_URL   default https://impilo.mohcc.gov.zw
#   RESOLVE_IP    if set (e.g. 127.0.0.1), curl --resolve <host>:443:<ip> —
#                 needed on the estate VM itself (public IP does not hairpin)
#   TENANT_ID     default 00000000-0000-4000-8000-000000000001
#   AUTHOR_USER / AUTHOR_PASS  default trainer.chikafu / ImpiloTest123!
# =============================================================================
set -uo pipefail

PREVIEW_URL="${PREVIEW_URL:-https://impilo.mohcc.gov.zw}"
TENANT_ID="${TENANT_ID:-00000000-0000-4000-8000-000000000001}"
AUTHOR_USER="${AUTHOR_USER:-trainer.chikafu}"
AUTHOR_PASS="${AUTHOR_PASS:-ImpiloTest123!}"
RESOLVE_IP="${RESOLVE_IP:-}"
RUN="fundo-seed-$(date +%s)"
LEARN="$PREVIEW_URL/internal/v1/learning/v11"
CREATED=0; SKIPPED=0; FAILED=0

CURL_EXTRA=()
if [ -n "$RESOLVE_IP" ]; then
  HOST=$(echo "$PREVIEW_URL" | sed -E 's#https?://##; s#/.*##')
  CURL_EXTRA=(--resolve "$HOST:443:$RESOLVE_IP")
fi
ccurl() { curl -sk -m 15 "${CURL_EXTRA[@]}" "$@"; }

jget() { python3 -c "import json,sys
try: d=json.load(sys.stdin)
except Exception: print(''); raise SystemExit
cur=d
for k in '$1'.split('.'):
    cur = cur.get(k) if isinstance(cur, dict) else None
    if cur is None: break
print(cur if cur is not None else '')" 2>/dev/null; }

hdrs() { HDRS=(-H "Content-Type: application/json" -H "X-Tenant-ID: $TENANT_ID" -H "X-Pod-ID: pod-seed" \
  -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN" \
  -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $RUN-$(cat /proc/sys/kernel/random/uuid | cut -c1-8)"); }

say()  { echo "[fundo-seed] $*"; }
fail() { FAILED=$((FAILED+1)); say "FAIL: $*"; }

# ── auth ─────────────────────────────────────────────────────────────────────
TOKEN=$(ccurl -X POST "$PREVIEW_URL/internal/v1/auth/login" \
  -H 'Content-Type: application/json' -H "X-Tenant-ID: $TENANT_ID" -H 'X-Pod-ID: pod-seed' \
  -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN" \
  -H "Idempotency-Key: $RUN-login" \
  -d "{\"email\":\"$AUTHOR_USER\",\"password\":\"$AUTHOR_PASS\"}" | jget data.attributes.token)
[ -n "$TOKEN" ] || { say "author login failed for $AUTHOR_USER — aborting"; exit 1; }
relogin() { TOKEN=$(ccurl -X POST "$PREVIEW_URL/internal/v1/auth/login" \
  -H 'Content-Type: application/json' -H "X-Tenant-ID: $TENANT_ID" -H 'X-Pod-ID: pod-seed' \
  -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN" \
  -H "Idempotency-Key: $RUN-relogin-$(date +%s)" \
  -d "{\"email\":\"$AUTHOR_USER\",\"password\":\"$AUTHOR_PASS\"}" | jget data.attributes.token); }

# ── idempotency marker: existing course codes ────────────────────────────────
hdrs
EXISTING=$(ccurl "$LEARN/catalog?limit=200" "${HDRS[@]}" | python3 -c "
import json,sys
d=json.load(sys.stdin)
for it in (d.get('data') or {}).get('items') or []:
    print(it.get('code') or '')" 2>/dev/null)

have() { echo "$EXISTING" | grep -qx "$1"; }

# course CODE | TITLE | DESCRIPTION | CATEGORY | LEVEL | DURATION | MANDATORY | CPD_ELIGIBLE | CPD_POINTS
mkcourse() {
  local code="$1" title="$2" desc="$3" cat="$4" level="$5" dur="$6" mand="$7" cpd="$8" pts="$9"
  hdrs
  ccurl -X POST "$LEARN/catalog" "${HDRS[@]}" -d "$(python3 -c "
import json
print(json.dumps({'code':'$code','title':'''$title''','description':'''$desc''','category':'$cat','level':'$level','status':'DRAFT','language':'en','estimatedDurationMinutes':$dur,'mandatory':'$mand'=='true','cpdEligible':'$cpd'=='true','cpdPoints':$pts}))")" | jget data.course.id
}

mkmodule() { # course_id title seq -> module id
  hdrs
  ccurl -X POST "$LEARN/courses/$1/modules" "${HDRS[@]}" \
    -d "{\"title\":\"$2\",\"sequence\":$3,\"status\":\"PUBLISHED\"}" | jget data.module.id
}

mklesson() { # module_id title seq body minutes
  hdrs
  ccurl -X POST "$LEARN/modules/$1/lessons" "${HDRS[@]}" -d "$(python3 -c "
import json
print(json.dumps({'title':'''$2''','contentType':'TEXT','contentFormat':'MARKDOWN','contentBody':'''$4''','sequence':$3,'required':True,'estimatedDurationMinutes':$5,'status':'PUBLISHED'}))")" | jget data.lesson.id >/dev/null
}

mkquiz() { # course_id title -> assessment id
  hdrs
  ccurl -X POST "$LEARN/assessments" "${HDRS[@]}" \
    -d "{\"courseId\":\"$1\",\"title\":\"$2\",\"assessmentType\":\"QUIZ\",\"passMark\":70,\"maxAttempts\":3,\"status\":\"PUBLISHED\"}" | jget data.assessment.id
}

mkq() { # assessment_id seq prompt optA optB optC correctIdx
  hdrs
  ccurl -X POST "$LEARN/assessments/$1/questions" "${HDRS[@]}" -d "$(python3 -c "
import json
opts=['$4','$5','$6']
print(json.dumps({'questionType':'MCQ','prompt':'''$3''','optionsJson':json.dumps(opts),'correctAnswerJson':json.dumps($7),'sequence':$2,'points':1}))")" >/dev/null
}

publish() { hdrs; ccurl -X PUT "$LEARN/catalog/$1" "${HDRS[@]}" -d '{"status":"PUBLISHED"}' | jget data.course.status; }

# seed_course wraps the marker guard + assembly + publish
# args: code title desc category level duration mandatory cpd pts m1 m2 q1 q2
lookup_course_id() { # code -> id (from the unfiltered catalogue read)
  hdrs
  ccurl "$LEARN/catalog?limit=200" "${HDRS[@]}" | python3 -c "
import json,sys
d=json.load(sys.stdin)
for it in (d.get('data') or {}).get('items') or []:
    if it.get('code') == '$1':
        print(it.get('id') or ''); break" 2>/dev/null
}

seed_course() {
  local code="$1"
  if have "$code"; then
    SKIPPED=$((SKIPPED+1)); say "skip (exists): $code"
    eval "COURSE_${code//[-.]/_}=$(lookup_course_id "$code")"
    return 0
  fi
  relogin
  local id; id=$(mkcourse "$@")
  if [ -z "$id" ]; then fail "create $code"; return 1; fi
  local m1 m2
  m1=$(mkmodule "$id" "${10}" 1); m2=$(mkmodule "$id" "${11}" 2)
  [ -n "$m1" ] && {
    mklesson "$m1" "Why this matters" 1 "${12}" 10
    mklesson "$m1" "Core concepts and definitions" 2 "${13}" 15
  }
  [ -n "$m2" ] && {
    mklesson "$m2" "Applying it in your facility" 1 "${14}" 15
    mklesson "$m2" "Common pitfalls and how to avoid them" 2 "${15}" 10
  }
  local quiz; quiz=$(mkquiz "$id" "End-of-course knowledge check")
  if [ -n "$quiz" ]; then
    mkq "$quiz" 1 "${16}" "${17}" "${18}" "${19}" "${20}"
    mkq "$quiz" 2 "${21}" "${22}" "${23}" "${24}" "${25}"
  fi
  local st; st=$(publish "$id")
  if [ "$st" = "PUBLISHED" ]; then CREATED=$((CREATED+1)); say "created + published: $code ($id)"; echo "$id" >> /tmp/fundo-seed-ids.txt; else fail "publish $code (status=$st)"; fi
  eval "COURSE_${code//[-.]/_}=$id"
}

: > /tmp/fundo-seed-ids.txt

seed_course "FUNDO-EHR-101" "Introduction to the Impilo EHR" \
  "Working confidently in the Impilo One Experience Shell: signing in, role activation, finding patients, capturing encounters and using Nompilo guidance safely." \
  "EHR_BASICS" "FOUNDATION" 50 false true 2 \
  "Getting around the shell" "Documenting care" \
  "Impilo is Zimbabwe's National Health Operating System. One person, one Health ID — every clinical action you record follows the patient across facilities. This lesson walks the shell: Start menu, context switcher, and your role-scoped workspaces." \
  "Actor, context and purpose: every request you make carries who you are, where you are working and why. Understanding trust headers helps you understand why some actions need a facility context or an activated Provider ID." \
  "Open a patient chart from search, review the longitudinal record, and capture a structured encounter note. Draft entries autosave; signing commits them to the record with your Provider ID." \
  "Never share a signed-in session, never capture care under someone else's identity, and check the active facility chip before you order or prescribe — context mistakes are the top audit finding." \
  "What anchors a patient's record across every Impilo facility?" "Their Health ID" "Their facility file number" "Their phone number" 0 \
  "Before prescribing, a clinician must have:" "An activated Provider ID and facility context" "Only a username and password" "A paper authorisation" 0

seed_course "FUNDO-IPC-201" "Infection Prevention and Control Essentials" \
  "Standard and transmission-based precautions for Zimbabwean facilities: hand hygiene, PPE, sharps, decontamination and outbreak-ready IPC routines." \
  "IPC" "INTERMEDIATE" 90 true true 4 \
  "Standard precautions" "Transmission-based precautions" \
  "Healthcare-associated infections are preventable. The WHO five moments of hand hygiene — before touching a patient, before clean procedures, after body-fluid exposure, after touching a patient, after touching surroundings — are the backbone of safe care." \
  "Personal protective equipment protects you and the next patient: selection depends on the anticipated exposure, donning order is gown-mask-eyewear-gloves, and doffing is where most self-contamination happens." \
  "Map your facility's IPC routine: decontamination flow for instruments, safe sharps disposal at point of use, linen and waste streams, and the IPC focal person's audit calendar." \
  "During suspected cholera, measles or viral haemorrhagic fever cases, escalate to transmission-based precautions immediately and notify the district rapid response team — do not wait for laboratory confirmation." \
  "When does most PPE self-contamination happen?" "During doffing (removal)" "During donning" "While wearing gloves" 0 \
  "The first response to a suspected VHF case is:" "Isolate, apply transmission-based precautions, notify the RRT" "Wait for lab confirmation" "Transfer the patient by public transport" 0

seed_course "FUNDO-PV-301" "Pharmacovigilance and ADR Reporting" \
  "Recognising, managing and reporting adverse drug reactions through Impilo's patient-safety lane — feeding Zimbabwe's national medicines-safety signal detection." \
  "PHARMACOVIGILANCE" "INTERMEDIATE" 60 false true 3 \
  "Recognising adverse drug reactions" "Reporting and signal detection" \
  "An adverse drug reaction is a harmful, unintended response at normal doses. Type A reactions are dose-dependent and predictable; Type B are idiosyncratic — rarer but often severe. High-burden programmes (ART, TB, malaria) make Zimbabwean vigilance data globally significant." \
  "Causality assessment is structured guesswork done honestly: temporality, dechallenge, rechallenge and alternative explanations. You report suspicions, not certainties — the national centre aggregates signals." \
  "Report through the Impilo patient-safety module: the structured ADR form captures the medicine, batch, reaction and outcome, and routes to the national pharmacovigilance centre without re-keying." \
  "Under-reporting is the enemy: report serious reactions, unexpected reactions, and all reactions to new medicines — even when the causal link feels weak. One report can be the signal." \
  "A Type B adverse drug reaction is:" "Idiosyncratic and not dose-dependent" "Predictable from pharmacology" "Always mild" 0 \
  "You should report a suspected ADR when:" "You suspect it, even without proof of causality" "Only after rechallenge confirms it" "Only for fatal outcomes" 0

seed_course "FUNDO-MNH-210" "Maternal and Newborn Danger Signs" \
  "Rapid recognition and referral of obstetric and neonatal emergencies at primary level: the danger signs every health worker must never miss." \
  "MATERNAL_HEALTH" "INTERMEDIATE" 75 true true 4 \
  "Maternal danger signs" "Newborn danger signs" \
  "Most maternal deaths follow one of five paths: haemorrhage, hypertensive disease, sepsis, obstructed labour, unsafe abortion. Each has warning signs visible at primary level hours before catastrophe — bleeding, severe headache with visual changes, fever with foul discharge, labour beyond 12 hours." \
  "The referral decision is the life-saving act: stabilise (uterotonics, MgSO4 loading dose, IV fluids, antibiotics), communicate ahead via Impilo's referral lane, and move — do not observe a deteriorating mother at a clinic." \
  "A newborn who feeds poorly, is lethargic, has fast breathing (≥60/min), chest indrawing, fever or feels cold has possible serious bacterial infection: give the first dose of antibiotics and refer — the first 24 hours decide survival." \
  "Kangaroo mother care for small babies, delayed cord clamping, immediate skin-to-skin and early breastfeeding are primary-level interventions with hospital-level impact. Record them; the data drives district quality reviews." \
  "A labouring mother with severe headache and blurred vision needs:" "MgSO4 loading dose and urgent referral" "Paracetamol and observation" "Discharge with advice" 0 \
  "A newborn breathing 70 times per minute is:" "A danger sign — treat and refer" "Normal for newborns" "Only concerning with fever" 0

seed_course "FUNDO-EMC-110" "Emergency Triage Assessment and Treatment" \
  "ETAT-based triage for first-contact facilities: sorting the queue by clinical urgency in under a minute per patient, not by arrival order." \
  "EMERGENCY_CARE" "FOUNDATION" 60 false true 3 \
  "The triage mindset" "Emergency signs and priority signs" \
  "Triage is sorting: Emergency (treat now), Priority (see soon), Queue (can wait). Deaths in the waiting line are triage failures. Every patient gets a rapid look at airway, breathing, circulation, consciousness and severe dehydration before anyone opens a register." \
  "The ABCD check takes twenty seconds when practised: obstructed airway, central cyanosis, severe respiratory distress, cold hands with weak fast pulse, unconsciousness, convulsions — any one means emergency treatment starts immediately." \
  "Priority signs — the TPR list: tiny babies, temperature, pallor, poisoning, pain (severe), respiratory distress, restlessness, referral urgent, malnutrition, oedema, burns. These jump the queue without opening an emergency bay." \
  "Re-triage is not optional: the queue is dynamic and the child who was 'green' an hour ago may now be fitting. Assign a re-look rhythm and record triage outcomes in Impilo so the flow data is honest." \
  "Triage orders the queue by:" "Clinical urgency" "Arrival time" "Payment status" 0 \
  "Cold hands with a weak fast pulse in a child indicate:" "Circulatory failure — emergency" "Normal cold weather response" "A priority (not emergency) sign" 0

seed_course "FUNDO-DG-120" "Health Data Privacy and Consent" \
  "Handling patient data lawfully inside Impilo: consent, purpose of use, the audit trail, and what 'no PII in the wrong lane' means for daily work." \
  "DATA_GOVERNANCE" "FOUNDATION" 45 true false 0 \
  "Why privacy is patient safety" "Consent and purpose of use in practice" \
  "A health record is a trust artefact: patients disclose because they believe disclosure stays inside the care relationship. Every Impilo access is logged with who, where and why — the audit chain is serialized and tamper-evident, and spot audits are routine." \
  "Purpose of use is not a formality: TREATMENT, PAYMENT, RESEARCH and EMERGENCY unlock different views of the record. Choosing an untrue purpose is an offence, and break-glass access pages the privacy officer in real time." \
  "Consent in Impilo is granular and revocable: a patient can share their record with a specific provider, for a specific time, and see who looked. Respect a closed consent — clinical curiosity is not a lawful basis." \
  "Practical hygiene: no patient data in personal messaging apps, no screenshots of charts, lock your session when you step away, and report suspected leaks the same day — early containment halves the harm." \
  "Break-glass emergency access:" "Is logged and pages the privacy officer" "Is invisible to the patient" "Needs no justification afterwards" 0 \
  "Sharing a chart screenshot on WhatsApp is:" "A reportable privacy breach" "Fine if the name is cropped" "Allowed within one facility" 0

seed_course "FUNDO-EPI-220" "Outbreak Detection and Surveillance Basics" \
  "From a strange cluster in your OPD register to a national alert: case definitions, immediate notifiable diseases and the weekly surveillance rhythm." \
  "PUBLIC_HEALTH" "INTERMEDIATE" 60 false true 3 \
  "Seeing the signal" "Reporting and responding" \
  "Surveillance starts at the front line: you cannot detect what you do not suspect. Know your immediately notifiable conditions — cholera, measles, VHF, acute flaccid paralysis, maternal death — and the weekly aggregate set. A cluster is three linked cases you cannot explain." \
  "Case definitions turn suspicion into data: suspected, probable, confirmed. Use the standard definition even when you personally disagree — comparability is what makes the national curve mean something." \
  "Impilo routes notifiable events to the surveillance lane the moment you save the diagnosis — but the system only sees what you record. Free-text 'gastro x4, same village' is invisible; coded cholera-suspected cases page the district." \
  "The first 24 hours of a cholera outbreak decide its size: ring the DMO, line-list from the register, secure water source information, and start ORS corners while awaiting the response team." \
  "Three linked cases you cannot explain are:" "A cluster worth reporting today" "Coincidence until lab-confirmed" "Only reportable at month end" 0 \
  "Immediately notifiable diseases are reported:" "Within 24 hours, by the fastest channel" "In the weekly aggregate" "Quarterly" 0

seed_course "FUNDO-CHW-130" "Community Health Worker Orientation" \
  "The village health worker's Impilo toolkit: household registration, referral slips that arrive before the patient, danger-sign screening and honest data." \
  "COMMUNITY_HEALTH" "FOUNDATION" 45 false false 0 \
  "Your role in the health system" "Working the toolkit" \
  "Community health workers are the health system's first mile: you see the pregnancy before the booking visit, the cough before the TB test, the child missing vaccinations. Impilo's community lane makes your observations part of the national record." \
  "Registration with consent: every household member you register gets linked to their Health ID or flagged for facility registration. Never register someone who refuses — record the refusal instead." \
  "A referral you send through Impilo arrives before the patient does: the clinic sees why you referred, and you see whether they attended — closing the loop that paper slips always leaked." \
  "Report what you actually did: visits made, screens done, referrals sent. Honest zeros are more valuable than invented coverage — the district plans supplies and outreach from your numbers." \
  "A community referral through Impilo:" "Is visible to the clinic before arrival" "Is a paper slip only" "Cannot be followed up" 0 \
  "If a household refuses registration you should:" "Record the refusal and respect it" "Register them anyway" "Skip the household silently" 0

seed_course "FUNDO-WEL-101" "Staff Wellness and Burnout Prevention" \
  "Recognising burnout in yourself and your team, practical recovery routines, and using Impilo's wellness supports without stigma." \
  "WELLNESS" "FOUNDATION" 40 false false 0 \
  "Understanding burnout" "Building recovery routines" \
  "Burnout is an occupational syndrome, not a personal failure: exhaustion, cynicism and reduced efficacy driven by chronic workplace stress. Health workers in high-load facilities carry double exposure — clinical trauma plus system strain." \
  "The early signs are behavioural: dreading shifts, irritability with patients, errors you would not normally make, sleep that stops restoring. Naming it early is the highest-yield intervention." \
  "Micro-recovery beats heroic leave: protected breaks, debriefs after hard cases, rotating the heaviest stations, and one genuinely off day. Team leads set the tone — modelled rest is permission to rest." \
  "Impilo's wellness pillar is for staff too: confidential self-checks, coaching, peer-support circles and referral to occupational health. Using support is a professional act — the record is separate from your HR file." \
  "Burnout is best described as:" "An occupational syndrome from chronic work stress" "A character weakness" "Ordinary tiredness" 0 \
  "The highest-yield burnout intervention is:" "Recognising and naming it early" "Waiting for annual leave" "Working through it" 0

seed_course "FUNDO-LDR-310" "Facility Leadership and Quality Improvement" \
  "Running a facility that learns: QI cycles on real Impilo data, honest incident review, and leading a team through change." \
  "LEADERSHIP" "ADVANCED" 90 false true 5 \
  "Leading with data" "The improvement cycle" \
  "Your facility dashboard is a mirror, not a scoreboard: waiting times, stockouts, referral completion and triage outcomes describe the system your patients actually experience. Leaders who argue with the mirror stay stuck." \
  "Incident review without blame: the question is never 'who failed' but 'what made failure easy'. Impilo's audit trail gives you the honest sequence — use it to fix the path, not to hunt the person." \
  "Plan-Do-Study-Act on one measure at a time: pick a gap the data shows, design a small change, run it for two weeks, study the same metric, keep or discard. Improvement is a rhythm, not a project." \
  "Change lands when the team owns it: co-design with the people who do the work, over-communicate the why, celebrate the first wins publicly, and give the sceptics real roles — resistance is usually unheard information." \
  "The purpose of incident review is:" "Fixing the conditions that made failure easy" "Identifying who to discipline" "Closing the file quickly" 0 \
  "A sound QI cycle changes:" "One measured thing at a time" "Everything at once" "Nothing without a committee" 0

# ── pathway: Clinical Foundations ────────────────────────────────────────────
relogin; hdrs
PATH_EXISTS=$(ccurl "$LEARN/pathways" "${HDRS[@]}" | grep -c 'FUNDO-PATH-CLIN' || true)
if [ "$PATH_EXISTS" -gt 0 ]; then
  say "pathway: skipped (FUNDO-PATH-CLIN exists)"
else
  hdrs
  PATH_ID=$(ccurl -X POST "$LEARN/pathways" "${HDRS[@]}" \
    -d '{"code":"FUNDO-PATH-CLIN","title":"Clinical Foundations Pathway","description":"The first three courses every clinical joiner completes: the EHR, infection prevention, and emergency triage.","targetCadres":"NURSE,CLINICIAN,CHW","status":"PUBLISHED"}' | jget data.pathway.id)
  if [ -n "$PATH_ID" ]; then
    SEQ=1
    for CV in COURSE_FUNDO_EHR_101 COURSE_FUNDO_IPC_201 COURSE_FUNDO_EMC_110; do
      CID="${!CV:-}"
      [ -z "$CID" ] && continue
      hdrs
      ccurl -X POST "$LEARN/pathways/$PATH_ID/items" "${HDRS[@]}" \
        -d "{\"courseId\":\"$CID\",\"sequence\":$SEQ,\"required\":true}" >/dev/null
      SEQ=$((SEQ+1))
    done
    say "pathway created: FUNDO-PATH-CLIN ($PATH_ID) with $((SEQ-1)) items"
  else
    fail "pathway create"
  fi
fi

echo ""
say "DONE: created=$CREATED skipped=$SKIPPED failed=$FAILED (run=$RUN)"
[ "$FAILED" -eq 0 ] || exit 1
