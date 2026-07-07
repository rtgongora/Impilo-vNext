"use client";

/**
 * Facility Mode dashboard (IATG Journey E). A facility-wide operational/admin
 * context for a provider who administers THIS facility. Admin actions are gated
 * on an active facility-admin appointment for this facility (not a global role),
 * so no one administers a facility they hold no appointment for.
 */

import type { ReactNode } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import {
  Building2,
  CheckCircle2,
  Clock,
  Loader2,
  ShieldAlert,
  ShieldCheck,
  Users,
} from "lucide-react";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStatusComposite } from "@/hooks/queries/useFacilityStatusComposite";
import { FacilityLegitimacyPanel } from "@/components/facility/FacilityLegitimacyPanel";
import {
  administersFacility,
  pendingAppointments,
  useFacilityAppointments,
} from "@/hooks/queries/useFacilityMode";
import { useApproveFacilityAppointment } from "@/hooks/queries/useFacilityClaim";

interface FacilityIdentity {
  id: string;
  attributes: { name: string; code: string; facilityType: string; status: string; district: string; province: string };
}

function Card({ title, icon: Icon, children }: { title: string; icon: typeof Building2; children: ReactNode }) {
  return (
    <section className="rounded-lg border border-border bg-card p-5">
      <h3 className="mb-3 flex items-center gap-2 font-medium text-foreground">
        <Icon className="h-4 w-4 text-primary" /> {title}
      </h3>
      {children}
    </section>
  );
}

export function FacilityModeDashboard({ facilityId }: { facilityId: string }) {
  const user = useAuthStore((s) => s.user);
  const healthId = user?.healthId ?? user?.id ?? undefined;

  const identityQ = useQuery<ApiResponse<FacilityIdentity>>({
    queryKey: ["facility-detail", facilityId],
    queryFn: () => apiClient.get<ApiResponse<FacilityIdentity>>(`/internal/v1/facilities/${facilityId}`),
    enabled: !!facilityId,
  });
  const { data: legit } = useFacilityStatusComposite(facilityId);
  const appointmentsQ = useFacilityAppointments(facilityId);
  const approve = useApproveFacilityAppointment();

  const facility = identityQ.data?.data;
  const appointments = appointmentsQ.data?.data?.appointments;
  const administers = administersFacility(appointments, healthId);
  const pending = pendingAppointments(appointments);
  const composite = legit?.data;

  return (
    <div className="space-y-4">
      {/* Facility Mode banner (current context after switch) */}
      <div className="flex items-center justify-between gap-3 rounded-lg border border-primary/25 bg-primary-soft px-4 py-3">
        <div className="flex items-center gap-2">
          <Building2 className="h-5 w-5 text-primary" />
          <div>
            <p className="text-sm font-semibold text-primary-hover">Facility Mode</p>
            <p className="text-xs text-primary">{facility?.attributes.name ?? "Loading facility…"}</p>
          </div>
        </div>
        {administers ? (
          <span className="inline-flex items-center gap-1 rounded-full border border-emerald-300 bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/30 dark:text-emerald-400">
            <ShieldCheck className="h-3 w-3" /> You administer this facility
          </span>
        ) : null}
      </div>

      {!administers ? (
        <div
          role="note"
          data-testid="facility-mode-not-admin"
          className="rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-700 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-400"
        >
          <p className="flex items-center gap-2 font-medium">
            <ShieldAlert className="h-4 w-4" /> You don&apos;t administer this facility yet
          </p>
          <p className="mt-1 text-xs">
            Facility-mode admin actions are hidden because you hold no active facility-admin appointment here.
            If you should administer it,{" "}
            <Link href={`/facility/claim?facilityUuid=${facilityId}`} className="font-medium underline">
              submit a facility claim
            </Link>
            . Identity and legitimacy below remain read-only.
          </p>
        </div>
      ) : null}

      {/* Card 1 — Facility identity */}
      <Card title="Facility identity" icon={Building2}>
        {identityQ.isLoading ? (
          <p className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</p>
        ) : facility ? (
          <dl className="grid grid-cols-[auto,1fr] gap-x-4 gap-y-1 text-sm">
            <dt className="text-muted-foreground">Name</dt><dd className="text-foreground">{facility.attributes.name}</dd>
            <dt className="text-muted-foreground">Code</dt><dd className="font-mono text-foreground">{facility.attributes.code}</dd>
            <dt className="text-muted-foreground">Type</dt><dd className="text-foreground">{facility.attributes.facilityType}</dd>
            <dt className="text-muted-foreground">District</dt><dd className="text-foreground">{facility.attributes.district}, {facility.attributes.province}</dd>
          </dl>
        ) : (
          <p className="text-sm text-muted-foreground">Facility identity is unavailable right now.</p>
        )}
      </Card>

      {/* Cards 2 + 6 — Legitimacy / compliance + warnings */}
      {composite ? (
        <FacilityLegitimacyPanel composite={composite} facilityId={facilityId} />
      ) : (
        <Card title="Legitimacy & platform access" icon={ShieldCheck}>
          <p className="text-sm text-muted-foreground">
            Legitimacy could not be verified right now — the verdict is withheld until TUSO is reachable.
          </p>
        </Card>
      )}

      {/* Card 5 — Pending claims / requests */}
      <Card title="Pending facility-admin appointments" icon={Clock}>
        {appointmentsQ.isLoading ? (
          <p className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</p>
        ) : pending.length === 0 ? (
          <p className="text-sm text-muted-foreground" data-testid="facility-mode-no-pending">No pending appointments.</p>
        ) : (
          <ul className="space-y-2" data-testid="facility-mode-pending-list">
            {pending.map((a) => (
              <li key={a.id} className="flex items-center justify-between gap-2 rounded-md border border-border px-3 py-2 text-sm">
                <span>
                  <span className="font-mono text-foreground">{a.personHealthId}</span>
                  <span className="ml-2 text-xs text-muted-foreground">{a.role ?? "administrator"}</span>
                </span>
                {administers ? (
                  <button
                    type="button"
                    className="inline-flex items-center gap-1 rounded-md bg-primary-hover px-2 py-1 text-xs font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
                    disabled={approve.isPending}
                    onClick={() => approve.mutate({ appointmentId: String(a.id) })}
                  >
                    {approve.isPending ? <Loader2 className="h-3 w-3 animate-spin" /> : <CheckCircle2 className="h-3 w-3" />}
                    Approve
                  </button>
                ) : (
                  <span className="inline-flex items-center gap-1 rounded-full border border-amber-300 bg-amber-50 px-2 py-0.5 text-xs text-amber-700"><Clock className="h-3 w-3" /> pending</span>
                )}
              </li>
            ))}
          </ul>
        )}
        {approve.isError ? (
          <p className="mt-2 text-xs text-destructive">Approval failed. Nothing was changed — retry.</p>
        ) : null}
      </Card>

      {/* Cards 3 + 4 — not yet surfaced in Facility Mode (honest placeholder, fast-follow) */}
      <Card title="Organisation affiliation & staff roster" icon={Users}>
        <p className="text-sm text-muted-foreground">
          Organisation affiliation and the full staff roster are not yet surfaced in Facility Mode — they arrive with a
          follow-up wave and will appear here when backed by real reads. Nothing is faked in the meantime.
        </p>
      </Card>
    </div>
  );
}
