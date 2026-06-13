"use client";

import Link from "next/link";
import { Building2, Clock, LayoutGrid, Shield } from "lucide-react";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import { useShiftStore } from "@/hooks/useShiftStore";

export function WorkspaceContextSettingsPanel() {
  const facility = useFacilityStore((s) => s.facility);
  const workspace = useWorkspaceStore((s) => s.workspace);
  const shift = useShiftStore((s) => s.shift);

  return (
    <div className="space-y-4 rounded-xl border border-border bg-card p-4">
      <div>
        <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Active context</p>
        <p className="mt-1 text-sm text-foreground">
          Workspace settings govern how facility, workspace, and shift identifiers flow into trust headers for downstream
          clinical and operational calls.
        </p>
      </div>

      <dl className="grid gap-3 text-sm sm:grid-cols-3">
        <div className="rounded-lg border border-border bg-background p-3">
          <dt className="flex items-center gap-1 text-xs font-medium text-muted-foreground">
            <Building2 className="h-3.5 w-3.5" /> Facility
          </dt>
          <dd className="mt-1 font-medium text-foreground">{facility?.name ?? "Not selected"}</dd>
          <dd className="font-mono text-xs text-muted-foreground">{facility?.id ?? "—"}</dd>
        </div>
        <div className="rounded-lg border border-border bg-background p-3">
          <dt className="flex items-center gap-1 text-xs font-medium text-muted-foreground">
            <LayoutGrid className="h-3.5 w-3.5" /> Workspace
          </dt>
          <dd className="mt-1 font-medium text-foreground">{workspace?.name ?? "Not selected"}</dd>
          <dd className="font-mono text-xs text-muted-foreground">{workspace?.id ?? "—"}</dd>
        </div>
        <div className="rounded-lg border border-border bg-background p-3">
          <dt className="flex items-center gap-1 text-xs font-medium text-muted-foreground">
            <Clock className="h-3.5 w-3.5" /> Shift
          </dt>
          <dd className="mt-1 font-medium text-foreground">{shift ? "Active" : "Not started"}</dd>
          <dd className="font-mono text-xs text-muted-foreground">{shift?.id ?? "—"}</dd>
        </div>
      </dl>

      <div className="flex flex-wrap gap-2">
        <Link
          href="/facility"
          className="inline-flex items-center gap-1 rounded-lg border border-border px-3 py-2 text-xs font-medium text-foreground hover:bg-background"
        >
          <Building2 className="h-3.5 w-3.5" /> Change facility
        </Link>
        <Link
          href="/workspace"
          className="inline-flex items-center gap-1 rounded-lg border border-border px-3 py-2 text-xs font-medium text-foreground hover:bg-background"
        >
          <LayoutGrid className="h-3.5 w-3.5" /> Change workspace
        </Link>
        <Link
          href="/shift/handover"
          className="inline-flex items-center gap-1 rounded-lg border border-warning/35 bg-warning-soft px-3 py-2 text-xs font-medium text-warning-foreground hover:bg-amber-100"
        >
          <Shield className="h-3.5 w-3.5" /> Shift handover
        </Link>
      </div>
    </div>
  );
}
