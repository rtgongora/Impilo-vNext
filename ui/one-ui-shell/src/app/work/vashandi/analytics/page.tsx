"use client";

import { VashandiShell } from "@/components/vashandi/VashandiShell";
import { StaffingAnalyticsCards } from "@/components/vashandi/StaffingAnalyticsCards";
import { RosterCoveragePanel } from "@/components/vashandi/RosterCoveragePanel";
import { VashandiFriendlyBlockedState } from "@/components/vashandi/VashandiFriendlyBlockedState";
import {
  useStaffingAnalytics,
  useRosterCoverageAnalytics,
  useAttendanceAnalytics,
  useAccessRiskAnalytics,
  isVashandiDegraded,
} from "@/hooks/useVashandi";

export default function Page() {
  const staffing = useStaffingAnalytics();
  const coverage = useRosterCoverageAnalytics();
  const attendance = useAttendanceAnalytics();
  const accessRisk = useAccessRiskAnalytics();

  const degraded = [staffing.data?.response, coverage.data?.response, attendance.data?.response, accessRisk.data?.response].find(
    (response) => response && isVashandiDegraded(response),
  );

  return (
    <VashandiShell title="Workforce Analytics" subtitle="Staffing, roster coverage, attendance and access risk metrics">
      {degraded ? (
        <VashandiFriendlyBlockedState
          title={degraded.friendlyTitle ?? "Analytics degraded"}
          description={degraded.friendlyMessage ?? degraded.integrationMessage}
        />
      ) : null}
      <section className="space-y-3">
        <h3 className="font-medium text-foreground">Staffing</h3>
        <StaffingAnalyticsCards analytics={staffing.data?.analytics} isLoading={staffing.isLoading} />
      </section>
      <section className="mt-6 space-y-3">
        <h3 className="font-medium text-foreground">Roster coverage</h3>
        <RosterCoveragePanel coverage={coverage.data?.coverage} isLoading={coverage.isLoading} />
      </section>
      <section className="mt-6 space-y-3">
        <h3 className="font-medium text-foreground">Attendance summary</h3>
        {attendance.data?.analytics ? (
          <div className="grid gap-4 sm:grid-cols-3">
            <div className="rounded-2xl border border-border bg-card p-4">
              <p className="text-sm text-muted-foreground">Check-ins</p>
              <p className="mt-2 text-2xl font-semibold">{attendance.data.analytics.checkIns ?? 0}</p>
            </div>
            <div className="rounded-2xl border border-border bg-card p-4">
              <p className="text-sm text-muted-foreground">Check-outs</p>
              <p className="mt-2 text-2xl font-semibold">{attendance.data.analytics.checkOuts ?? 0}</p>
            </div>
            <div className="rounded-2xl border border-border bg-card p-4">
              <p className="text-sm text-muted-foreground">Supervisor confirmed</p>
              <p className="mt-2 text-2xl font-semibold">{attendance.data.analytics.supervisorConfirmed ?? 0}</p>
            </div>
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">No attendance analytics returned yet.</p>
        )}
      </section>
      <section className="mt-6 space-y-3">
        <h3 className="font-medium text-foreground">Access risk summary</h3>
        {accessRisk.data?.analytics ? (
          <div className="grid gap-4 sm:grid-cols-3">
            <div className="rounded-2xl border border-border bg-card p-4">
              <p className="text-sm text-muted-foreground">Open risks</p>
              <p className="mt-2 text-2xl font-semibold">{accessRisk.data.analytics.openRisks ?? 0}</p>
            </div>
            <div className="rounded-2xl border border-border bg-card p-4">
              <p className="text-sm text-muted-foreground">Critical</p>
              <p className="mt-2 text-2xl font-semibold">{accessRisk.data.analytics.criticalRisks ?? 0}</p>
            </div>
            <div className="rounded-2xl border border-border bg-card p-4">
              <p className="text-sm text-muted-foreground">High</p>
              <p className="mt-2 text-2xl font-semibold">{accessRisk.data.analytics.highRisks ?? 0}</p>
            </div>
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">No access risk analytics returned yet.</p>
        )}
      </section>
    </VashandiShell>
  );
}
