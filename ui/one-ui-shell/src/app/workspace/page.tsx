"use client";

/**
 * Select Workspace — Workspace selection page.
 * Route: /workspace | pageTitle: "Select Workspace"
 */

import Link from "next/link";
import { ArrowRight, Building2, LayoutGrid, Loader2, Sparkles } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useExperienceEntry } from "@/providers/ExperienceEntryProvider";
import { WorkspaceContextOrchestrationRail } from "@/components/workspace-ops/WorkspaceContextOrchestrationRail";

interface WorkspaceResource {
  id: string;
  type: "workspace";
  attributes: {
    name: string;
    workspaceType: string;
    facilityId: string;
    status: string;
    [key: string]: unknown;
  };
}

export default function WorkspacePage() {
  const { facility, workspace, selectWorkspace } = useExperienceEntry();

  const { data, isLoading } = useQuery<ApiResponse<WorkspaceResource[]>>({
    queryKey: ["workspaces", facility?.id],
    queryFn: () =>
      apiClient.get<ApiResponse<WorkspaceResource[]>>(
        `/internal/v1/workspaces?facility_id=${encodeURIComponent(facility?.id ?? "")}`,
      ),
    enabled: !!facility?.id,
  });

  const workspaces = data?.data ?? [];

  function handleSelect(ws: WorkspaceResource) {
    selectWorkspace(
      {
        id: ws.id,
        name: ws.attributes.name,
        workspaceType: ws.attributes.workspaceType,
        facilityId: ws.attributes.facilityId,
      },
      {
        nextPath: "/shift",
      }
    );
  }

  const WORKSPACE_COLORS: Record<string, string> = {
    OPD: "bg-sky-100 text-sky-700",
    IPD: "bg-violet-100 text-violet-700",
    EMERGENCY: "bg-rose-100 text-danger",
    THEATRE: "bg-amber-100 text-warning-foreground",
    LABORATORY: "bg-teal-100 text-teal-700",
    PHARMACY: "bg-emerald-100 text-primary-hover",
  };

  return (
    <AppLayout>
      <PageShell
        title="Select Workspace"
        subtitle={facility ? `Choose your department or service area at ${facility.name}.` : "Choose your department or service area."}
      >
        <div className="space-y-6">
          <WorkspaceContextOrchestrationRail />
          <section className="overflow-hidden rounded-[28px] border border-border bg-[radial-gradient(circle_at_top_left,_rgba(14,165,233,0.16),_transparent_32%),linear-gradient(180deg,#ffffff_0%,#f8fafc_100%)] shadow-sm">
            <div className="border-b border-border px-6 py-5">
              <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
                <div>
                  <div className="inline-flex items-center gap-2 rounded-full border border-sky-200 bg-sky-50 px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.24em] text-sky-700">
                    <Sparkles className="h-3.5 w-3.5" />
                    Experience Entry
                  </div>
                  <h2 className="mt-4 text-2xl font-semibold tracking-tight text-foreground">
                    Select the workspace that matches your current role
                  </h2>
                  <p className="mt-2 max-w-2xl text-sm leading-6 text-muted-foreground">
                    Facility, workspace, and shift are sequenced separately so downstream services receive the right operational context before you enter a care or admin flow.
                  </p>
                </div>
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                  <div className="rounded-2xl border border-white bg-card/80 px-4 py-3">
                    <p className="text-[11px] uppercase tracking-[0.22em] text-muted-foreground">Step</p>
                    <p className="mt-2 text-sm font-semibold text-foreground">2 of 3</p>
                  </div>
                  <div className="rounded-2xl border border-white bg-card/80 px-4 py-3">
                    <p className="text-[11px] uppercase tracking-[0.22em] text-muted-foreground">Facility</p>
                    <p className="mt-2 truncate text-sm font-semibold text-foreground">
                      {facility?.name ?? "Not set"}
                    </p>
                  </div>
                  <div className="rounded-2xl border border-white bg-card/80 px-4 py-3">
                    <p className="text-[11px] uppercase tracking-[0.22em] text-muted-foreground">Next</p>
                    <p className="mt-2 text-sm font-semibold text-foreground">Start shift</p>
                  </div>
                </div>
              </div>
            </div>

            <div className="px-6 py-6">
              {isLoading ? (
                <div className="flex items-center justify-center gap-3 rounded-[24px] border border-dashed border-border bg-card/70 px-6 py-12 text-sm text-muted-foreground">
                  <Loader2 className="h-5 w-5 animate-spin text-sky-500" />
                  Loading workspaces...
                </div>
              ) : workspaces.length === 0 ? (
                <div className="rounded-[24px] border border-dashed border-border bg-card/70 px-6 py-12 text-center">
                  <LayoutGrid className="mx-auto h-10 w-10 text-muted-foreground" />
                  <p className="mt-4 text-sm font-medium text-foreground">
                    No workspaces available at this facility
                  </p>
                  <p className="mt-1 text-sm text-muted-foreground">
                    The selected facility has not published active workspaces yet.
                  </p>
                  <Link
                    href="/facility"
                    className="mt-4 inline-flex items-center gap-2 rounded-full border border-border bg-card px-4 py-2 text-sm font-medium text-foreground transition hover:border-sky-300 hover:text-sky-700"
                  >
                    Choose another facility
                    <ArrowRight className="h-4 w-4" />
                  </Link>
                </div>
              ) : (
                <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
                  {workspaces.map((ws) => {
                    const colorClass =
                      WORKSPACE_COLORS[ws.attributes.workspaceType] ?? "bg-neutral-100 text-foreground";
                    const isSelected = workspace?.id === ws.id;

                    return (
                      <button
                        key={ws.id}
                        type="button"
                        onClick={() => handleSelect(ws)}
                        className={`rounded-[24px] border bg-card p-5 text-left shadow-sm transition-all hover:border-sky-300 hover:bg-sky-50/60 ${
                          isSelected ? "border-sky-400 ring-2 ring-sky-300 ring-offset-2" : "border-border"
                        }`}
                      >
                        <div className="flex items-start justify-between gap-4">
                          <div className="flex min-w-0 items-start gap-4">
                            <div className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl ${colorClass}`}>
                              <LayoutGrid className="h-5 w-5" />
                            </div>
                            <div className="min-w-0">
                              <div className="flex flex-wrap items-center gap-2">
                                <p className="truncate text-base font-semibold text-foreground">
                                  {ws.attributes.name}
                                </p>
                                <span
                                  className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${
                                    ws.attributes.status === "ACTIVE"
                                      ? "bg-emerald-100 text-primary-hover"
                                      : "bg-border text-muted-foreground"
                                  }`}
                                >
                                  {ws.attributes.status}
                                </span>
                              </div>
                              <p className="mt-1 text-sm text-muted-foreground">
                                {ws.attributes.workspaceType.replace(/_/g, " ")}
                              </p>
                              <div className="mt-3 inline-flex items-center gap-2 rounded-full bg-neutral-100 px-3 py-1 text-xs font-medium text-muted-foreground">
                                <Building2 className="h-3.5 w-3.5" />
                                {facility?.name ?? "Facility context"}
                              </div>
                            </div>
                          </div>

                          <div className="inline-flex items-center gap-2 text-sm font-medium text-sky-700">
                            Continue
                            <ArrowRight className="h-4 w-4" />
                          </div>
                        </div>
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
