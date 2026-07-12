"use client";

/**
 * Public health-professional verification.
 * Route: /verify/practitioner (guard: none — mirrors /verify/facility-certificate)
 *
 * Anyone holding a professional's registration number can confirm whether the
 * person is on the national health professionals register and whether their
 * practising certificate is current. Calls the permitAll BFF route
 * GET /internal/v1/public/gateway/practitioners/verify/{registrationNumber}
 * and renders the governed public disclosure only — register facts, never
 * contact details or disciplinary detail. Lookup is exact-match by number;
 * a miss comes back as a 200 with registerStatus=NOT_FOUND (no enumeration
 * oracle). Supports ?number= prefill for deep links.
 */

import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { BadgeCheck, Loader2, SearchX, ShieldQuestion } from "lucide-react";
import { apiClient } from "@/lib/api-client";

export interface PractitionerVerification {
  registerStatus: string;
  displayName: string | null;
  profession: string | null;
  cadre: string | null;
  registrationNumber: string | null;
  registeredSince: string | null;
  registrationExpiryDate: string | null;
  licenceStatus: string | null;
  licenceValidFrom: string | null;
  licenceValidTo: string | null;
  registeringAuthority: string | null;
}

function statusChipClass(status: string): string {
  switch (status) {
    case "REGISTERED":
    case "ACTIVE":
      return "bg-emerald-500/10 text-emerald-600";
    case "EXPIRED":
    case "LAPSED":
      return "bg-amber-500/10 text-amber-600";
    case "SUSPENDED":
    case "DEREGISTERED":
    case "REVOKED":
      return "bg-red-500/10 text-red-600";
    default:
      return "bg-muted text-muted-foreground";
  }
}

function fmtDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  return iso.slice(0, 10);
}

export default function VerifyPractitionerPage() {
  const searchParams = useSearchParams();
  const [number, setNumber] = useState("");
  const [result, setResult] = useState<PractitionerVerification | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const prefillDone = useRef(false);

  async function verify(rawNumber: string) {
    const trimmed = rawNumber.trim();
    if (!trimmed) return;
    setLoading(true);
    setError("");
    setNotFound(false);
    setResult(null);
    try {
      const res = await apiClient.get<PractitionerVerification>(
        `/internal/v1/public/gateway/practitioners/verify/${encodeURIComponent(trimmed)}`,
      );
      if (!res || res.registerStatus === "NOT_FOUND") {
        setNotFound(true);
      } else {
        setResult(res);
      }
    } catch (e) {
      if ((e as { status?: number })?.status === 404) {
        setNotFound(true);
      } else {
        setError("Verification is temporarily unavailable. Please try again.");
      }
    } finally {
      setLoading(false);
    }
  }

  // ?number= prefill (deep link) — verify once on arrival.
  useEffect(() => {
    const prefill = searchParams.get("number");
    if (prefill && !prefillDone.current) {
      prefillDone.current = true;
      setNumber(prefill);
      void verify(prefill);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  return (
    <div className="max-w-xl space-y-6">
      <p className="text-sm text-muted-foreground">
        Enter a health professional&apos;s registration number to confirm they appear on the
        national health professionals register and whether their practising certificate is
        current. Only public register facts are shown.
      </p>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          void verify(number);
        }}
        className="flex flex-col gap-3 rounded-lg border border-border bg-card p-4 sm:flex-row sm:items-end"
      >
        <div className="min-w-0 flex-1">
          <label htmlFor="registration-number" className="mb-1 block text-sm font-medium text-foreground">
            Registration number
          </label>
          <input
            id="registration-number"
            type="text"
            value={number}
            onChange={(e) => setNumber(e.target.value)}
            placeholder="Type the registration number exactly…"
            className="w-full rounded-lg border border-border px-3 py-2 text-sm"
            required
          />
        </div>
        <button
          type="submit"
          disabled={loading}
          className="shrink-0 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover disabled:opacity-50"
        >
          {loading ? (
            <span className="inline-flex items-center gap-1.5">
              <Loader2 className="h-4 w-4 animate-spin" /> Verifying…
            </span>
          ) : (
            "Verify"
          )}
        </button>
      </form>

      {error && (
        <div className="flex items-start gap-2 rounded-lg border border-danger/28 bg-danger-soft p-4 text-sm text-red-800">
          <ShieldQuestion className="mt-0.5 h-4 w-4 shrink-0" />
          {error}
        </div>
      )}

      {notFound && (
        <div className="rounded-lg border border-border bg-card p-6 text-center">
          <SearchX className="mx-auto mb-2 h-8 w-8 text-muted-foreground" />
          <p className="text-sm font-medium text-foreground">
            No registered professional matches this number
          </p>
          <p className="mt-1 text-sm text-muted-foreground">
            The number was not recognised on the register. Check for typing errors — numbers must
            match exactly. If someone presented this number as proof of registration and it still
            fails to verify, treat the claim as unverified and report it to the relevant health
            professions authority.
          </p>
        </div>
      )}

      {result && (
        <div className="overflow-hidden rounded-lg border border-border bg-card">
          <div className="flex items-center justify-between gap-3 border-b border-border bg-background px-5 py-4">
            <div className="flex items-center gap-2">
              <BadgeCheck className="h-5 w-5 text-primary" />
              <div>
                <p className="text-sm font-semibold text-foreground">{result.displayName ?? "—"}</p>
                <p className="text-xs text-muted-foreground">
                  {result.profession ?? "Health professional"}
                  {result.registeringAuthority ? ` · ${result.registeringAuthority}` : ""}
                </p>
              </div>
            </div>
            <span
              className={`rounded-full px-3 py-1 text-xs font-medium uppercase tracking-wide ${statusChipClass(result.registerStatus)}`}
            >
              {result.registerStatus}
            </span>
          </div>
          <dl className="divide-y divide-border/60 px-5 text-sm">
            <Row label="Registration number" value={result.registrationNumber} />
            {result.cadre && <Row label="Cadre" value={result.cadre} />}
            <Row label="Registered since" value={fmtDate(result.registeredSince)} />
            <Row
              label="Registration expiry"
              value={result.registrationExpiryDate ? fmtDate(result.registrationExpiryDate) : "No expiry recorded"}
            />
            {result.licenceStatus && (
              <div className="flex items-start justify-between gap-4 py-2.5">
                <dt className="text-muted-foreground">Practising certificate</dt>
                <dd className="text-right font-medium text-foreground">
                  <span
                    className={`mr-2 rounded-full px-2 py-0.5 text-[11px] font-medium uppercase tracking-wide ${statusChipClass(result.licenceStatus)}`}
                  >
                    {result.licenceStatus}
                  </span>
                  {fmtDate(result.licenceValidFrom)} – {fmtDate(result.licenceValidTo)}
                </dd>
              </div>
            )}
            <Row label="Registering authority" value={result.registeringAuthority} />
          </dl>
          <p className="border-t border-border bg-background px-5 py-3 text-[11px] text-muted-foreground">
            This disclosure reflects the live national health professionals register at the time of
            the check.
          </p>
        </div>
      )}
    </div>
  );
}

function Row({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div className="flex items-start justify-between gap-4 py-2.5">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="text-right font-medium text-foreground">{value ?? "—"}</dd>
    </div>
  );
}
