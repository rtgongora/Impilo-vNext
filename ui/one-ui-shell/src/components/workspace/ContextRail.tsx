"use client";

import { useExperienceEntry } from "@/providers/ExperienceEntryProvider";
import { useAuthStore } from "@/hooks/useAuthStore";

export function ContextRail(props: {
  patientLabel?: string;
  encounterLabel?: string;
  transactionLabel?: string;
}) {
  const { facility, workspace, shiftActive } = useExperienceEntry();
  const user = useAuthStore((s) => s.user);

  const rows: { label: string; value: string }[] = [];
  if (user?.displayName) rows.push({ label: "Actor", value: user.displayName });
  if (facility?.name) rows.push({ label: "Facility", value: facility.name });
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
