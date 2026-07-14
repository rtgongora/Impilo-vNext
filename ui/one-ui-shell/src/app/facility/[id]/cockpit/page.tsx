"use client";

/**
 * Facility Mode cockpit — home / overview / quick-actions.
 * Route: /facility/[id]/cockpit
 *
 * T4 owns this route (the destination a provider lands in after ENTERing facility
 * mode). Rendered strictly from the FacilityModeContext read-model produced by TUSO
 * and composed by the experience-bff FacilityModeController.
 */

import { useParams, useSearchParams } from "next/navigation";
import Link from "next/link";
import { Loader2, AlertTriangle, ArrowLeft, BadgeCheck, ShieldCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { CockpitOverview } from "@/components/facility-mode/CockpitOverview";
import { FacilityCompletenessBanner } from "@/components/facility/FacilityCompletenessBanner";
import { useFacilityModeContext } from "@/components/facility-mode/useFacilityMode";
import { useFacilityProfile } from "@/hooks/queries/useFacilityRegulatory";

export default function FacilityCockpitPage() {
  const params = useParams();
  const searchParams = useSearchParams();
  const id = params.id as string;
  const pctFacilityId = searchParams.get("pctFacilityId") ?? undefined;

  const { data, isLoading, error } = useFacilityModeContext(id, pctFacilityId);

  return (
    <AppLayout>
      <PageShell title="Facility Mode" subtitle="Cockpit overview and quick actions">
        <div className="mb-4">
          <Link
            href="/facility"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Back to facilities
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading facility mode…</span>
          </div>
        ) : error || !data ? (
          <div className="rounded-lg border border-danger/28 bg-danger-soft p-6 text-center">
            <AlertTriangle className="mx-auto mb-2 h-8 w-8 text-red-400" />
            <p className="text-sm text-red-600">
              Failed to load facility-mode context. The facility registry (TUSO) may be
              unavailable.
            </p>
          </div>
        ) : (
          <div className="max-w-4xl space-y-6">
            {/* Completeness prompt for the facility manager / PIC — the cockpit is a
                management surface (display gate); TUSO enforces the write authz. */}
            <FacilityCompletenessBanner facilityId={id} />
            <CockpitOverview facilityId={id} envelope={data} />
            <RegulatoryStatusPanel facilityId={id} />
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}

/**
 * Compact HPA regulatory posture card — regulatory status, active certificate
 * (with public verification link when a verification code exists), active
 * practitioner-in-charge, open applications, and the next regulatory action.
 * Sourced from the same governed facility-registry profile that backs the
 * regulator's facility file.
 */
function RegulatoryStatusPanel({ facilityId }: { facilityId: string }) {
  const profileQuery = useFacilityProfile(facilityId);
  const profile = profileQuery.data?.data;

  if (profileQuery.isLoading) {
    return (
      <section className="rounded-lg border border-border bg-card p-4">
        <h3 className="mb-3 flex items-center gap-2 text-sm font-medium text-foreground">
          <ShieldCheck className="h-4 w-4" /> Regulatory status
        </h3>
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading regulatory profile…
        </div>
      </section>
    );
  }

  if (!profile) {
    return (
      <section className="rounded-lg border border-border bg-card p-4">
        <h3 className="mb-3 flex items-center gap-2 text-sm font-medium text-foreground">
          <ShieldCheck className="h-4 w-4" /> Regulatory status
        </h3>
        <p className="text-xs text-muted-foreground">
          No regulatory file is available for this facility in the HPA register.
        </p>
      </section>
    );
  }

  const regulatoryStatus = profile.master.regulatoryStatus;
  const activeCertificate =
    profile.certificates.find((c) => c.status === "ACTIVE") ?? profile.certificates[0];
  const verificationCode = activeCertificate
    ? ((activeCertificate as unknown as { verificationCode?: string }).verificationCode ??
      (activeCertificate.metadata?.verificationCode as string | undefined))
    : undefined;
  const activePic = profile.practitionerAssignments.find((p) => p.endDate === null);
  const picReviewState = activePic
    ? (activePic as unknown as { reviewState?: string }).reviewState
    : undefined;
  const openApplications = profile.applications.filter((a) => !a.finalDecision).length;

  return (
    <section className="rounded-lg border border-border bg-card p-4">
      <div className="mb-3 flex items-center justify-between gap-2">
        <h3 className="flex items-center gap-2 text-sm font-medium text-foreground">
          <ShieldCheck className="h-4 w-4" /> Regulatory status
        </h3>
        <span
          className={`rounded-full px-2.5 py-0.5 text-[10px] font-medium uppercase tracking-wide ${
            regulatoryStatus === "REGISTERED" || regulatoryStatus === "LICENSED"
              ? "bg-emerald-500/10 text-emerald-600"
              : regulatoryStatus === "RENEWAL_DUE"
                ? "bg-amber-500/10 text-amber-600"
                : "bg-muted text-muted-foreground"
          }`}
        >
          {regulatoryStatus.replace(/_/g, " ")}
        </span>
      </div>

      <dl className="space-y-1.5 text-xs">
        <div className="flex items-center justify-between gap-2">
          <dt className="text-muted-foreground">Active certificate</dt>
          <dd className="text-right text-foreground">
            {activeCertificate ? (
              <>
                {activeCertificate.certificateNumber}
                {activeCertificate.expiryDate
                  ? ` · expires ${activeCertificate.expiryDate.slice(0, 10)}`
                  : ""}
                {verificationCode && (
                  <Link
                    href={`/verify/facility-certificate?code=${encodeURIComponent(verificationCode)}`}
                    className="ml-2 inline-flex items-center gap-1 text-primary hover:underline"
                  >
                    <BadgeCheck className="h-3 w-3" /> Verify
                  </Link>
                )}
              </>
            ) : (
              "None issued"
            )}
          </dd>
        </div>
        <div className="flex items-center justify-between gap-2">
          <dt className="text-muted-foreground">Practitioner in charge</dt>
          <dd className="text-right text-foreground">
            {activePic ? (
              <>
                {activePic.providerPublicId}
                {picReviewState === "REVIEW_REQUIRED" && (
                  <span className="ml-2 rounded-full bg-amber-500/10 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide text-amber-600">
                    Review required
                  </span>
                )}
              </>
            ) : (
              "Not assigned"
            )}
          </dd>
        </div>
        <div className="flex items-center justify-between gap-2">
          <dt className="text-muted-foreground">Open applications</dt>
          <dd className="text-foreground">{openApplications}</dd>
        </div>
      </dl>

      {regulatoryStatus === "RENEWAL_DUE" && (
        <p className="mt-2 flex items-center gap-1.5 rounded-md bg-amber-500/10 px-2 py-1.5 text-[11px] text-amber-700">
          <AlertTriangle className="h-3.5 w-3.5 shrink-0" />
          Registration renewal is due — submit a renewal application before the certificate expires.
        </p>
      )}
    </section>
  );
}
