"use client";

/**
 * Workplace selection hub (D-P3 / PJ5). Route: /provider/workplace
 *
 * A linked, operational provider starts — or switches — a work session here.
 * The hub lists the actor's ACTIVE Vashandi assignments and, on tap, asks the
 * BFF to PROVE the chosen workplace against those assignments and mint a
 * duty-scoped WORK_CONTEXT token. A switch revokes the previous token first, so
 * two contexts are never live at once. Professional scope and live status stay
 * PDP-resolved per action — the token only carries the proven context.
 *
 * Being a professional is not, by itself, a work session: if the chosen
 * workplace is not a live assignment the BFF answers with a GENERIC denial and
 * never reveals which contexts do exist. My Life / My Professional stay
 * reachable regardless of work state.
 */

import { useState } from "react";
import Link from "next/link";
import {
  Building2,
  CheckCircle2,
  Clock,
  LogIn,
  RefreshCw,
  ShieldAlert,
  Stethoscope,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useStartWorkSession,
  useWorkContext,
  workSessionErrorMessage,
  type WorkAssignment,
} from "@/hooks/queries/useWorkSession";
import { useWorkSessionStore } from "@/hooks/useWorkSessionStore";

function assignmentKey(a: WorkAssignment): string {
  return `${a.facilityId}::${a.workspaceId ?? ""}`;
}

function formatExpiry(iso: string | null): string | null {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

export default function WorkplaceSelectionHubPage() {
  const { workContext, isLoading } = useWorkContext();
  const start = useStartWorkSession();
  const session = useWorkSessionStore((s) => s.session);
  const clearSession = useWorkSessionStore((s) => s.clearSession);

  const [error, setError] = useState("");
  const [pendingKey, setPendingKey] = useState<string | null>(null);

  const assignments = workContext.activeAssignments;
  const activeKey = session
    ? `${session.facilityId}::${session.workspaceId ?? ""}`
    : null;

  function handleSelect(a: WorkAssignment) {
    setError("");
    setPendingKey(assignmentKey(a));
    start.mutate(
      { facilityId: a.facilityId, workspaceId: a.workspaceId },
      {
        onSettled: () => setPendingKey(null),
        onError: (err) =>
          setError(
            workSessionErrorMessage(
              err,
              "Professional Work access is currently unavailable for the selected workplace.",
            ),
          ),
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Start a work session"
        subtitle="Choose the workplace you are on duty at — access is bound to a live assignment"
      >
        <div className="mx-auto max-w-2xl space-y-6">
          {/* Active session banner */}
          {session && (
            <div
              className="rounded-2xl border border-success/35 bg-success-soft p-4"
              data-testid="active-work-session"
            >
              <div className="flex items-start gap-3">
                <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-success" />
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold text-foreground">On duty</p>
                  <p className="mt-0.5 text-sm text-muted-foreground">
                    Facility {session.facilityId}
                    {session.roleTemplateId ? ` · ${session.roleTemplateId}` : ""}
                  </p>
                  {formatExpiry(session.expiresAt) && (
                    <p className="mt-1 flex items-center gap-1.5 text-xs text-muted-foreground">
                      <Clock className="h-3 w-3" />
                      Session reissues automatically · valid to {formatExpiry(session.expiresAt)}
                    </p>
                  )}
                  <p className="mt-2 text-xs text-muted-foreground">
                    To move to another workplace, choose it below — this session is revoked first.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={clearSession}
                  className="shrink-0 rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-foreground hover:bg-muted"
                >
                  End session
                </button>
              </div>
            </div>
          )}

          {error && (
            <div className="flex items-start gap-2 rounded-lg border border-danger/28 bg-danger-soft p-3 text-sm text-red-800">
              <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          {/* Assignment picker */}
          <section className="space-y-2">
            <h2 className="text-sm font-semibold text-foreground">Your active assignments</h2>

            {isLoading && (
              <p className="text-sm text-muted-foreground">Loading your assignments…</p>
            )}

            {!isLoading && !workContext.resolved && (
              <div className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
                Your work assignments are unavailable right now. Your professional profile is
                unaffected — try again shortly.
              </div>
            )}

            {!isLoading && workContext.resolved && assignments.length === 0 && (
              <div className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
                You have no active work assignments. A facility must approve a posting before you can
                start a work session.{" "}
                <Link href="/provider/facility-access" className="text-primary hover:underline">
                  Request facility access
                </Link>
                .
              </div>
            )}

            <ul className="space-y-2">
              {assignments.map((a) => {
                const key = assignmentKey(a);
                const isActive = key === activeKey;
                const isPending = pendingKey === key && start.isPending;
                return (
                  <li key={key}>
                    <button
                      type="button"
                      onClick={() => handleSelect(a)}
                      disabled={start.isPending}
                      data-testid="work-assignment-option"
                      className={[
                        "flex w-full items-center gap-3 rounded-2xl border p-4 text-left transition disabled:opacity-60",
                        isActive
                          ? "border-success/40 bg-success-soft"
                          : "border-border bg-card hover:border-primary/40 hover:bg-primary/5",
                      ].join(" ")}
                    >
                      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary/10">
                        <Building2 className="h-5 w-5 text-primary" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-semibold text-foreground">
                          {a.facilityName ?? `Facility ${a.facilityId}`}
                        </p>
                        <p className="mt-0.5 truncate text-xs text-muted-foreground">
                          {[a.workspaceName, a.roleLabel].filter(Boolean).join(" · ") ||
                            "Work assignment"}
                        </p>
                      </div>
                      <span className="shrink-0 text-xs font-medium text-primary">
                        {isPending ? (
                          "Starting…"
                        ) : isActive ? (
                          <span className="inline-flex items-center gap-1 text-success">
                            <CheckCircle2 className="h-3.5 w-3.5" /> On duty
                          </span>
                        ) : session ? (
                          <span className="inline-flex items-center gap-1">
                            <RefreshCw className="h-3.5 w-3.5" /> Switch
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1">
                            <LogIn className="h-3.5 w-3.5" /> Start
                          </span>
                        )}
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          </section>

          <div className="flex items-center justify-center gap-4 text-xs text-muted-foreground">
            <Link
              href="/home/credentials"
              className="inline-flex items-center gap-1.5 hover:text-foreground"
            >
              <Stethoscope className="h-3.5 w-3.5" /> My Professional
            </Link>
            <span aria-hidden>·</span>
            <Link href="/home" className="hover:text-foreground">
              My Life
            </Link>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
