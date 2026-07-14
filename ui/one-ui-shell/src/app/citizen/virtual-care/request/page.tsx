"use client";

/**
 * Citizen teleconsult request — submits into a virtual hospital's specialty pool via the
 * BFF virtual-care lane. The virtual hospital comes from ?vh= (deep link from discovery)
 * or an in-page picker limited to REQUESTABLE hospitals. Video/audio need explicit
 * consent (governed media); the confirmation is honest: queued for the pool, a provider
 * will accept it.
 */

import Link from "next/link";
import { Suspense, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, CheckCircle2, Loader2, MonitorSmartphone } from "lucide-react";
import {
  useCreateVirtualCareRequest,
  useVirtualHospitals,
  type VirtualCareRequestConfirmation,
} from "@/hooks/queries/useVirtualCare";

const MODALITIES = [
  { value: "video", label: "Video call", needsConsent: true },
  { value: "audio", label: "Audio call", needsConsent: true },
  { value: "message", label: "Secure message", needsConsent: false },
] as const;

type Modality = (typeof MODALITIES)[number]["value"];

function errorMessageOf(error: unknown): string {
  if (error && typeof error === "object") {
    const err = (error as { error?: { code?: string; message?: string }; message?: string });
    if (err.error?.message) return err.error.message;
    if (typeof err.message === "string" && err.message) return err.message;
  }
  return "Your request could not be submitted. Please try again.";
}

