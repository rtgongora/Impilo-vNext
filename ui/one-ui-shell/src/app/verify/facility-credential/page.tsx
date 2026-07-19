"use client";

/**
 * Public facility QR credential verification (FJ QR/D-L7; doctrine §8/§20).
 * Route: /verify/facility-credential (guard: none — under PUBLIC_PREFIXES /verify)
 *
 * Anyone scanning a facility's QR credential (or typing its verification code)
 * confirms it against the live register. Calls the permitAll BFF route
 * POST /internal/v1/public/facility-certificates/credential-scan (QR in the
 * body). Returns the policy-filtered public projection only — never internal
 * ids, confidential findings, or administrator PII. A forged/revoked/missing
 * credential returns a uniform NOT_RECOGNISED shape. Distinct from the HPA
 * certificate verify at /verify/facility-certificate.
 */

import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { BadgeCheck, Building2, Loader2, ShieldQuestion, ShieldX } from "lucide-react";
import { apiClient } from "@/lib/api-client";

interface CredentialVerification {
  status: "VERIFIED" | "NOT_RECOGNISED";
  facilityName: string | null;
  facilityCode: string | null;
  facilityType: string | null;
  province: string | null;
  district: string | null;
  facilityStatus: string | null;
}

export default function VerifyFacilityCredentialPage() {
  const searchParams = useSearchParams();
  const [qr, setQr] = useState("");
  const [result, setResult] = useState<CredentialVerification | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const prefillDone = useRef(false);

  async function verify(raw: string) {
    const trimmed = raw.trim();
    if (!trimmed) return;
    setLoading(true);
    setError("");
    setResult(null);
    try {
      const res = await apiClient.post<CredentialVerification>(
        "/internal/v1/public/facility-certificates/credential-scan",
        { qr: trimmed },
      );
      setResult(res);
    } catch {
      setError("Verification is temporarily unavailable. Please try again.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    const prefill = searchParams.get("qr") ?? searchParams.get("code");
    if (prefill && !prefillDone.current) {
      prefillDone.current = true;
      setQr(prefill);
      void verify(prefill);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  return (
    <div className="max-w-xl space-y-6">
      <p className="text-sm text-muted-foreground">
        Scan a facility&apos;s QR credential — or type its verification code — to confirm it is a
        recognised health facility. This shows approved public details only.
      </p>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          void verify(qr);
        }}
        className="flex flex-col gap-3 rounded-lg border border-border bg-card p-4 sm:flex-row sm:items-end"
      >
        <div className="min-w-0 flex-1">
          <label htmlFor="credential-qr" className="mb-1 block text-sm font-medium text-foreground">
            Credential code
          </label>
          <input
            id="credential-qr"
            type="text"
            value={qr}
            onChange={(e) => setQr(e.target.value)}
            placeholder="Paste the scanned credential code…"
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

      {result?.status === "NOT_RECOGNISED" && (
        <div className="rounded-lg border border-border bg-card p-6 text-center" data-testid="credential-not-recognised">
          <ShieldX className="mx-auto mb-2 h-8 w-8 text-muted-foreground" />
          <p className="text-sm font-medium text-foreground">This credential could not be verified</p>
          <p className="mt-1 text-sm text-muted-foreground">
            The credential was not recognised. Treat the facility as unverified and, if it was
            presented as official, report it.
          </p>
        </div>
      )}

      {result?.status === "VERIFIED" && (
        <div className="overflow-hidden rounded-lg border border-border bg-card" data-testid="credential-verified">
          <div className="flex items-center justify-between gap-3 border-b border-border bg-background px-5 py-4">
            <div className="flex items-center gap-2">
              <Building2 className="h-5 w-5 text-primary" />
              <div>
                <p className="text-sm font-semibold text-foreground">{result.facilityName ?? "Verified facility"}</p>
                <p className="text-xs text-muted-foreground">
                  {[result.facilityType, result.facilityCode].filter(Boolean).join(" · ") || "Health facility"}
                </p>
              </div>
            </div>
            <span className="inline-flex items-center gap-1 rounded-full bg-emerald-500/10 px-3 py-1 text-xs font-medium uppercase tracking-wide text-emerald-600">
              <BadgeCheck className="h-3.5 w-3.5" /> Verified
            </span>
          </div>
          <dl className="divide-y divide-border/60 px-5 text-sm">
            <Row label="Location" value={[result.district, result.province].filter(Boolean).join(", ") || null} />
            <Row label="Public status" value={result.facilityStatus} />
          </dl>
          <p className="border-t border-border bg-background px-5 py-3 text-[11px] text-muted-foreground">
            This reflects the live facility register at the time of the check.
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
