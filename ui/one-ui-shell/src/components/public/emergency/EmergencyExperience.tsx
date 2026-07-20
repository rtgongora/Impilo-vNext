"use client";

/**
 * The public Emergency journey — an intelligent responder, not a brochure.
 *
 * Above the fold: the life-at-risk warning and verified call numbers (from
 * config — the single source of truth), then a four-way choice:
 * call now / find nearest emergency care / request a callback / Nompilo triage.
 *
 * The Nompilo guided triage IS the primary interface (EmergencyTriagePanel).
 * The classic short form remains as the "just request a callback" path — same
 * real backend, fewer questions. Both preserve state across navigation.
 */

import { useState } from "react";
import { ClipboardList, MapPin, Phone, Sparkles } from "lucide-react";
import { EMERGENCY_CONTACTS } from "@/config/emergency";
import { EmergencyAssistanceForm } from "@/components/public/EmergencyAssistanceForm";
import { EmergencyTriagePanel } from "./EmergencyTriagePanel";
import { NearbyEmergencyCare } from "./NearbyEmergencyCare";

type Mode = "triage" | "callback" | "nearby" | null;

export function EmergencyExperience() {
  const [mode, setMode] = useState<Mode>("triage");
  const [coords, setCoords] = useState<{ lat: number; lng: number } | null>(null);
  const [locating, setLocating] = useState(false);
  const [locateErr, setLocateErr] = useState("");

  function findNearby() {
    setMode("nearby");
    setLocateErr("");
    if (coords) return;
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      setLocateErr("Location is not available on this device. Use Find care to search by area.");
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setCoords({ lat: pos.coords.latitude, lng: pos.coords.longitude });
        setLocating(false);
      },
      () => {
        setLocating(false);
        setLocateErr(
          "We could not get your location. Allow location access, or use Find care to search by area.",
        );
      },
      { timeout: 8000, enableHighAccuracy: true },
    );
  }

  const choiceBase =
    "flex min-h-[72px] items-center gap-3 rounded-xl border p-4 text-left transition focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2";

  return (
    <>
      {/* ── Life-at-risk warning + verified numbers ── */}
      <div className="mt-3 rounded-2xl border-2 border-red-300 bg-red-50 p-5 sm:p-6">
        <h1 className="text-2xl font-extrabold text-red-800 sm:text-3xl">
          If life is at risk, get help now
        </h1>
        <p className="mt-2 max-w-3xl text-[15px] leading-relaxed text-slate-800">
          For a medical emergency, call emergency services or go to the nearest hospital emergency
          department immediately. Do not wait to create an account.
        </p>
        <div className="mt-4 grid gap-3 sm:grid-cols-3">
          {EMERGENCY_CONTACTS.map((c) => (
            <a
              key={c.tel}
              href={`tel:${c.tel}`}
              className="rounded-xl bg-white p-4 ring-1 ring-red-200 transition hover:ring-red-400 focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600"
            >
              <span className="block text-xs font-medium uppercase tracking-wide text-slate-500">
                {c.label}
              </span>
              <span className="mt-1 flex items-baseline gap-2">
                <span className="text-3xl font-extrabold text-slate-900">{c.number}</span>
                {c.note && <span className="text-sm text-slate-500">({c.note})</span>}
              </span>
              <span className="mt-1 inline-flex items-center gap-1 text-sm font-semibold text-red-700">
                <Phone className="h-3.5 w-3.5" aria-hidden /> Tap to call
              </span>
            </a>
          ))}
        </div>
      </div>

      {/* ── Four-way choice ── */}
      <div className="mt-6 grid gap-3 sm:grid-cols-2" role="group" aria-label="How would you like to get help?">
        <a
          href={`tel:${EMERGENCY_CONTACTS[0].tel}`}
          className={`${choiceBase} border-red-300 bg-red-700 text-white hover:bg-red-800 focus-visible:outline-red-600`}
        >
          <Phone className="h-6 w-6 shrink-0" aria-hidden />
          <span>
            <span className="block text-[15px] font-bold">Call now</span>
            <span className="block text-sm opacity-90">Fastest — speak to emergency services</span>
          </span>
        </a>
        <button
          type="button"
          data-testid="choice-nearby"
          onClick={findNearby}
          className={`${choiceBase} w-full border-slate-300 bg-white hover:border-emerald-400 hover:bg-emerald-50 focus-visible:outline-emerald-600 ${mode === "nearby" ? "border-emerald-500 ring-1 ring-emerald-300" : ""}`}
        >
          <MapPin className="h-6 w-6 shrink-0 text-emerald-600" aria-hidden />
          <span>
            <span className="block text-[15px] font-bold text-slate-900">
              Find the nearest emergency facility
            </span>
            <span className="block text-sm text-slate-600">Uses your location, no account needed</span>
          </span>
        </button>
        <button
          type="button"
          data-testid="choice-triage"
          onClick={() => setMode("triage")}
          className={`${choiceBase} w-full border-slate-300 bg-white hover:border-violet-400 hover:bg-violet-50 focus-visible:outline-violet-600 ${mode === "triage" ? "border-violet-500 ring-1 ring-violet-300" : ""}`}
        >
          <Sparkles className="h-6 w-6 shrink-0 text-violet-600" aria-hidden />
          <span>
            <span className="block text-[15px] font-bold text-slate-900">
              Continue with Nompilo emergency triage
            </span>
            <span className="block text-sm text-slate-600">
              Guided questions, danger-sign checks, then a callback request
            </span>
          </span>
        </button>
        <button
          type="button"
          data-testid="choice-callback"
          onClick={() => setMode("callback")}
          className={`${choiceBase} w-full border-slate-300 bg-white hover:border-emerald-400 hover:bg-emerald-50 focus-visible:outline-emerald-600 ${mode === "callback" ? "border-emerald-500 ring-1 ring-emerald-300" : ""}`}
        >
          <ClipboardList className="h-6 w-6 shrink-0 text-emerald-600" aria-hidden />
          <span>
            <span className="block text-[15px] font-bold text-slate-900">
              Request assistance / callback
            </span>
            <span className="block text-sm text-slate-600">Short form — a responder calls you back</span>
          </span>
        </button>
      </div>

      {/* ── Chosen mode ── */}
      {mode === "nearby" && (
        <div className="mt-2">
          {locating && <p className="mt-4 text-sm text-slate-600">Getting your location…</p>}
          {locateErr && <p className="mt-4 text-sm text-red-700">{locateErr}</p>}
          <NearbyEmergencyCare lat={coords?.lat} lng={coords?.lng} />
        </div>
      )}
      {mode === "triage" && <EmergencyTriagePanel />}
      {mode === "callback" && <EmergencyAssistanceForm />}
    </>
  );
}
