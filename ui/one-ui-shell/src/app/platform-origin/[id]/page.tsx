"use client";

/**
 * Platform Origin country-operation detail — appointments + the two-person
 * APPROVE/EXECUTE panel + appoint-national-admin form. The appoint flow initiates
 * a PENDING platform action whose accessRequestId is bound to the shared
 * TwoPersonApprovalPanel (approve → execute).
 *
 * Route: /platform-origin/[id] | role-gated PLATFORM_ORIGIN_ADMINISTRATOR.
 */

import Link from "next/link";
import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { ArrowLeft, Globe2, UserPlus } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { TwoPersonApprovalPanel } from "../TwoPersonApprovalPanel";
import {
  useActionApprovals,
  useAppointments,
  useApproveAction,
  useCountryOperation,
  useExecuteAction,
  useInitiateAppointment,
} from "@/hooks/queries/usePlatformOrigin";

function errText(e: unknown): string {
  if (e && typeof e === "object" && "error" in e) {
    return String((e as { error?: { message?: string } }).error?.message ?? "Request failed");
  }
  return e instanceof Error ? e.message : "Request failed";
}

export default function PlatformOriginDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params?.id;
  const user = useAuthStore((s) => s.user);
  const initiatorId = user?.healthId ?? user?.id ?? undefined;

  const detail = useCountryOperation(id);
  const appointments = useAppointments(id);
  const approve = useApproveAction();
  const execute = useExecuteAction();
  const initiate = useInitiateAppointment();

  const [activeActionId, setActiveActionId] = useState("");
  const approvals = useActionApprovals(activeActionId || undefined);

  const [appt, setAppt] = useState({ subjectUserId: "", role: "NATIONAL_ADMINISTRATOR", expiresAt: "" });

  // Reset the approve/execute projection whenever the bound action changes.
  useEffect(() => {
    approve.reset();
    execute.reset();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeActionId]);

  const op = detail.data?.data?.countryOperation;
  const apptRows = appointments.data?.data ?? [];
  const approvalRows = approvals.data?.data ?? [];

  const approveState = approve.data?.data;
  const executed = Boolean(execute.data?.data);
  const recordedFromRows = approvalRows.filter((a) => a.decision?.toUpperCase() === "APPROVE").length;
  const approvalsRecorded = approveState?.approvalsRecorded ?? recordedFromRows;
  const approvalsSatisfied = approveState?.approvalsSatisfied ?? false;
  const status = executed ? "EXECUTED" : approveState?.status ?? (activeActionId ? "PENDING" : "—");

  function submitAppoint() {
    if (!id) return;
    initiate.mutate(
      {
        countryOperationId: id,
        subjectUserId: appt.subjectUserId.trim(),
        role: appt.role.trim() || "NATIONAL_ADMINISTRATOR",
        expiresAt: appt.expiresAt.trim() || undefined,
      },
      { onSuccess: (res) => setActiveActionId(res.data?.accessRequestId ?? "") },
    );
  }

  return (
    <AppLayout>
      <PageShell title="Country operation" subtitle="Appointments and two-person platform actions.">
        <OrganizationPlaneContextBar />

        <div className="mb-6">
          <Link
            href="/platform-origin"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Platform Origin
          </Link>
        </div>

        {/* Detail header */}
        <section className="rounded-lg border border-border bg-card p-5 shadow-sm">
          <div className="flex items-center gap-2">
            <Globe2 className="h-5 w-5 text-muted-foreground" />
            {detail.isLoading ? (
              <span className="text-sm text-muted-foreground">Loading…</span>
            ) : detail.isError ? (
              <span className="text-sm text-red-600">{errText(detail.error)}</span>
            ) : (
              <div>
                <div className="font-medium text-foreground">{op?.displayName ?? op?.isoCountryCode ?? id}</div>
                <div className="text-xs text-muted-foreground">
                  {op?.isoCountryCode ?? "—"} · {op?.status ?? "—"}
                </div>
              </div>
            )}
          </div>
        </section>

        <div className="mt-6 grid gap-6 lg:grid-cols-2">
          {/* Appointments */}
          <section className="overflow-hidden rounded-lg border border-border bg-card shadow-sm">
            <div className="border-b border-border bg-background px-4 py-3 text-sm font-medium text-foreground">
              National appointments
            </div>
            {appointments.isLoading ? (
              <div className="px-4 py-8 text-sm text-muted-foreground">Loading…</div>
            ) : apptRows.length === 0 ? (
              <div className="px-4 py-8 text-sm text-muted-foreground">No appointments yet.</div>
            ) : (
              <ul className="divide-y divide-slate-100">
                {apptRows.map((a) => (
                  <li key={a.id} className="flex items-center justify-between gap-3 px-4 py-3">
                    <div>
                      <div className="font-medium text-foreground">{a.subjectUserId ?? "—"}</div>
                      <div className="text-xs text-muted-foreground">{a.role ?? "—"}</div>
                    </div>
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                        a.status?.toUpperCase() === "ACTIVE"
                          ? "bg-emerald-100 text-emerald-800"
                          : "bg-slate-200 text-slate-700"
                      }`}
                    >
                      {a.status ?? "—"}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </section>

          {/* Appoint national admin */}
          <section className="rounded-lg border border-border bg-card p-5 shadow-sm">
            <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
              <UserPlus className="h-4 w-4 text-primary-hover" /> Appoint national administrator
            </div>
            <p className="mt-1 text-xs text-muted-foreground">
              Initiates a PENDING platform action bound to the approval panel below.
            </p>
            <div className="mt-4 space-y-3">
              <input
                aria-label="Subject user id"
                placeholder="Subject user id"
                value={appt.subjectUserId}
                onChange={(e) => setAppt((f) => ({ ...f, subjectUserId: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
              <input
                aria-label="Role"
                placeholder="Role"
                value={appt.role}
                onChange={(e) => setAppt((f) => ({ ...f, role: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
              <input
                aria-label="Expires at"
                placeholder="Expires at (ISO instant, optional)"
                value={appt.expiresAt}
                onChange={(e) => setAppt((f) => ({ ...f, expiresAt: e.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              />
              {initiate.isError && <p className="text-xs text-red-600">{errText(initiate.error)}</p>}
              <button
                type="button"
                disabled={!appt.subjectUserId.trim() || initiate.isPending}
                onClick={submitAppoint}
                className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-indigo-800 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {initiate.isPending ? "Initiating…" : "Initiate appointment"}
              </button>
            </div>
          </section>
        </div>

        {/* Two-person approve + execute panel */}
        <div className="mt-6">
          <label className="mb-2 block text-xs font-medium text-muted-foreground" htmlFor="active-action">
            Platform action access request id
          </label>
          <input
            id="active-action"
            value={activeActionId}
            onChange={(e) => setActiveActionId(e.target.value)}
            placeholder="Paste a PENDING platform-action access request id, or initiate an appointment above"
            className="mb-4 w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm"
          />
          {activeActionId ? (
            <TwoPersonApprovalPanel
              accessRequestId={activeActionId}
              status={status}
              initiatorId={initiatorId}
              approvalsRequired={2}
              approvalsRecorded={approvalsRecorded}
              approvalsSatisfied={approvalsSatisfied}
              approvals={approvalRows}
              approving={approve.isPending}
              executing={execute.isPending}
              approveError={approve.isError ? errText(approve.error) : null}
              executeError={execute.isError ? errText(execute.error) : null}
              onApprove={({ approverUserId, note }) =>
                approve.mutate(
                  { accessRequestId: activeActionId, approverUserId, note },
                  { onSuccess: () => void approvals.refetch() },
                )
              }
              onExecute={() =>
                execute.mutate(activeActionId, {
                  onSuccess: () => {
                    void appointments.refetch();
                    void detail.refetch();
                  },
                })
              }
            />
          ) : (
            <div className="rounded-xl border border-dashed border-border bg-card px-4 py-8 text-center text-sm text-muted-foreground">
              Initiate an appointment or paste a platform-action id to record approvals.
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