function VirtualCareRequestForm() {
  const searchParams = useSearchParams();
  const directory = useVirtualHospitals("citizen");
  const createRequest = useCreateVirtualCareRequest();

  const requestable = useMemo(
    () =>
      (directory.data?.data?.virtualHospitals ?? []).filter(
        (h) => h.availability === "REQUESTABLE",
      ),
    [directory.data?.data?.virtualHospitals],
  );

  const [virtualHospitalId, setVirtualHospitalId] = useState("");
  const [reason, setReason] = useState("");
  const [modality, setModality] = useState<Modality>("video");
  const [consentGranted, setConsentGranted] = useState(false);
  const [confirmation, setConfirmation] = useState<VirtualCareRequestConfirmation | null>(null);

  useEffect(() => {
    const fromLink = searchParams.get("vh");
    if (fromLink && !virtualHospitalId) {
      setVirtualHospitalId(fromLink);
    }
  }, [searchParams, virtualHospitalId]);

  const selected = requestable.find((h) => h.id === virtualHospitalId);
  const needsConsent = MODALITIES.find((m) => m.value === modality)?.needsConsent ?? true;
  const canSubmit =
    Boolean(selected) &&
    reason.trim().length > 0 &&
    (!needsConsent || consentGranted) &&
    !createRequest.isPending;

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (!canSubmit || !selected) return;
    createRequest.mutate(
      {
        virtualHospitalId: selected.id,
        reason: reason.trim(),
        modality,
        consentGranted,
      },
      {
        onSuccess: (response) => setConfirmation(response.data),
      },
    );
  }

  if (confirmation) {
    return (
      <div className="rounded-xl border border-teal-200 bg-teal-50 p-6" role="status">
        <div className="flex items-start gap-3">
          <CheckCircle2 className="h-6 w-6 shrink-0 text-teal-600" aria-hidden />
          <div>
            <h2 className="font-semibold text-slate-900">Request queued</h2>
            <p className="mt-1 text-sm text-slate-700">
              {confirmation.message ??
                `Queued for ${confirmation.virtualHospital?.name ?? "the virtual hospital"} — a provider will accept your request.`}
            </p>
            <p className="mt-2 text-xs text-slate-500">Request reference: {confirmation.requestId}</p>
            <div className="mt-4 flex gap-3">
              <Link
                href="/citizen/virtual-care"
                className="rounded-lg bg-teal-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-teal-700"
              >
                View my requests
              </Link>
              <Link
                href="/discover/virtual-care"
                className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50"
              >
                Back to virtual care
              </Link>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-5">
      {directory.isLoading && (
        <p className="inline-flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> Loading virtual hospitals…
        </p>
      )}
      {directory.isError && !directory.isLoading && (
        <p className="rounded-lg border border-destructive/40 bg-destructive/5 p-3 text-sm text-foreground">
          The virtual care directory is unavailable right now. Please try again shortly.
        </p>
      )}

      {!directory.isLoading && !directory.isError && (
        <>
          <label className="block text-sm">
            <span className="font-medium text-foreground">Virtual hospital</span>
            <select
              value={virtualHospitalId}
              onChange={(e) => setVirtualHospitalId(e.target.value)}
              className="mt-1 block w-full rounded-lg border border-border bg-card px-3 py-2 text-sm"
            >
              <option value="">Choose a virtual hospital…</option>
              {requestable.map((h) => (
                <option key={h.id} value={h.id}>
                  {h.name}
                </option>
              ))}
            </select>
          </label>
          {selected?.purpose && (
            <p className="text-xs text-muted-foreground">{selected.purpose}</p>
          )}

          <label className="block text-sm">
            <span className="font-medium text-foreground">What do you need help with?</span>
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={3}
              placeholder="Describe your symptoms or question"
              className="mt-1 block w-full rounded-lg border border-border bg-card px-3 py-2 text-sm"
            />
          </label>

          <fieldset>
            <legend className="text-sm font-medium text-foreground">How would you like to consult?</legend>
            <div className="mt-2 flex flex-wrap gap-2">
              {MODALITIES.map((m) => (
                <label
                  key={m.value}
                  className={`cursor-pointer rounded-lg border px-3 py-2 text-sm ${
                    modality === m.value
                      ? "border-teal-600 bg-teal-50 text-teal-700"
                      : "border-border text-muted-foreground hover:border-teal-300"
                  }`}
                >
                  <input
                    type="radio"
                    name="modality"
                    value={m.value}
                    checked={modality === m.value}
                    onChange={() => setModality(m.value)}
                    className="sr-only"
                  />
                  {m.label}
                </label>
              ))}
            </div>
          </fieldset>

          {needsConsent && (
            <label className="flex items-start gap-2 rounded-lg border border-border bg-card p-3 text-sm">
              <input
                type="checkbox"
                checked={consentGranted}
                onChange={(e) => setConsentGranted(e.target.checked)}
                className="mt-0.5"
              />
              <span className="text-muted-foreground">
                I consent to a governed video/audio consultation. My consent is recorded and I
                can withdraw it at any time.
              </span>
            </label>
          )}

          {createRequest.isError && (
            <p className="rounded-lg border border-destructive/40 bg-destructive/5 p-3 text-sm text-foreground" role="alert">
              {errorMessageOf(createRequest.error)}
            </p>
          )}

          <button
            type="submit"
            disabled={!canSubmit}
            className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-50"
          >
            {createRequest.isPending && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
            Submit request
          </button>
        </>
      )}
    </form>
  );
}

export default function CitizenVirtualCareRequestPage() {
  return (
    <div className="mx-auto max-w-2xl space-y-6 p-6">
      <header>
        <Link
          href="/discover/virtual-care"
          className="mb-2 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-teal-700"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden /> Virtual care
        </Link>
        <div className="flex items-center gap-3">
          <MonitorSmartphone className="h-7 w-7 text-teal-600" aria-hidden />
          <div>
            <h1 className="text-2xl font-semibold text-foreground">Request a teleconsult</h1>
            <p className="text-sm text-muted-foreground">
              Your request joins the virtual hospital&apos;s care pool; a provider accepts it and
              consults with you.
            </p>
          </div>
        </div>
      </header>
      <Suspense fallback={<p className="text-sm text-muted-foreground">Loading…</p>}>
        <VirtualCareRequestForm />
      </Suspense>
    </div>
  );
}
