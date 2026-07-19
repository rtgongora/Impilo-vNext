"use client";

/**
 * Request site operator access (SJ1 / D-L4). Route: /site/operator-access
 *
 * A person claims an operator role on an existing Indawo site (viewer / operator
 * / administrator / regulatory liaison). The claimant is the session Health ID
 * (server-side). It requires a proven personal identity (≥LOA2) and evidence of
 * authority; no operator capability is active until a steward approves. A
 * temporary role must be end-dated.
 */

import { useState } from "react";
import Link from "next/link";
import { CheckCircle2, KeyRound, ShieldAlert } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  SITE_OPERATOR_ROLES,
  siteSelfServiceErrorMessage,
  useRequestOperatorGrant,
  type SiteOperatorRole,
} from "@/hooks/queries/useSiteSelfService";

const inputClass =
  "w-full rounded-lg border border-border bg-background px-3 py-2 text-sm text-foreground";

const ROLE_LABEL: Record<SiteOperatorRole, string> = {
  SITE_VIEWER: "Site viewer",
  SITE_OPERATOR: "Site operator",
  SITE_ADMINISTRATOR: "Site administrator",
  REGULATORY_LIAISON: "Regulatory liaison",
};

export default function SiteOperatorAccessPage() {
  const request = useRequestOperatorGrant();

  const [siteId, setSiteId] = useState("");
  const [role, setRole] = useState<SiteOperatorRole>("SITE_OPERATOR");
  const [claimType, setClaimType] = useState<"NEW" | "RECOVERY">("NEW");
  const [evidenceRef, setEvidenceRef] = useState("");
  const [validTo, setValidTo] = useState("");
  const [error, setError] = useState("");
  const [submitted, setSubmitted] = useState<{ approvalState?: string; note?: string } | null>(null);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    if (!siteId.trim()) {
      setError("Enter the site you are requesting access to.");
      return;
    }
    if (!evidenceRef.trim()) {
      setError("Provide a supporting evidence reference for your authority.");
      return;
    }
    request.mutate(
      {
        siteId: siteId.trim(),
        role,
        claimType,
        evidenceRef: evidenceRef.trim(),
        validTo: validTo || undefined,
      },
      {
        onSuccess: (res) => {
          setSubmitted({ approvalState: res.approvalState, note: res.note });
          setEvidenceRef("");
        },
        onError: (err) =>
          setError(siteSelfServiceErrorMessage(err, "Your operator-access request could not be submitted.")),
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Request site operator access"
        subtitle="Claim an operator role on a site — access begins only after approval"
      >
        <div className="mx-auto max-w-2xl space-y-6">
          {submitted && (
            <div
              className="flex items-start gap-2 rounded-lg border border-success/35 bg-success-soft p-4 text-sm"
              data-testid="operator-grant-submitted"
            >
              <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-success" />
              <div>
                <p className="font-medium text-foreground">
                  Request submitted ({submitted.approvalState ?? "PENDING"})
                </p>
                <p className="mt-1 text-muted-foreground">{submitted.note}</p>
              </div>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4 rounded-2xl border border-border bg-card p-5">
            <div>
              <label htmlFor="site" className="mb-1 block text-sm font-medium text-foreground">
                Site
              </label>
              <input
                id="site"
                type="text"
                value={siteId}
                onChange={(e) => setSiteId(e.target.value)}
                placeholder="Site identifier"
                className={inputClass}
                required
              />
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div>
                <label htmlFor="role" className="mb-1 block text-sm font-medium text-foreground">
                  Role
                </label>
                <select
                  id="role"
                  value={role}
                  onChange={(e) => setRole(e.target.value as SiteOperatorRole)}
                  className={inputClass}
                >
                  {SITE_OPERATOR_ROLES.map((r) => (
                    <option key={r} value={r}>
                      {ROLE_LABEL[r]}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label htmlFor="claimType" className="mb-1 block text-sm font-medium text-foreground">
                  Request type
                </label>
                <select
                  id="claimType"
                  value={claimType}
                  onChange={(e) => setClaimType(e.target.value as "NEW" | "RECOVERY")}
                  className={inputClass}
                >
                  <option value="NEW">New access</option>
                  <option value="RECOVERY">Recover my access</option>
                </select>
              </div>
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div>
                <label htmlFor="evidence" className="mb-1 block text-sm font-medium text-foreground">
                  Evidence of authority
                </label>
                <input
                  id="evidence"
                  type="text"
                  value={evidenceRef}
                  onChange={(e) => setEvidenceRef(e.target.value)}
                  placeholder="Document id"
                  className={inputClass}
                  required
                />
              </div>
              <div>
                <label htmlFor="validTo" className="mb-1 block text-sm font-medium text-foreground">
                  Access end date (optional)
                </label>
                <input
                  id="validTo"
                  type="date"
                  value={validTo}
                  onChange={(e) => setValidTo(e.target.value)}
                  className={inputClass}
                />
              </div>
            </div>

            <p className="text-xs text-muted-foreground">
              Requesting requires a proven personal identity and evidence of your authority over the
              site. No operator capability is active until a steward approves.
            </p>

            {error && (
              <div className="flex items-start gap-2 rounded-lg border border-danger/28 bg-danger-soft p-3 text-sm text-red-800">
                <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <button
              type="submit"
              disabled={request.isPending}
              className="inline-flex items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground hover:bg-primary-hover disabled:opacity-50"
            >
              <KeyRound className="h-4 w-4" />
              {request.isPending ? "Submitting…" : "Request access"}
            </button>
          </form>

          <p className="text-center text-xs text-muted-foreground">
            Site not on the platform yet?{" "}
            <Link href="/site/register" className="text-primary hover:underline">
              Register a site
            </Link>{" "}
            first.
          </p>
        </div>
      </PageShell>
    </AppLayout>
  );
}
