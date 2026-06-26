"use client";

import { useExperienceEntry } from "@/providers/ExperienceEntryProvider";
import { useAuthStore } from "@/hooks/useAuthStore";

/**
 * Extended WHERE/WHAT context dimensions, sourced from the C2 work-context
 * read-model (Vashandi via BFF /internal/v1/work-context). Surface the active
 * assignment granularity (department / ward / service-point / virtual-pool /
 * above-site) so the rail reflects the operating context the picker selected,
 * not just facility + workspace.
 */
export interface ContextRailWhere {
  departmentLabel?: string;
  unitLabel?: string; // ward / service-point
  servicePointLabel?: string;
  virtualPoolLabel?: string;
  scope?: string; // FACILITY | DEPARTMENT | WARD | SERVICE_POINT | VIRTUAL_POOL | ABOVE_SITE
}

const SCOPE_LABELS: Record<string, string> = {
  FACILITY: "Facility-wide",
  DEPARTMENT: "Department",
  WARD: "Ward",
  SERVICE_POINT: "Service point",
  VIRTUAL_POOL: "Virtual pool",
  ABOVE_SITE: "Above-site",
};

export function ContextRail(props: {
  patientLabel?: string;
  encounterLabel?: string;
  transactionLabel?: string;
  where?: ContextRailWhere;
}) {
  const { facility, workspace, shiftActive } = useExperienceEntry();
  const user = useAuthStore((s) => s.user);
  const where = props.where;

  const rows: { label: string; value: string }[] = [];
  if (user?.displayName) rows.push({ label: "Actor", value: user.displayName });
  if (facility?.name) rows.push({ label: "Facility", value: facility.name });
  if (where?.scope && SCOPE_LABELS[where.scope]) {
    rows.push({ label: "Scope", value: SCOPE_LABELS[where.scope] });
  }
  if (where?.departmentLabel) rows.push({ label: "Department", value: where.departmentLabel });
  if (where?.unitLabel) rows.push({ label: "Ward / Unit", value: where.unitLabel });
  if (where?.servicePointLabel) rows.push({ label: "Service point", value: where.servicePointLabel });
  if (where?.virtualPoolLabel) rows.push({ label: "Virtual pool", value: where.virtualPoolLabel });
  if (workspace?.name) rows.push({ label: "Workspace", value: workspace.name });
  if (shiftActive) rows.push({ label: "Shift", value: "Active" });
  if (props.patientLabel) rows.push({ label: "Patient", value: props.patientLabel });
  if (props.encounterLabel) rows.push({ label: "Encounter", value: props.encounterLabel });
  if (props.transactionLabel) rows.push({ label: "Transaction", value: props.transactionLabel });

  if (rows.length === 0) return null;

  return (
    <div className="impilo-surface-soft p-3 text-xs">
      <p className="mb-2 font-semibold uppercase tracking-wide text-[var(--text-muted)]">Context</p>
      <dl className="space-y-1.5">
        {rows.map((row) => (
          <div key={row.label} className="flex justify-between gap-2">
            <dt className="text-[var(--text-secondary)]">{row.label}</dt>
            <dd className="truncate font-medium text-[var(--text-primary)]">{row.value}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}
