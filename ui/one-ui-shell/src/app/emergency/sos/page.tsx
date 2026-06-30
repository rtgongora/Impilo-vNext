"use client";

/**
 * Citizen SOS — raises a real emergency request via experience-BFF
 * (POST /internal/v1/daidzai/requests) and routes to tracking. No fake ambulance
 * tracker; Nompilo "what to do while help comes" guidance is shown.
 * Route: /emergency/sos
 */

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Ambulance, AlertTriangle, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { apiClient } from "@/lib/api-client";

const CATEGORIES = [
  "MEDICAL",
  "CARDIAC",
  "RESPIRATORY",
  "TRAUMA",
  "MAJOR_TRAUMA",
  "OBSTETRIC",
  "MENTAL_HEALTH",
  "POISONING",
  "OTHER",
] as const;

interface CreatedRequest {
  id: string;
  requestReference?: string;
}

function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `Request failed (HTTP ${obj.status}).`;
  }
  return "Could not send your SOS. Please try again.";
}

export default function EmergencySosPage() {
  const router = useRouter();
  const [forSelf, setForSelf] = useState(true);
  const [category, setCategory] = useState<(typeof CATEGORIES)[number]>("MEDICAL");
  const [severity, setSeverity] = useState("CRITICAL");
  const [description, setDescription] = useState("");
  const [locationDescription, setLocationDescription] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setSubmitting(true);
    setError(null);
    try {
      const body: Record<string, unknown> = {
        requesterType: forSelf ? "CITIZEN" : "CAREGIVER",
        subjectIdentityMode: forSelf ? "KNOWN" : "UNKNOWN",
        emergencyCategory: category,
        severity,
        description: description || undefined,
        locationDescription: locationDescription || undefined,
        channel: "WEB",
      };
      // Best-effort geolocation — never blocks the SOS if denied.
      if (typeof navigator !== "undefined" && navigator.geolocation) {
        await new Promise<void>((resolve) => {
          navigator.geolocation.getCurrentPosition(
            (pos) => {
              body.lat = pos.coords.latitude;
              body.lng = pos.coords.longitude;
              resolve();
            },
            () => resolve(),
            { timeout: 4000 }
          );
        });
      }
      const res = await apiClient.post<CreatedRequest>("/internal/v1/daidzai/requests", body);
      if (res?.id) {
        router.push(`/emergency/track/${encodeURIComponent(res.id)}`);
      } else {
        setError("Your SOS was sent but no reference was returned. Please call for help.");
      }
    } catch (e) {
      setError(errMessage(e));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AppLayout>
      <PageShell
        title="Send SOS"
        subtitle="Tell us what's happening — help is dispatched without payment"
        serviceSlug="daidzai"
      >
        <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
          <div className="space-y-4 rounded-2xl border border-border bg-card p-5 shadow-sm">
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setForSelf(true)}
                className={`flex-1 rounded-lg border px-3 py-2 text-sm font-medium ${forSelf ? "border-red-400 bg-red-50 text-red-800" : "border-border text-muted-foreground"}`}
              >
                For me
              </button>
              <button
                type="button"
                onClick={() => setForSelf(false)}
                className={`flex-1 rounded-lg border px-3 py-2 text-sm font-medium ${!forSelf ? "border-red-400 bg-red-50 text-red-800" : "border-border text-muted-foreground"}`}
              >
                For someone else
              </button>
            </div>

            <label className="block text-sm">
              <span className="mb-1 block font-medium">What kind of emergency?</span>
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value as (typeof CATEGORIES)[number])}
                className="w-full rounded-lg border border-border bg-background px-3 py-2"
              >
                {CATEGORIES.map((c) => (
                  <option key={c} value={c}>
                    {c.replace(/_/g, " ")}
                  </option>
                ))}
              </select>
            </label>

            <label className="block text-sm">
              <span className="mb-1 block font-medium">How serious is it?</span>
              <select
                value={severity}
                onChange={(e) => setSeverity(e.target.value)}
                className="w-full rounded-lg border border-border bg-background px-3 py-2"
              >
                <option value="CRITICAL">Life-threatening</option>
                <option value="HIGH">Serious</option>
                <option value="MODERATE">Needs help soon</option>
                <option value="LOW">Minor</option>
              </select>
            </label>

            <label className="block text-sm">
              <span className="mb-1 block font-medium">What is happening? (optional)</span>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={3}
                className="w-full rounded-lg border border-border bg-background px-3 py-2"
                placeholder="e.g. chest pain, collapsed, bleeding…"
              />
            </label>

            <label className="block text-sm">
              <span className="mb-1 block font-medium">Where are you? (optional)</span>
              <input
                value={locationDescription}
                onChange={(e) => setLocationDescription(e.target.value)}
                className="w-full rounded-lg border border-border bg-background px-3 py-2"
                placeholder="e.g. Mbare market, near the bus rank"
              />
            </label>

            {error ? (
              <div className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                <span>{error}</span>
              </div>
            ) : null}

            <button
              type="button"
              onClick={() => void submit()}
              disabled={submitting}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-red-600 px-4 py-3 text-base font-semibold text-white shadow-sm transition hover:bg-red-700 disabled:opacity-60"
            >
              {submitting ? (
                <>
                  <Loader2 className="h-5 w-5 animate-spin" /> Sending SOS…
                </>
              ) : (
                <>
                  <Ambulance className="h-5 w-5" /> Send SOS now
                </>
              )}
            </button>
          </div>

          <NompiloContextualGuidance routePath="/emergency/sos" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
