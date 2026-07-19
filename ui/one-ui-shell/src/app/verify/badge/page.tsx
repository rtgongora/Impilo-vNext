"use client";

/**
 * Public provider badge verification (PJ6/D-P8; doctrine §20).
 * Route: /verify/badge (guard: none — under middleware PUBLIC_PREFIXES /verify)
 *
 * A patient scans a provider's badge QR (or pastes the signed payload) and sees
 * live public register facts. Calls the permitAll BFF route
 * POST /internal/v1/public/gateway/practitioners/badge-scan (QR in the body,
 * never the URL). A forged, revoked or not-in-standing badge all return the same
 * NOT_RECOGNISED shape — no oracle. Strictly separate from login and from any
 * claim journey: this page never links to sign-in.
 */

import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { BadgeCheck, Loader2, ShieldQuestion, ShieldX } from "lucide-react";
import { apiClient } from "@/lib/api-client";

interface BadgeVerification {
  status: "VERIFIED" | "NOT_RECOGNISED";
  displayName: string | null;
  profession: string | null;
  cadre: string | null;
  registerStatus: string | null;
  licenceValidFrom: string | null;
  licenceValidTo: string | null;
}

export default function VerifyBadgePage() {
  const searchParams = useSearchParams();
  const [qr, setQr] = useState("");
  const [result, setResult] = useState<BadgeVerification | null>(null);
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
      const res = await apiClient.post<BadgeVerification>(
        "/internal/v1/public/gateway/practitioners/badge-scan",
        { qr: trimmed },
      );
      setResult(res);
    } catch {
      setError("Verification is temporarily unavailable. Please try again.");
    } finally {
      setLoading(false);
    }
  }

  // ?qr= prefill (from a scanned badge deep link) — verify once on arrival.
  useEffect(() => {
    const prefill = searchParams.get("qr");
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
        Scan a health provider&apos;s badge QR — or paste its code — to confirm they are a
        registered professional in good standing. This shows public register facts only.
      </p>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          void verify(qr);
        }}
        className="flex flex-col gap-3 rounded-lg border border-border bg-card p-4 sm:flex-row sm:items-end"
      >
        <div className="min-w-0 flex-1">
          <label htmlFor="badge-qr" className="mb-1 block text-sm font-medium text-foreground">
            Badge code
          </label>
          <input
            id="badge-qr"
            type="text"
            value={qr}
            onChange={(e) => setQr(e.target.value)}
            placeholder="Paste the scanned badge code…"
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
        <div className="rounded-lg border border-border bg-card p-6 text-center" data-testid="badge-not-recognised">
          <ShieldX className="mx-auto mb-2 h-8 w-8 text-muted-foreground" />
          <p className="text-sm font-medium text-foreground">This badge could not be verified</p>
          <p className="mt-1 text-sm text-muted-foreground">
            The badge was not recognised as belonging to a provider in good standing. Do not rely on
            it — ask the person for another form of identification, or report a suspicious badge to
            the facility.
          </p>
        </div>
      )}

      {result?.status === "VERIFIED" && (
        <div className="overflow-hidden rounded-lg border border-border bg-card" data-testid="badge-verified">
          <div className="flex items-center justify-between gap-3 border-b border-border bg-background px-5 py-4">
            <div className="flex items-center gap-2">
              <BadgeCheck className="h-5 w-5 text-primary" />
              <div>
                <p className="text-sm font-semibold text-foreground">{result.displayName ?? "Verified provider"}</p>
                <p className="text-xs text-muted-foreground">
                  {[result.profession, result.cadre].filter(Boolean).join(" · ") || "Registered professional"}
                </p>
              </div>
            </div>
            {result.registerStatus && (
              <span className="rounded-full bg-emerald-500/10 px-3 py-1 text-xs font-medium uppercase tracking-wide text-emerald-600">
                {result.registerStatus}
              </span>
            )}
          </div>
          <dl className="divide-y divide-border/60 px-5 text-sm">
            <Row label="Practising certificate from" value={fmtDate(result.licenceValidFrom)} />
            <Row label="Valid until" value={result.licenceValidTo ? fmtDate(result.licenceValidTo) : "—"} />
          </dl>
          <p className="border-t border-border bg-background px-5 py-3 text-[11px] text-muted-foreground">
            This reflects the live professional register at the time of the check. A badge identifies
            an account — it never authorises care by itself.
          </p>
        </div>
      )}
    </div>
  );
}

function fmtDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  return iso.slice(0, 10);
}

function Row({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div className="flex items-start justify-between gap-4 py-2.5">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="text-right font-medium text-foreground">{value ?? "—"}</dd>
    </div>
  );
}
