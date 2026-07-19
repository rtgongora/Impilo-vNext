"use client";

/**
 * Request facility access (PJ4/D-P7). Route: /provider/facility-access
 *
 * A linked provider self-requests to WORK at a facility: engagement type
 * (permanent posting / rotation / locum / outreach / telemed / specialist pool
 * / supervisory / training) + validity. A temporary engagement must be
 * end-dated. The request is durable — routed to the receiving facility
 * (PENDING_FACILITY_REVIEW); no work access is active until it is approved and
 * the posting is recorded. The person anchor is the session Health ID
 * (server-side); it is never sent in the body.
 */

import { useMemo, useState } from "react";
import Link from "next/link";
import { BriefcaseBusiness, CheckCircle2, Clock } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  ENGAGEMENT_TYPES,
  type EngagementType,
  providerAccessErrorMessage,
  useProviderAccessRequests,
  useSubmitProviderAccessRequest,
} from "@/hooks/queries/useProviderAccessRequest";

const ENGAGEMENT_LABEL: Record<EngagementType, string> = {
  PERMANENT: "Permanent posting",
  ROTATION: "Rotation",
  LOCUM: "Locum",
  OUTREACH: "Outreach",
  TELEMED: "Telemedicine",
  SPECIALIST_POOL: "Specialist pool",
  SUPERVISORY: "Supervisory",
  TRAINING: "Training placement",
};

const inputClass =
  "w-full rounded-lg border border-border bg-background px-3 py-2 text-sm text-foreground";

export default function FacilityAccessRequestPage() {
  const submit = useSubmitProviderAccessRequest();
  const requests = useProviderAccessRequests();

  const [facilityId, setFacilityId] = useState("");
  const [engagementType, setEngagementType] = useState<EngagementType>("LOCUM");
  const [validFrom, setValidFrom] = useState("");
  const [validTo, setValidTo] = useState("");
  const [error, setError] = useState("");
  const [submittedId, setSubmittedId] = useState<string | null>(null);

  const temporaryNeedsEnd = engagementType !== "PERMANENT";

  const facilityRequests = useMemo(
    () => (requests.data ?? []).filter((r) => r.requestType === "FACILITY_ACCESS"),
    [requests.data],
  );

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    if (!facilityId.trim()) {
      setError("Enter the facility you are requesting access to.");
      return;
    }
    if (temporaryNeedsEnd && !validTo) {
      setError("A temporary engagement needs an access end date.");
      return;
    }
    submit.mutate(
      {
        requestType: "FACILITY_ACCESS",
        facilityId: facilityId.trim(),
        engagementType,
        accessValidFrom: validFrom || undefined,
        accessValidTo: validTo || undefined,
      },
      {
        onSuccess: (view) => {
          setSubmittedId(view.publicId);
          setFacilityId("");
          setValidTo("");
        },
        onError: (err) =>
          setError(providerAccessErrorMessage(err, "Your request could not be submitted.")),
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Request facility access"
        subtitle="Being a registered professional does not, by itself, authorise access at a facility"
      >
        <div className="mx-auto max-w-2xl space-y-6">
          {submittedId && (
            <div
              className="flex items-start gap-2 rounded-lg border border-success/35 bg-success-soft p-4 text-sm"
              data-testid="facility-access-submitted"
            >
              <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-success" />
              <div>
                <p className="font-medium text-foreground">Request submitted for facility review</p>
                <p className="mt-1 text-muted-foreground">
                  Work access begins only after the facility approves and the posting is recorded.
                  Track it below.
                </p>
              </div>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4 rounded-2xl border border-border bg-card p-5">
            <div>
              <label htmlFor="facility" className="mb-1 block text-sm font-medium text-foreground">
                Facility
              </label>
              <input
                id="facility"
                type="text"
                value={facilityId}
                onChange={(e) => setFacilityId(e.target.value)}
                placeholder="Facility identifier (from the facility you will work at)"
                className={inputClass}
                required
              />
              <p className="mt-1 text-xs text-muted-foreground">
                The facility administrator confirms your posting before any work access is active.
              </p>
            </div>

            <div>
              <label htmlFor="engagement" className="mb-1 block text-sm font-medium text-foreground">
                Engagement type
              </label>
              <select
                id="engagement"
                value={engagementType}
                onChange={(e) => setEngagementType(e.target.value as EngagementType)}
                className={inputClass}
              >
                {ENGAGEMENT_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {ENGAGEMENT_LABEL[t]}
                  </option>
                ))}
              </select>
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div>
                <label htmlFor="valid-from" className="mb-1 block text-sm font-medium text-foreground">
                  Start date (optional)
                </label>
                <input
                  id="valid-from"
                  type="date"
                  value={validFrom}
                  onChange={(e) => setValidFrom(e.target.value)}
                  className={inputClass}
                />
              </div>
              <div>
                <label htmlFor="valid-to" className="mb-1 block text-sm font-medium text-foreground">
                  End date {temporaryNeedsEnd ? "(required)" : "(not needed for permanent)"}
                </label>
                <input
                  id="valid-to"
                  type="date"
                  value={validTo}
                  onChange={(e) => setValidTo(e.target.value)}
                  className={inputClass}
                  required={temporaryNeedsEnd}
                  disabled={!temporaryNeedsEnd}
                />
              </div>
            </div>

            {error && (
              <div className="rounded-lg border border-danger/28 bg-danger-soft p-3 text-sm text-red-800">
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={submit.isPending}
              className="inline-flex items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground hover:bg-primary-hover disabled:opacity-50"
            >
              <BriefcaseBusiness className="h-4 w-4" />
              {submit.isPending ? "Submitting…" : "Request access"}
            </button>
          </form>

          <section className="space-y-2">
            <h2 className="text-sm font-semibold text-foreground">Your facility-access requests</h2>
            {requests.isLoading && (
              <p className="text-sm text-muted-foreground">Loading your requests…</p>
            )}
            {!requests.isLoading && facilityRequests.length === 0 && (
              <p className="text-sm text-muted-foreground">
                You have no facility-access requests yet.
              </p>
            )}
            <ul className="space-y-2">
              {facilityRequests.map((r) => (
                <li
                  key={r.publicId}
                  className="rounded-lg border border-border bg-card p-3 text-sm"
                  data-testid="facility-access-request-row"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-medium text-foreground">
                      {r.engagementType ? ENGAGEMENT_LABEL[r.engagementType as EngagementType] ?? r.engagementType : "Facility access"}
                    </span>
                    <span className="inline-flex items-center gap-1 rounded-full bg-muted px-2.5 py-0.5 text-xs uppercase tracking-wide text-muted-foreground">
                      <Clock className="h-3 w-3" />
                      {r.status}
                    </span>
                  </div>
                  {r.accessValidTo && (
                    <p className="mt-1 text-xs text-muted-foreground">Until {r.accessValidTo}</p>
                  )}
                  {r.reason && <p className="mt-1 text-xs text-muted-foreground">{r.reason}</p>}
                </li>
              ))}
            </ul>
          </section>

          <p className="text-center text-xs text-muted-foreground">
            Not linked to a professional profile yet?{" "}
            <Link href="/provider/get-access" className="text-primary hover:underline">
              Request Provider Access
            </Link>{" "}
            first.
          </p>
        </div>
      </PageShell>
    </AppLayout>
  );
}
