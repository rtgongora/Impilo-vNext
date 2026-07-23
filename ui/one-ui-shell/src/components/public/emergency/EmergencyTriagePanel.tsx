"use client";

/**
 * Nompilo emergency responder — a conversational, one-question-at-a-time triage
 * panel. Presents the deterministic danger-sign protocol (triage-protocol.ts)
 * as a guided Nompilo conversation:
 *
 * - danger signs escalate IMMEDIATELY (call-first banner + safe actions) and
 *   shorten the remaining flow to callback + location;
 * - location supports device GPS, landmark text, province and at-a-facility;
 * - nearby emergency care is surfaced through the real find-care lane;
 * - submission goes to the REAL anonymous SOS intake (daidzai behind the BFF)
 *   and the receipt shows the honest status timeline — never a dispatch claim;
 * - the conversation persists in sessionStorage so leaving and returning does
 *   not lose progress;
 * - Nompilo triages and guides. It never diagnoses — stated in the UI.
 */

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import {
  AlertTriangle,
  Check,
  Loader2,
  MapPin,
  Pencil,
  Phone,
  RotateCcw,
  Send,
  Sparkles,
} from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { EMERGENCY_CONTACTS, ZW_PROVINCES } from "@/config/emergency";
import { EmergencyLocationMapPicker } from "./EmergencyLocationMapPicker";
import { NearbyEmergencyCare } from "./NearbyEmergencyCare";
import { NompiloAskInline } from "./NompiloAskInline";
import { SosStatusTimeline } from "./SosStatusTimeline";
import {
  assessDanger,
  buildSummary,
  classifyUrgency,
  nextStep,
  SAFE_ACTIONS,
  toSosPayload,
  TRIAGE_QUESTIONS,
  URGENCY_GUIDANCE,
  type TriageAnswers,
  type TriageStepId,
} from "./triage-protocol";

const STORAGE_KEY = "impilo.emergency.triage.v1";

interface SosReceipt {
  requestReference?: string | null;
  message?: string | null;
}

interface PersistedState {
  answers: TriageAnswers;
  receipt?: SosReceipt | null;
}

function loadPersisted(): PersistedState | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as PersistedState) : null;
  } catch {
    return null;
  }
}

function NompiloBubble({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex items-start gap-2.5">
      <span className="mt-0.5 grid h-8 w-8 shrink-0 place-items-center rounded-full bg-violet-600 text-white">
        <Sparkles className="h-4 w-4" aria-hidden />
      </span>
      <div className="max-w-[85%] rounded-2xl rounded-tl-md bg-white p-3.5 text-[15px] leading-relaxed text-slate-800 shadow-sm ring-1 ring-slate-200">
        {children}
      </div>
    </div>
  );
}

function AnswerBubble({ children, onEdit }: { children: React.ReactNode; onEdit?: () => void }) {
  return (
    <div className="flex items-center justify-end gap-2">
      {onEdit && (
        <button
          type="button"
          onClick={onEdit}
          aria-label="Change this answer"
          className="grid h-8 w-8 place-items-center rounded-full text-slate-400 hover:bg-slate-100 hover:text-slate-700 focus:outline-none focus-visible:outline-2 focus-visible:outline-emerald-600"
        >
          <Pencil className="h-3.5 w-3.5" aria-hidden />
        </button>
      )}
      <div className="max-w-[80%] rounded-2xl rounded-tr-md bg-emerald-600 px-4 py-2.5 text-[14.5px] font-medium text-white">
        {children}
      </div>
    </div>
  );
}

