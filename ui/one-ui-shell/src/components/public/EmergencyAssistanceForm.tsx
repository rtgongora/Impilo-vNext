"use client";

/**
 * Public emergency assistance request (no sign-in). Posts to the anonymous gateway lane
 * POST /internal/v1/public/gateway/sos. A callback number is REQUIRED so a responder can call
 * back to confirm before help is dispatched (PD-3). This form NEVER replaces the emergency-call
 * numbers above it — calling directly is always the fastest path and stays prominent.
 */

import { useState } from "react";
import Link from "next/link";
import { Loader2, MapPin } from "lucide-react";
import { apiClient } from "@/lib/api-client";

interface SosReceipt {
  requestReference?: string | null;
  message?: string | null;
}

const CATEGORIES: { value: string; label: string }[] = [
  { value: "MEDICAL", label: "Medical emergency" },
  { value: "TRAUMA", label: "Injury or accident" },
  { value: "OBSTETRIC", label: "Pregnancy or newborn" },
  { value: "CARDIAC", label: "Chest pain / heart" },
  { value: "MENTAL_HEALTH", label: "Mental health crisis" },
  { value: "POISONING", label: "Poisoning" },
  { value: "VIOLENCE", label: "Violence or assault" },
  { value: "OTHER", label: "Other emergency" },
];

export function EmergencyAssistanceForm() {
  const [callbackNumber, setCallbackNumber] = useState("");
  const [category, setCategory] = useState("MEDICAL");
  const [location, setLocation] = useState("");
  const [description, setDescription] = useState("");
  const [callbackError, setCallbackError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [receipt, setReceipt] = useState<SosReceipt | null>(null);
  const [coords, setCoords] = useState<{ lat: number; lng: number } | null>(null);
  const [locating, setLocating] = useState(false);

  function shareLocation() {
    if (typeof navigator === "undefined" || !navigator.geolocation) return;
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setCoords({ lat: pos.coords.latitude, lng: pos.coords.longitude });
        setLocating(false);
      },
      () => setLocating(false),
      { timeout: 8000, enableHighAccuracy: true },
    );
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError("");
    if (!callbackNumber.trim()) {
      setCallbackError("Enter a phone number we can call you back on.");
      return;
    }
    setCallbackError("");
    setSubmitting(true);
    try {
      const res = await apiClient.post<SosReceipt>("/internal/v1/public/gateway/sos", {
        callbackNumber: callbackNumber.trim(),
        emergencyCategory: category,
        locationDescription: location.trim() || undefined,
        description: description.trim() || undefined,
        lat: coords?.lat,
        lng: coords?.lng,
      });
      setReceipt(res);
    } catch (e) {
      const status = (e as { status?: number })?.status;
      if (status === 400) {
        setCallbackError("Enter a valid phone number we can call you back on.");
      } else if (status === 429) {
        setError(
          "The service is very busy right now. If this is a real emergency, call the numbers above immediately.",
        );
      } else {
        setError(
          "We could not send your request just now. If this is an emergency, call the numbers above immediately.",
        );
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (receipt) {
    return (
      <section
        data-testid="sos-success"
        className="mt-6 rounded-xl border border-emerald-300 bg-emerald-50 p-6"
      >
        <h2 className="font-semibold text-emerald-900">Your request for help has been received</h2>
        {receipt.requestReference && (
          <p className="mt-2 text-sm text-emerald-900">
            Reference:{" "}
            <span data-testid="sos-reference" className="font-mono font-semibold">
              {receipt.requestReference}
            </span>
          </p>
        )}
        <p className="mt-2 text-sm text-emerald-900">
          {receipt.message ||
            "We will call you back on the number you provided to confirm before help is dispatched."}
        </p>
        <p className="mt-3 text-sm font-medium text-red-800">
          If the situation is life-threatening, call the emergency numbers above now — do not wait
          for the callback.
        </p>
        {receipt.requestReference && (
          <Link
            href={`/welcome/emergency/track?ref=${encodeURIComponent(receipt.requestReference)}`}
            data-testid="sos-track-link"
            className="mt-4 inline-block text-sm font-semibold text-emerald-800 underline hover:text-emerald-900"
          >
            Track this request →
          </Link>
        )}
      </section>
    );
  }

  return (
    <section className="mt-6 rounded-xl border border-slate-200 bg-white p-6">
      <h2 className="font-semibold text-slate-900">Request emergency assistance</h2>
      <p className="mt-1 text-sm text-slate-600">
        No account needed. A responder will call you back on the number you give to confirm before
        help is dispatched. Calling the numbers above is always the fastest way to get help.
      </p>

      <form className="mt-4 grid gap-4" onSubmit={submit} noValidate>
        <div>
          <label htmlFor="sos-callback" className="block text-sm font-medium text-slate-700">
            Callback number <span className="text-red-600">*</span>
          </label>
          <input
            id="sos-callback"
            data-testid="sos-callback"
            type="tel"
            inputMode="tel"
            value={callbackNumber}
            onChange={(e) => setCallbackNumber(e.target.value)}
            placeholder="e.g. 0771 234 567"
            aria-required="true"
            aria-invalid={callbackError ? "true" : undefined}
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none"
          />
          {callbackError && (
            <p data-testid="sos-callback-error" className="mt-1 text-sm text-red-700">
              {callbackError}
            </p>
          )}
        </div>

        <div>
          <label htmlFor="sos-category" className="block text-sm font-medium text-slate-700">
            What kind of emergency?
          </label>
          <select
            id="sos-category"
            data-testid="sos-category"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none"
          >
            {CATEGORIES.map((c) => (
              <option key={c.value} value={c.value}>
                {c.label}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="sos-location" className="block text-sm font-medium text-slate-700">
            Where are you? (landmark, address, or area)
          </label>
          <input
            id="sos-location"
            data-testid="sos-location"
            type="text"
            value={location}
            onChange={(e) => setLocation(e.target.value)}
            placeholder="e.g. Mbare Musika, near the bus rank"
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none"
          />
          <button
            type="button"
            data-testid="sos-share-location"
            onClick={shareLocation}
            disabled={locating}
            className="mt-2 inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60"
          >
            {locating ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <MapPin className="h-3.5 w-3.5" />}
            {coords ? "Location shared" : "Share my current location"}
          </button>
          {coords && (
            <p className="mt-1 text-xs text-emerald-700">
              Your location will be sent to help responders find you faster.
            </p>
          )}
        </div>

        <div>
          <label htmlFor="sos-description" className="block text-sm font-medium text-slate-700">
            What is happening? (optional)
          </label>
          <textarea
            id="sos-description"
            data-testid="sos-description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={3}
            placeholder="Briefly describe the emergency"
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none"
          />
        </div>

        {error && (
          <p data-testid="sos-error" className="text-sm text-red-700">
            {error}
          </p>
        )}

        <button
          type="submit"
          data-testid="sos-submit"
          disabled={submitting}
          className="inline-flex items-center justify-center gap-2 rounded-lg bg-red-700 px-5 py-2.5 text-sm font-semibold text-white hover:bg-red-800 disabled:opacity-60"
        >
          {submitting ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : null}
          {submitting ? "Sending…" : "Request assistance"}
        </button>
      </form>
    </section>
  );
}
