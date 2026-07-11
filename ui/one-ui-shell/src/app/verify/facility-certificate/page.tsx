"use client";

/**
 * Public facility-certificate verification.
 * Route: /verify/facility-certificate (guard: none — mirrors /verify/credential)
 *
 * Anyone holding a certificate's verification code (printed/QR on the HPA
 * certificate) can confirm its authenticity. Calls the permitAll BFF route
 * GET /internal/v1/public/facility-certificates/verify/{code} and renders the
 * governed public disclosure only — no PII, no registry internals.
 * Supports ?code= prefill for QR deep links.
 */

import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { BadgeCheck, Loader2, SearchX, ShieldQuestion } from "lucide-react";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import type { FacilityCertificateVerification } from "@/hooks/queries/useHpaRegulatory";

function statusChipClass(status: string): string {
  switch (status) {
    case "ACTIVE":
      return "bg-emerald-500/10 text-emerald-600";
    case "EXPIRED":
      return "bg-amber-500/10 text-amber-600";
    case "SUSPENDED":
    case "REVOKED":
      return "bg-red-500/10 text-red-600";
    case "SUPERSEDED":
      return "bg-muted text-muted-foreground";
    default:
      return "bg-muted text-muted-foreground";
  }
}

function fmtDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  return iso.slice(0, 10);
}

export default function VerifyFacilityCertificatePage() {
  const searchParams = useSearchParams();
  const [code, setCode] = useState("");
  const [result, setResult] = useState<FacilityCertificateVerification | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const prefillDone = useRef(false);

  async function verify(rawCode: string) {
    const trimmed = rawCode.trim();
    if (!trimmed) return;
    setLoading(true);
    setError("");
    setNotFound(false);
    setResult(null);
    try {
      const res = await apiClient.get<ApiResponse<FacilityCertificateVerification>>(
        `/internal/v1/public/facility-certificates/verify/${encodeURIComponent(trimmed)}`,
      );
      setResult(res.data);
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

  // ?code= prefill (QR deep link) — verify once on arrival.
  useEffect(() => {
    const prefill = searchParams.get("code");
    if (prefill && !prefillDone.current) {
      prefillDone.current = true;
      setCode(prefill);
      void verify(prefill);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  return (
    <div className="max-w-xl space-y-6">
      <p className="text-sm text-muted-foreground">
        Enter the verification code printed on a facility registration or licensing certificate to
        confirm it was issued under Health Professions Authority governance.
      </p>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          void verify(code);
        }}
        className="flex flex-col gap-3 rounded-lg border border-border bg-card p-4 sm:flex-row sm:items-end"
      >
        <div className="min-w-0 flex-1">
          <label htmlFor="certificate-code" className="mb-1 block text-sm font-medium text-foreground">
            Verification code
          </label>
          <input
            id="certificate-code"
            type="text"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            placeholder="Paste or type the certificate code…"
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
          <p className="text-sm font-medium text-foreground">No certificate matches this code</p>
          <p className="mt-1 text-sm text-muted-foreground">
            The code was not recognised. Check for typing errors — if the code came from a printed
            certificate that still fails to verify, treat the document as unverified and report it
            to the Health Professions Authority.
          </p>
        </div>
      )}

      {result && (
        <div className="overflow-hidden rounded-lg border border-border bg-card">
          <div className="flex items-center justify-between gap-3 border-b border-border bg-background px-5 py-4">
            <div className="flex items-center gap-2">
              <BadgeCheck className="h-5 w-5 text-primary" />
              <div>
                <p className="text-sm font-semibold text-foreground">{result.certificateNumber}</p>
                <p className="text-xs text-muted-foreground">
                  {result.certificateType ?? "Certificate"}
                  {result.issuedUnderAuthority ? ` · ${result.issuedUnderAuthority}` : ""}
                </p>
              </div>
            </div>
            <span
              className={`rounded-full px-3 py-1 text-xs font-medium uppercase tracking-wide ${statusChipClass(result.status)}`}
            >
              {result.status}
            </span>
          </div>
          <dl className="divide-y divide-border/60 px-5 text-sm">
            <Row label="Facility" value={result.facilityName} />
            {result.licensedUnit && <Row label="Licensed unit" value={result.licensedUnit} />}
            <Row label="Facility type" value={result.facilityType} />
            <Row
              label="Location"
              value={[result.district, result.province].filter(Boolean).join(", ") || null}
            />
            <Row label="Issue date" value={fmtDate(result.issueDate)} />
            <Row label="Expiry date" value={result.expiryDate ? fmtDate(result.expiryDate) : "No expiry"} />
            {result.conditions && <Row label="Conditions" value={result.conditions} />}
            {result.supersedes && <Row label="Supersedes" value={result.supersedes} />}
          </dl>
          <p className="border-t border-border bg-background px-5 py-3 text-[11px] text-muted-foreground">
            This disclosure reflects the live HPA facility register at the time of the check.
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