export function EmergencyTriagePanel() {
  const [answers, setAnswers] = useState<TriageAnswers>({});
  const [multiDraft, setMultiDraft] = useState<string[]>([]);
  const [callbackDraft, setCallbackDraft] = useState("");
  const [callbackErr, setCallbackErr] = useState("");
  const [locationDraft, setLocationDraft] = useState("");
  const [provinceDraft, setProvinceDraft] = useState("");
  const [facilityDraft, setFacilityDraft] = useState("");
  const [locating, setLocating] = useState(false);
  const [locateNote, setLocateNote] = useState("");
  const [showMapPicker, setShowMapPicker] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState("");
  const [receipt, setReceipt] = useState<SosReceipt | null>(null);
  const [hydrated, setHydrated] = useState(false);
  const liveRef = useRef<HTMLDivElement>(null);

  // Restore a conversation in progress (user navigated away and came back).
  useEffect(() => {
    const persisted = loadPersisted();
    if (persisted) {
      setAnswers(persisted.answers ?? {});
      setReceipt(persisted.receipt ?? null);
    }
    setHydrated(true);
  }, []);

  // Persist on every change so back/return never loses state.
  useEffect(() => {
    if (!hydrated || typeof window === "undefined") return;
    try {
      window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ answers, receipt }));
    } catch {
      /* storage full/blocked — non-fatal */
    }
  }, [answers, receipt, hydrated]);

  const step: TriageStepId = useMemo(() => nextStep(answers), [answers]);
  const danger = useMemo(() => assessDanger(answers), [answers]);
  const question = TRIAGE_QUESTIONS[step];

  // Keep the active question in view as the conversation grows.
  useEffect(() => {
    if (hydrated && typeof liveRef.current?.scrollIntoView === "function") {
      liveRef.current.scrollIntoView({ block: "nearest", behavior: "smooth" });
    }
  }, [step, hydrated, receipt]);

  const answeredSteps = useMemo(() => {
    const ids: TriageStepId[] = [
      "situation",
      "conscious",
      "breathing",
      "bleeding",
      "redFlags",
      "onset",
      "age",
      "pregnancy",
      "conditions",
      "alone",
    ];
    return ids.filter((id) => answers[id as keyof TriageAnswers] !== undefined);
  }, [answers]);

  function answerLabel(id: TriageStepId): string {
    const q = TRIAGE_QUESTIONS[id];
    const value = answers[id as keyof TriageAnswers];
    if (Array.isArray(value)) {
      return value.map((v) => q.options?.find((o) => o.value === v)?.label ?? v).join(", ");
    }
    return q.options?.find((o) => o.value === value)?.label ?? String(value ?? "");
  }

  const choose = useCallback((id: TriageStepId, value: string) => {
    setAnswers((prev) => ({ ...prev, [id]: value }));
  }, []);

  function toggleMulti(value: string, exclusive?: boolean) {
    setMultiDraft((prev) => {
      if (exclusive) return prev.includes(value) ? [] : [value];
      const without = prev.filter((v) => v !== value && v !== "none");
      return prev.includes(value) ? without : [...without, value];
    });
  }

  function commitMulti(id: TriageStepId) {
    setAnswers((prev) => ({ ...prev, [id]: multiDraft.length ? multiDraft : ["none"] }));
    setMultiDraft([]);
  }

  function editStep(id: TriageStepId) {
    setAnswers((prev) => {
      const next = { ...prev };
      delete next[id as keyof TriageAnswers];
      return next;
    });
  }

  function commitCallback() {
    const trimmed = callbackDraft.trim();
    if (!/^[+0-9][0-9 ()-]{6,}$/.test(trimmed)) {
      setCallbackErr("Enter a valid phone number, e.g. 0771 234 567.");
      return;
    }
    setCallbackErr("");
    setAnswers((prev) => ({ ...prev, callbackNumber: trimmed }));
  }

  function shareLocation() {
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      setLocateNote("Location is not available on this device — describe the place instead.");
      return;
    }
    setLocating(true);
    setLocateNote("");
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setAnswers((prev) => ({ ...prev, lat: pos.coords.latitude, lng: pos.coords.longitude }));
        setLocating(false);
        setLocateNote("Device location captured.");
      },
      () => {
        setLocating(false);
        setLocateNote("We could not get your location — describe the place instead.");
      },
      { timeout: 8000, enableHighAccuracy: true },
    );
  }

  function commitLocation() {
    setAnswers((prev) => ({
      ...prev,
      locationDescription: locationDraft.trim() || prev.locationDescription,
      province: provinceDraft || prev.province,
      atFacility: facilityDraft.trim() || prev.atFacility,
      // GPS-only submissions are allowed; ensure at least something is set so
      // nextStep() advances.
      ...(locationDraft.trim() || provinceDraft || facilityDraft.trim() || prev.lat !== undefined
        ? {}
        : {}),
    }));
  }

  const locationCaptured =
    answers.lat !== undefined || Boolean(answers.locationDescription) || Boolean(answers.province);

  async function submit() {
    setSubmitting(true);
    setSubmitError("");
    try {
      const res = await apiClient.post<SosReceipt>(
        "/internal/v1/public/gateway/sos",
        toSosPayload(answers),
      );
      setReceipt(res);
    } catch (e) {
      const status = (e as { status?: number })?.status;
      if (status === 429) {
        setSubmitError(
          "The service is very busy right now. If this is a real emergency, call the numbers above immediately.",
        );
      } else if (status === 400) {
        setSubmitError("The callback number was not accepted — change it and try again.");
      } else {
        setSubmitError(
          "We could not send your request (network problem). It is saved on this device — try again, or call the numbers above now.",
        );
      }
    } finally {
      setSubmitting(false);
    }
  }

  function restart() {
    setAnswers({});
    setReceipt(null);
    setMultiDraft([]);
    setCallbackDraft("");
    setLocationDraft("");
    setProvinceDraft("");
    setFacilityDraft("");
    setSubmitError("");
    if (typeof window !== "undefined") window.sessionStorage.removeItem(STORAGE_KEY);
  }

  if (!hydrated) {
    return (
      <div className="mt-6 rounded-2xl bg-slate-100 p-6 text-sm text-slate-500">
        Loading the emergency responder…
      </div>
    );
  }

  // ── Receipt state: honest status, timeline, nearby care ────────────────────
  if (receipt) {
    return (
      <section
        data-testid="triage-receipt"
        aria-label="Emergency request receipt"
        className="mt-6 rounded-2xl border border-emerald-300 bg-emerald-50 p-5 sm:p-6"
      >
        <h3 className="flex items-center gap-2 text-lg font-bold text-emerald-900">
          <Check className="h-5 w-5" aria-hidden /> Your request for help has been received
        </h3>
        {receipt.requestReference && (
          <p className="mt-2 text-sm text-emerald-900">
            Reference:{" "}
            <span data-testid="triage-reference" className="font-mono font-semibold">
              {receipt.requestReference}
            </span>
          </p>
        )}
        <p className="mt-2 text-sm text-emerald-900">
          {receipt.message ||
            "A responder will call you back on the number you gave to confirm before anything is arranged."}
        </p>
        <p className="mt-2 text-sm font-semibold text-red-800">
          If the situation gets worse, call the emergency numbers now — do not wait for the callback.
        </p>

        <div className="mt-4 rounded-xl bg-white p-4 ring-1 ring-emerald-200">
          <h4 className="text-sm font-semibold text-slate-900">What happens next</h4>
          <SosStatusTimeline status="AWAITING_CALLBACK" callbackPending />
        </div>

        <NearbyEmergencyCare lat={answers.lat} lng={answers.lng} province={answers.province} />

        {/* Free-text side questions while waiting for the callback. */}
        <NompiloAskInline className="mt-3" />

        <div className="mt-4 flex flex-wrap gap-3">
          {receipt.requestReference && (
            <Link
              href={`/welcome/emergency/track?ref=${encodeURIComponent(receipt.requestReference)}`}
              data-testid="triage-track-link"
              className="rounded-lg bg-emerald-700 px-4 py-2.5 text-sm font-semibold text-white hover:bg-emerald-800"
            >
              Track this request →
            </Link>
          )}
          <button
            type="button"
            onClick={restart}
            className="inline-flex items-center gap-1.5 rounded-lg border border-emerald-300 bg-white px-4 py-2.5 text-sm font-semibold text-emerald-800 hover:bg-emerald-50"
          >
            <RotateCcw className="h-4 w-4" aria-hidden /> Start a new request
          </button>
        </div>
      </section>
    );
  }

  // ── Conversation ───────────────────────────────────────────────────────────
  return (
    <section
      data-testid="emergency-triage"
      aria-label="Nompilo emergency triage"
      className="mt-6 overflow-hidden rounded-2xl border border-slate-200 bg-slate-50"
    >
      <header className="flex items-center justify-between gap-3 border-b border-slate-200 bg-white px-4 py-3">
        <div className="flex items-center gap-2.5">
          <span className="grid h-9 w-9 place-items-center rounded-full bg-violet-600 text-white">
            <Sparkles className="h-4.5 w-4.5" aria-hidden />
          </span>
          <div>
            <p className="text-sm font-bold text-slate-900">Nompilo emergency responder</p>
            <p className="text-xs text-slate-500">
              Guided help — not a diagnosis. Calling is always fastest.
            </p>
          </div>
        </div>
        {answeredSteps.length > 0 && (
          <button
            type="button"
            onClick={restart}
            data-testid="triage-restart"
            className="inline-flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-xs font-medium text-slate-500 hover:bg-slate-100 hover:text-slate-800"
          >
            <RotateCcw className="h-3.5 w-3.5" aria-hidden /> Start again
          </button>
        )}
      </header>

      <div className="space-y-4 px-4 py-5 sm:px-6" aria-live="polite">
        {/* Danger escalation banner — appears the moment a danger sign is reported. */}
        {danger.danger && (
          <div
            data-testid="triage-danger"
            role="alert"
            className="rounded-xl border-2 border-red-400 bg-red-50 p-4"
          >
            <p className="flex items-center gap-2 text-[15px] font-bold text-red-800">
              <AlertTriangle className="h-5 w-5 shrink-0" aria-hidden />
              These are danger signs. Call for help now — do not wait.
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              {EMERGENCY_CONTACTS.map((c) => (
                <a
                  key={c.tel}
                  href={`tel:${c.tel}`}
                  className="inline-flex min-h-[44px] items-center gap-2 rounded-full bg-red-700 px-4 py-2 text-sm font-bold text-white hover:bg-red-800"
                >
                  <Phone className="h-4 w-4" aria-hidden /> {c.number}
                  <span className="font-normal opacity-90">{c.label}</span>
                </a>
              ))}
            </div>
            <ul className="mt-3 space-y-1.5">
              {danger.reasons.slice(0, 3).map(
                (reason) =>
                  SAFE_ACTIONS[reason] && (
                    <li key={reason} className="text-sm leading-relaxed text-red-900">
                      • {SAFE_ACTIONS[reason]}
                    </li>
                  ),
              )}
            </ul>
            <p className="mt-2 text-xs text-red-800/80">
              You can still finish the questions below so a responder can call you back.
            </p>
          </div>
        )}

        {/* Transcript of answered questions. */}
        {answeredSteps.map((id) => (
          <div key={id} className="space-y-2">
            <NompiloBubble>{TRIAGE_QUESTIONS[id].prompt}</NompiloBubble>
            <AnswerBubble onEdit={() => editStep(id)}>{answerLabel(id)}</AnswerBubble>
          </div>
        ))}
        {answers.callbackNumber && step !== "callback" && (
          <div className="space-y-2">
            <NompiloBubble>{TRIAGE_QUESTIONS.callback.prompt}</NompiloBubble>
            <AnswerBubble onEdit={() => editStep("callback" as TriageStepId)}>
              {answers.callbackNumber}
            </AnswerBubble>
          </div>
        )}
        {locationCaptured && step !== "location" && (
          <div className="space-y-2">
            <NompiloBubble>{TRIAGE_QUESTIONS.location.prompt}</NompiloBubble>
            <AnswerBubble
              onEdit={() =>
                setAnswers((prev) => ({
                  ...prev,
                  locationDescription: undefined,
                  province: undefined,
                  atFacility: undefined,
                  lat: undefined,
                  lng: undefined,
                }))
              }
            >
              {[
                answers.lat !== undefined ? "Device location shared" : null,
                answers.atFacility,
                answers.locationDescription,
                answers.province,
              ]
                .filter(Boolean)
                .join(" · ")}
            </AnswerBubble>
          </div>
        )}

        {/* Active question. */}
        <div ref={liveRef} className="space-y-3">
          <NompiloBubble>
            <p className="font-medium">{question.prompt}</p>
            {question.help && <p className="mt-1 text-sm text-slate-500">{question.help}</p>}
          </NompiloBubble>

          {question.kind === "choice" && !question.multi && (
            <div className="flex flex-wrap gap-2 pl-10" role="group" aria-label={question.prompt}>
              {question.options?.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  data-testid={`triage-opt-${opt.value}`}
                  onClick={() => choose(step, opt.value)}
                  className="min-h-[44px] rounded-full border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-800 transition hover:border-emerald-400 hover:bg-emerald-50 focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600"
                >
                  {opt.label}
                </button>
              ))}
            </div>
          )}

          {question.kind === "choice" && question.multi && (
            <div className="pl-10">
              <div className="flex flex-wrap gap-2" role="group" aria-label={question.prompt}>
                {question.options?.map((opt) => {
                  const selected = multiDraft.includes(opt.value);
                  return (
                    <button
                      key={opt.value}
                      type="button"
                      data-testid={`triage-opt-${opt.value}`}
                      aria-pressed={selected}
                      onClick={() => toggleMulti(opt.value, opt.exclusive)}
                      className={`min-h-[44px] rounded-full border px-4 py-2 text-sm font-medium transition focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 ${
                        selected
                          ? "border-emerald-600 bg-emerald-600 text-white"
                          : "border-slate-300 bg-white text-slate-800 hover:border-emerald-400 hover:bg-emerald-50"
                      }`}
                    >
                      {selected && <Check className="mr-1 inline h-3.5 w-3.5" aria-hidden />}
                      {opt.label}
                    </button>
                  );
                })}
              </div>
              <button
                type="button"
                data-testid="triage-multi-continue"
                onClick={() => commitMulti(step)}
                className="mt-3 min-h-[44px] rounded-lg bg-emerald-600 px-5 py-2 text-sm font-semibold text-white hover:bg-emerald-700 focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
              >
                Continue
              </button>
            </div>
          )}

          {question.kind === "callback" && (
            <div className="max-w-sm pl-10">
              <label htmlFor="triage-callback" className="sr-only">
                Callback number
              </label>
              <input
                id="triage-callback"
                data-testid="triage-callback"
                type="tel"
                inputMode="tel"
                value={callbackDraft}
                onChange={(e) => setCallbackDraft(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && commitCallback()}
                placeholder="e.g. 0771 234 567"
                aria-invalid={callbackErr ? "true" : undefined}
                aria-describedby={callbackErr ? "triage-callback-err" : undefined}
                className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-[15px] focus:border-emerald-500 focus:outline-none"
              />
              {callbackErr && (
                <p id="triage-callback-err" data-testid="triage-callback-err" className="mt-1 text-sm text-red-700">
                  {callbackErr}
                </p>
              )}
              <button
                type="button"
                data-testid="triage-callback-continue"
                onClick={commitCallback}
                className="mt-2 min-h-[44px] rounded-lg bg-emerald-600 px-5 py-2 text-sm font-semibold text-white hover:bg-emerald-700"
              >
                Continue
              </button>
            </div>
          )}

          {question.kind === "location" && (
            <div className="max-w-md space-y-2.5 pl-10">
              <button
                type="button"
                data-testid="triage-share-location"
                onClick={shareLocation}
                disabled={locating}
                className="inline-flex min-h-[44px] items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-800 hover:border-emerald-400 hover:bg-emerald-50 disabled:opacity-60"
              >
                {locating ? (
                  <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
                ) : (
                  <MapPin className="h-4 w-4 text-emerald-600" aria-hidden />
                )}
                {answers.lat !== undefined ? "Location shared ✓" : "Share my device location"}
              </button>
              {locateNote && <p className="text-xs text-slate-600">{locateNote}</p>}
              <button
                type="button"
                data-testid="triage-map-toggle"
                onClick={() => setShowMapPicker((v) => !v)}
                className="inline-flex min-h-[44px] items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-800 hover:border-emerald-400 hover:bg-emerald-50"
              >
                {showMapPicker ? "Hide the map" : "Point on a map instead"}
              </button>
              {showMapPicker && (
                <EmergencyLocationMapPicker
                  lat={answers.lat}
                  lng={answers.lng}
                  onPick={(c) => {
                    setAnswers((prev) => ({ ...prev, lat: c.latitude, lng: c.longitude }));
                    setLocateNote("Pin location captured.");
                  }}
                />
              )}
              <input
                type="text"
                data-testid="triage-location-text"
                value={locationDraft}
                onChange={(e) => setLocationDraft(e.target.value)}
                placeholder="Landmark, address or area — e.g. Mbare Musika, near the bus rank"
                aria-label="Describe the place"
                className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-[15px] focus:border-emerald-500 focus:outline-none"
              />
              <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
                <select
                  data-testid="triage-province"
                  value={provinceDraft}
                  onChange={(e) => setProvinceDraft(e.target.value)}
                  aria-label="Province"
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-[15px] focus:border-emerald-500 focus:outline-none"
                >
                  <option value="">Province (optional)</option>
                  {ZW_PROVINCES.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
                <input
                  type="text"
                  data-testid="triage-facility"
                  value={facilityDraft}
                  onChange={(e) => setFacilityDraft(e.target.value)}
                  placeholder="Already at a clinic? Its name"
                  aria-label="Facility name if already at one"
                  className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-[15px] focus:border-emerald-500 focus:outline-none"
                />
              </div>
              <button
                type="button"
                data-testid="triage-location-continue"
                onClick={commitLocation}
                disabled={
                  !locationDraft.trim() &&
                  !provinceDraft &&
                  !facilityDraft.trim() &&
                  answers.lat === undefined
                }
                className="min-h-[44px] rounded-lg bg-emerald-600 px-5 py-2 text-sm font-semibold text-white hover:bg-emerald-700 disabled:opacity-50"
              >
                Continue
              </button>
            </div>
          )}

          {question.kind === "review" && (
            <div className="space-y-3 pl-10">
              {(() => {
                const u = classifyUrgency(answers);
                const g = URGENCY_GUIDANCE[u.tier];
                const tone =
                  u.tier === "emergency"
                    ? "border-red-300 bg-red-50 text-red-900"
                    : u.tier === "urgency"
                      ? "border-amber-300 bg-amber-50 text-amber-900"
                      : "border-emerald-300 bg-emerald-50 text-emerald-900";
                return (
                  <div
                    data-testid="triage-urgency"
                    data-urgency={u.tier}
                    className={`rounded-xl border p-4 ${tone}`}
                  >
                    <p className="text-[15px] font-bold">{g.headline}</p>
                    <p className="mt-1 text-sm">{u.reason}</p>
                    <p className="mt-1 text-sm">{g.action}</p>
                  </div>
                );
              })()}
              <div
                data-testid="triage-summary"
                className="whitespace-pre-line rounded-xl bg-white p-4 font-mono text-[13px] leading-relaxed text-slate-800 ring-1 ring-slate-200"
              >
                {buildSummary(answers)}
              </div>
              <NearbyEmergencyCare lat={answers.lat} lng={answers.lng} province={answers.province} />
              {submitError && (
                <p data-testid="triage-submit-error" role="alert" className="text-sm font-medium text-red-700">
                  {submitError}
                </p>
              )}
              <button
                type="button"
                data-testid="triage-submit"
                onClick={submit}
                disabled={submitting}
                className="inline-flex min-h-[48px] items-center gap-2 rounded-lg bg-red-700 px-6 py-2.5 text-[15px] font-bold text-white hover:bg-red-800 disabled:opacity-60"
              >
                {submitting ? (
                  <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
                ) : (
                  <Send className="h-4 w-4" aria-hidden />
                )}
                {submitting ? "Sending…" : "Send request — a responder will call back"}
              </button>
              <p className="text-xs text-slate-500">
                Sending this does not dispatch an ambulance. A responder calls your number to
                confirm first. If things get worse, call the numbers above now.
              </p>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
