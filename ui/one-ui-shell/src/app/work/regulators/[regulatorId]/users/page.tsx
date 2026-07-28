"use client";

/**
 * Regulator user administration (RB-6). The appointment roster of one regulator and the acts that
 * change it: invite a colleague into a governed role (the RB-4 hashed, single-use invitation rail),
 * verify a pending appointment, end one.
 *
 * This de-stubs the 7-line ScopedAdministrationSurface placeholder. It reads the org-registry
 * appointment roster and drives the BFF regulator user-admin API (/api/v1/regulatory/...). The
 * mutating acts are LOA3-gated and fail-closed at the BFF; ending the last administrator is refused
 * by the org-registry succession guard (409 LAST_ADMINISTRATOR), surfaced here so the operator
 * appoints a replacement first. A visible "two administrators required" state makes the
 * mandatory-second-administrator invariant legible rather than a silent 409.
 *
 * Route: /work/regulators/[regulatorId]/users  (regulatorId = the org-registry organisation UUID)
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft,
  ShieldAlert,
  ShieldCheck,
  UserPlus,
  Clock,
  CheckCircle2,
  XCircle,
} from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";
import { useRegulatoryOrganisations } from "@/hooks/queries/useRegulatoryOrganisations";

interface Appointment {
  id: string;
  personHealthId: string;
  roleCode: string;
  jurisdictionCode?: string;
  status: string;
  source?: string;
  validFrom?: string | null;
  validTo?: string | null;
  verifiedBy?: string | null;
  verifiedAt?: string | null;
}

interface AppointmentRole {
  roleCode: string;
  label: string;
  administrative: boolean;
  bootstrapOnly: boolean;
}

interface ApiError {
  status?: number;
  error?: { code?: string; message?: string };
}

function unwrap<T>(res: unknown): T {
  const data = (res as { data?: unknown })?.data;
  return (data ?? res) as T;
}

function errMessage(e: unknown, fallback: string): string {
  const err = e as ApiError;
  return err?.error?.message || fallback;
}

function daysUntil(dateStr?: string | null): number | null {
  if (!dateStr) return null;
  const then = new Date(dateStr).getTime();
  if (Number.isNaN(then)) return null;
  return Math.floor((then - Date.now()) / 86_400_000);
}

const ACTIVE = "ACTIVE";
const PENDING = "PENDING_VERIFICATION";

export default function RegulatorUsersPage() {
  const params = useParams<{ regulatorId: string }>();
  const orgId = decodeURIComponent(String(params?.regulatorId ?? ""));
  const { organisation } = useRegulatoryOrganisations();
  const org = organisation(orgId);

  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [roles, setRoles] = useState<AppointmentRole[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [banner, setBanner] = useState<{ tone: "ok" | "err"; text: string } | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  // Invite form
  const [inviteeIdentifier, setInviteeIdentifier] = useState("");
  const [inviteeType, setInviteeType] = useState("EMAIL");
  const [inviteRole, setInviteRole] = useState("");
  const [inviting, setInviting] = useState(false);
  const [issuedLink, setIssuedLink] = useState<string | null>(null);

  const loadRoster = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const [rosterRes, rolesRes] = await Promise.all([
        apiClient.get<unknown>(`/api/v1/regulatory/organizations/${encodeURIComponent(orgId)}/appointments`),
        apiClient.get<unknown>(`/api/v1/regulatory/appointment-roles`),
      ]);
      setAppointments(unwrap<Appointment[]>(rosterRes) ?? []);
      setRoles((unwrap<AppointmentRole[]>(rolesRes) ?? []).filter((r) => !r.bootstrapOnly));
    } catch (e) {
      setLoadError(errMessage(e, "The regulator roster could not be loaded."));
    } finally {
      setLoading(false);
    }
  }, [orgId]);

  useEffect(() => {
    if (orgId) void loadRoster();
  }, [orgId, loadRoster]);

  const activeAdministrators = useMemo(() => {
    const adminCodes = new Set(roles.filter((r) => r.administrative).map((r) => r.roleCode));
    return appointments.filter((a) => a.status === ACTIVE && adminCodes.has(a.roleCode)).length;
  }, [appointments, roles]);

  const roleLabel = useCallback(
    (code: string) => roles.find((r) => r.roleCode === code)?.label ?? code,
    [roles],
  );

  const verify = useCallback(
    async (id: string) => {
      setBusyId(id);
      setBanner(null);
      try {
        await apiClient.post<unknown>(`/api/v1/regulatory/appointments/${encodeURIComponent(id)}/verify`, {});
        setBanner({ tone: "ok", text: "Appointment verified." });
        await loadRoster();
      } catch (e) {
        setBanner({ tone: "err", text: errMessage(e, "The appointment could not be verified.") });
      } finally {
        setBusyId(null);
      }
    },
    [loadRoster],
  );

  const end = useCallback(
    async (id: string) => {
      const reason = window.prompt("Reason for ending this appointment (optional):") ?? undefined;
      setBusyId(id);
      setBanner(null);
      try {
        await apiClient.post<unknown>(
          `/api/v1/regulatory/appointments/${encodeURIComponent(id)}/end`,
          { reason },
        );
        setBanner({ tone: "ok", text: "Appointment ended." });
        await loadRoster();
      } catch (e) {
        const err = e as ApiError;
        // The succession guard refuses ending the last administrator — tell the operator why.
        const text =
          err?.error?.code === "LAST_ADMINISTRATOR"
            ? "This is the last active administrator. Appoint a replacement before ending this appointment."
            : errMessage(e, "The appointment could not be ended.");
        setBanner({ tone: "err", text });
      } finally {
        setBusyId(null);
      }
    },
    [loadRoster],
  );

  const submitInvite = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      if (!inviteeIdentifier.trim() || !inviteRole) return;
      setInviting(true);
      setBanner(null);
      setIssuedLink(null);
      try {
        const res = await apiClient.post<unknown>(
          `/api/v1/regulatory/organizations/${encodeURIComponent(orgId)}/invitations`,
          {
            inviteeIdentifier: inviteeIdentifier.trim(),
            inviteeIdentifierType: inviteeType,
            roleCode: inviteRole,
          },
        );
        const invitation = unwrap<{ rawToken?: string }>(res);
        // The raw token is returned exactly once — build the one-time acceptance link for the issuer
        // to hand over out of band. It is never persisted; a page reload will not show it again.
        if (invitation?.rawToken) {
          setIssuedLink(`${window.location.origin}/onboarding/accept-invitation?token=${invitation.rawToken}`);
        }
        setBanner({ tone: "ok", text: `Invitation issued to ${inviteeIdentifier.trim()}.` });
        setInviteeIdentifier("");
        setInviteRole("");
        await loadRoster();
      } catch (err) {
        setBanner({ tone: "err", text: errMessage(err, "The invitation could not be issued.") });
      } finally {
        setInviting(false);
      }
    },
    [inviteeIdentifier, inviteeType, inviteRole, orgId, loadRoster],
  );

  return (
    <AppLayout>
      <PageShell
        title="Regulator user administration"
        subtitle={org ? `${org.name} — appointments, roles and invitations` : "Regulator appointments"}
        serviceSlug="varapi"
      >
        <div className="space-y-5">
          <Link
            href={`/work/regulatory/${encodeURIComponent(orgId)}`}
            className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
          >
            <ArrowLeft className="h-4 w-4" /> Back to workspace
          </Link>

          {/* Mandatory-second-administrator invariant, made legible */}
          {!loading && activeAdministrators < 2 && (
            <div className="flex items-start gap-3 rounded-xl border border-amber-300 bg-amber-50 p-4 text-amber-900 dark:border-amber-500/40 dark:bg-amber-500/10 dark:text-amber-200">
              <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0" />
              <div className="text-sm">
                <p className="font-semibold">Two administrators are required.</p>
                <p className="mt-0.5">
                  This regulator has {activeAdministrators} active administrator
                  {activeAdministrators === 1 ? "" : "s"}. Invite a second administrator so no single
                  person is a point of failure — the last administrator cannot be ended until a
                  replacement is in place.
                </p>
              </div>
            </div>
          )}

          {banner && (
            <div
              className={`flex items-start gap-2 rounded-lg border p-3 text-sm ${
                banner.tone === "ok"
                  ? "border-emerald-300 bg-emerald-50 text-emerald-800 dark:border-emerald-500/40 dark:bg-emerald-500/10 dark:text-emerald-200"
                  : "border-rose-300 bg-rose-50 text-rose-800 dark:border-rose-500/40 dark:bg-rose-500/10 dark:text-rose-200"
              }`}
            >
              {banner.tone === "ok" ? (
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" />
              ) : (
                <XCircle className="mt-0.5 h-4 w-4 shrink-0" />
              )}
              <span>{banner.text}</span>
            </div>
          )}

          {/* Roster */}
          <LuminousStage className="space-y-4 p-5">
            <div className="flex items-center justify-between">
              <h2 className="text-base font-semibold text-slate-800 dark:text-slate-100">
                Appointment roster
              </h2>
              <span className="text-xs text-slate-500 dark:text-slate-400">
                {appointments.length} appointment{appointments.length === 1 ? "" : "s"}
              </span>
            </div>

            {loading ? (
              <p className="py-6 text-center text-sm text-slate-500">Loading roster…</p>
            ) : loadError ? (
              <p className="py-6 text-center text-sm text-rose-600">{loadError}</p>
            ) : appointments.length === 0 ? (
              <p className="py-6 text-center text-sm text-slate-500">
                No appointments yet. Invite the first colleague below.
              </p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[640px] text-sm">
                  <thead>
                    <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-500 dark:border-slate-700">
                      <th className="pb-2 pr-3 font-medium">Person</th>
                      <th className="pb-2 pr-3 font-medium">Role</th>
                      <th className="pb-2 pr-3 font-medium">Jurisdiction</th>
                      <th className="pb-2 pr-3 font-medium">Status</th>
                      <th className="pb-2 pr-3 font-medium">Validity</th>
                      <th className="pb-2 font-medium text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {appointments.map((a) => {
                      const days = daysUntil(a.validTo);
                      const expiring = a.status === ACTIVE && days !== null && days <= 30;
                      const expired = a.status === ACTIVE && days !== null && days < 0;
                      return (
                        <tr
                          key={a.id}
                          className="border-b border-slate-100 last:border-0 dark:border-slate-800"
                        >
                          <td className="py-2.5 pr-3 font-mono text-xs text-slate-700 dark:text-slate-300">
                            {a.personHealthId}
                          </td>
                          <td className="py-2.5 pr-3 text-slate-700 dark:text-slate-200">
                            {roleLabel(a.roleCode)}
                          </td>
                          <td className="py-2.5 pr-3 text-slate-500 dark:text-slate-400">
                            {a.jurisdictionCode ?? "NATIONAL"}
                          </td>
                          <td className="py-2.5 pr-3">
                            <StatusPill status={a.status} />
                          </td>
                          <td className="py-2.5 pr-3 text-xs text-slate-500 dark:text-slate-400">
                            {a.validTo ? (
                              <span
                                className={
                                  expired
                                    ? "text-rose-600 dark:text-rose-400"
                                    : expiring
                                      ? "text-amber-600 dark:text-amber-400"
                                      : ""
                                }
                              >
                                {expired ? (
                                  <>
                                    <Clock className="mr-1 inline h-3 w-3" />
                                    expired
                                  </>
                                ) : expiring ? (
                                  <>
                                    <Clock className="mr-1 inline h-3 w-3" />
                                    {days}d left
                                  </>
                                ) : (
                                  `to ${a.validTo}`
                                )}
                              </span>
                            ) : (
                              "—"
                            )}
                          </td>
                          <td className="py-2.5 text-right">
                            <div className="inline-flex gap-2">
                              {a.status === PENDING && (
                                <button
                                  onClick={() => verify(a.id)}
                                  disabled={busyId === a.id}
                                  className="inline-flex items-center gap-1 rounded-md bg-emerald-600 px-2.5 py-1 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
                                >
                                  <ShieldCheck className="h-3 w-3" /> Verify
                                </button>
                              )}
                              {a.status === ACTIVE && (
                                <button
                                  onClick={() => end(a.id)}
                                  disabled={busyId === a.id}
                                  className="inline-flex items-center gap-1 rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-800"
                                >
                                  <XCircle className="h-3 w-3" /> End
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </LuminousStage>

          {/* Invite */}
          <LuminousStage className="space-y-4 p-5">
            <div className="flex items-center gap-2">
              <UserPlus className="h-4 w-4 text-slate-500" />
              <h2 className="text-base font-semibold text-slate-800 dark:text-slate-100">
                Invite a colleague
              </h2>
            </div>
            <p className="text-sm text-slate-500 dark:text-slate-400">
              An invitation issues a single-use link. Accepting it creates a pending appointment in
              the chosen role — never an active one; an administrator still verifies it above.
            </p>
            <form onSubmit={submitInvite} className="grid gap-3 sm:grid-cols-[1fr_auto_auto_auto]">
              <input
                type="text"
                required
                value={inviteeIdentifier}
                onChange={(e) => setInviteeIdentifier(e.target.value)}
                placeholder={inviteeType === "EMAIL" ? "colleague@council.gov.zw" : "Health ID"}
                className="rounded-lg border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-900"
              />
              <select
                value={inviteeType}
                onChange={(e) => setInviteeType(e.target.value)}
                className="rounded-lg border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-900"
              >
                <option value="EMAIL">Email</option>
                <option value="HEALTH_ID">Health ID</option>
              </select>
              <select
                required
                value={inviteRole}
                onChange={(e) => setInviteRole(e.target.value)}
                className="rounded-lg border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-900"
              >
                <option value="">Select role…</option>
                {roles.map((r) => (
                  <option key={r.roleCode} value={r.roleCode}>
                    {r.label}
                    {r.administrative ? " (admin)" : ""}
                  </option>
                ))}
              </select>
              <button
                type="submit"
                disabled={inviting || !inviteeIdentifier.trim() || !inviteRole}
                className="inline-flex items-center justify-center gap-1 rounded-lg bg-slate-800 px-4 py-2 text-sm font-medium text-white hover:bg-slate-900 disabled:opacity-50 dark:bg-slate-200 dark:text-slate-900 dark:hover:bg-white"
              >
                {inviting ? "Issuing…" : "Send invitation"}
              </button>
            </form>

            {issuedLink && (
              <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs dark:border-slate-700 dark:bg-slate-800/50">
                <p className="mb-1 font-medium text-slate-700 dark:text-slate-200">
                  One-time acceptance link (shown once — copy it now):
                </p>
                <code className="block break-all text-slate-600 dark:text-slate-300">{issuedLink}</code>
              </div>
            )}
          </LuminousStage>
        </div>
      </PageShell>
    </AppLayout>
  );
}

function StatusPill({ status }: { status: string }) {
  const map: Record<string, string> = {
    ACTIVE: "bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300",
    PENDING_VERIFICATION: "bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300",
    ENDED: "bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-300",
    EXPIRED: "bg-rose-100 text-rose-700 dark:bg-rose-500/15 dark:text-rose-300",
  };
  const label = status.replace(/_/g, " ").toLowerCase();
  return (
    <span
      className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium capitalize ${
        map[status] ?? "bg-slate-100 text-slate-600"
      }`}
    >
      {label}
    </span>
  );
}
