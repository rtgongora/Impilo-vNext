"use client";

/**
 * Consent Center (CJ13) — Route: /citizen/consent-center
 *
 * The two questions a person asks about their record, on one page:
 *   - "Who did I allow?"      → consent directives, each revocable
 *   - "Who actually looked?"  → the record access-log
 *
 * Subject is resolved server-side from the authenticated actor (BFF
 * /internal/v1/citizen/consent-center) — the citizen never supplies a patient id.
 */

import { useState } from "react";
import { Shield, Eye, UserCheck, AlertCircle } from "lucide-react";
import {
  useConsentCenter,
  useRevokeConsent,
  itemsOf,
  type ConsentDirective,
  type AccessHistoryEntry,
} from "@/hooks/queries/useConsentCenter";
import { StepUpPrompt } from "@/components/citizen/StepUpPrompt";
import { readStepUp } from "@/lib/stepUp";

const STATUS_COLOURS: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-800",
  GRANTED: "bg-green-100 text-green-800",
  PENDING: "bg-yellow-100 text-yellow-800",
  REVOKED: "bg-red-100 text-red-800",
  EXPIRED: "bg-neutral-100 text-muted-foreground",
  WITHDRAWN: "bg-red-100 text-red-800",
};

function StatusBadge({ status }: { status: string }) {
  const cls = STATUS_COLOURS[status?.toUpperCase()] ?? "bg-neutral-100 text-muted-foreground";
  return (
    <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${cls}`}>
      {status}
    </span>
  );
}

function formatWhen(iso?: string): string {
  if (!iso) return "";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
}

function isActive(status?: string): boolean {
  const s = status?.toUpperCase();
  return s === "ACTIVE" || s === "GRANTED";
}

function ConsentCard({
  directive,
  onRevoke,
  revoking,
}: {
  directive: ConsentDirective;
  onRevoke: (id: string) => void;
  revoking: boolean;
}) {
  return (
    <li className="rounded-lg border border-border bg-card p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="font-medium text-foreground">
            {directive.granteeRef || directive.purpose || "Consent directive"}
          </p>
          <p className="mt-0.5 text-sm text-muted-foreground">
            {[directive.scope, directive.purpose, directive.provision].filter(Boolean).join(" · ") ||
              "No additional detail"}
          </p>
          <p className="mt-1 text-xs text-muted-foreground">
            {directive.periodStart && <>From {formatWhen(directive.periodStart)} </>}
            {directive.periodEnd && <>until {formatWhen(directive.periodEnd)}</>}
            {directive.revokedAt && <> · Revoked {formatWhen(directive.revokedAt)}</>}
          </p>
        </div>
        <div className="flex shrink-0 flex-col items-end gap-2">
          <StatusBadge status={directive.status} />
          {isActive(directive.status) && (
            <button
              type="button"
              onClick={() => onRevoke(directive.id)}
              disabled={revoking}
              className="text-xs font-medium text-danger underline hover:no-underline disabled:opacity-50"
            >
              {revoking ? "Revoking…" : "Revoke"}
            </button>
          )}
        </div>
      </div>
    </li>
  );
}

function AccessRow({ entry }: { entry: AccessHistoryEntry }) {
  const denied = entry.outcome && entry.outcome.toUpperCase() !== "PERMIT" &&
    entry.outcome.toUpperCase() !== "SUCCESS";
  return (
    <li className="flex items-start gap-3 py-3">
      <div
        className={`mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${
          denied ? "bg-red-100 text-red-700" : "bg-info-soft text-blue-700"
        }`}
      >
        <Eye className="h-4 w-4" />
      </div>
      <div className="min-w-0 flex-1">
        <p className="text-sm text-foreground">
          <span className="font-medium">{prettyActor(entry.actorType)}</span>
          {" "}
          {prettyAction(entry.action)} your record
          {entry.purposeOfUse ? <> for {entry.purposeOfUse.toLowerCase().replace(/_/g, " ")}</> : null}
        </p>
        <p className="mt-0.5 text-xs text-muted-foreground">
          {formatWhen(entry.timestamp)}
          {entry.outcome ? <> · {denied ? "access denied" : "access allowed"}</> : null}
        </p>
      </div>
    </li>
  );
}

function prettyActor(actorType?: string): string {
  if (!actorType) return "Someone";
  const map: Record<string, string> = {
    PROVIDER: "A healthcare provider",
    CITIZEN: "You",
    STAFF: "Facility staff",
    SYSTEM: "An automated system",
  };
  return map[actorType.toUpperCase()] ?? actorType;
}

function prettyAction(action?: string): string {
  if (!action) return "accessed";
  const a = action.toUpperCase();
  if (a.includes("READ") || a.includes("VIEW")) return "viewed";
  if (a.includes("WRITE") || a.includes("UPDATE")) return "updated";
  if (a.includes("CREATE")) return "added to";
  return "accessed";
}

export default function ConsentCenterPage() {
  const { data, isLoading, isError, refetch } = useConsentCenter();
  const revoke = useRevokeConsent();
  const [stepUpMethods, setStepUpMethods] = useState<string[] | null>(null);
  const [pendingRevokeId, setPendingRevokeId] = useState<string | null>(null);

  const center = data?.data;
  const consents = itemsOf(center?.consents);
  const accessLog = center?.accessLog == null ? null : itemsOf(center.accessLog);

  async function handleRevoke(consentId: string) {
    setPendingRevokeId(consentId);
    try {
      await revoke.mutateAsync(consentId);
      setStepUpMethods(null);
      setPendingRevokeId(null);
    } catch (err) {
      const stepUp = readStepUp(err);
      if (stepUp.required) {
        // Keep pendingRevokeId so the StepUpPrompt shows and we can retry after verification.
        setStepUpMethods(stepUp.methods);
        return;
      }
      // A real failure — clear the pending state (do NOT read stepUpMethods here: it is a
      // stale closure value and would wrongly clear the retry target on the step-up path).
      setPendingRevokeId(null);
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-8">
      {stepUpMethods && pendingRevokeId && (
        <StepUpPrompt
          methods={stepUpMethods}
          onCompleted={() => {
            const id = pendingRevokeId;
            setStepUpMethods(null);
            if (id) void handleRevoke(id);
          }}
          onCancel={() => {
            setStepUpMethods(null);
            setPendingRevokeId(null);
          }}
        />
      )}

      {/* Who you've allowed */}
      <section>
        <div className="mb-3 flex items-center gap-2">
          <UserCheck className="h-5 w-5 text-primary" />
          <h2 className="text-lg font-semibold text-foreground">People and services you&apos;ve allowed</h2>
        </div>
        <p className="mb-4 text-sm text-muted-foreground">
          These are the consents you&apos;ve granted for others to access your health record. You can
          withdraw any active consent at any time.
        </p>

        {isLoading ? (
          <p className="text-sm text-muted-foreground">Loading your consents…</p>
        ) : isError ? (
          <div className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-danger">
            <AlertCircle className="h-4 w-4" />
            <span>We couldn&apos;t load your consents.</span>
            <button type="button" onClick={() => void refetch()} className="ml-auto underline">
              Retry
            </button>
          </div>
        ) : consents.length === 0 ? (
          <p className="rounded-lg border border-dashed border-border p-6 text-center text-sm text-muted-foreground">
            You haven&apos;t granted any consents yet. When you allow a provider or service to access
            your record, it will appear here.
          </p>
        ) : (
          <ul className="space-y-3">
            {consents.map((c) => (
              <ConsentCard
                key={c.id}
                directive={c}
                onRevoke={handleRevoke}
                revoking={revoke.isPending && pendingRevokeId === c.id}
              />
            ))}
          </ul>
        )}
      </section>

      {/* Who has accessed your record */}
      <section>
        <div className="mb-3 flex items-center gap-2">
          <Shield className="h-5 w-5 text-primary" />
          <h2 className="text-lg font-semibold text-foreground">Who has accessed your record</h2>
        </div>
        <p className="mb-4 text-sm text-muted-foreground">
          Every access to your health record is recorded. If you see something you don&apos;t
          recognise, contact support.
        </p>

        {isLoading ? (
          <p className="text-sm text-muted-foreground">Loading your access history…</p>
        ) : accessLog === null ? (
          <p className="rounded-lg border border-dashed border-border p-6 text-center text-sm text-muted-foreground">
            Your access history isn&apos;t available yet. It becomes available once your health record
            has a clinical identity.
          </p>
        ) : accessLog.length === 0 ? (
          <p className="rounded-lg border border-dashed border-border p-6 text-center text-sm text-muted-foreground">
            No one has accessed your record yet.
          </p>
        ) : (
          <ul className="divide-y divide-border rounded-lg border border-border bg-card px-4">
            {accessLog.map((e, i) => (
              <AccessRow key={`${e.timestamp}-${i}`} entry={e} />
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
