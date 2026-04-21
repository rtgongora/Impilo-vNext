"use client";

import Link from "next/link";
import type { LucideIcon } from "lucide-react";
import {
  ArrowRight,
  Building2,
  Loader2,
  MapPin,
  Sparkles,
} from "lucide-react";
import type { FacilityResource } from "@/hooks/queries/useFacilities";

interface ModeAction {
  label: string;
  description: string;
  icon: LucideIcon;
  onClick?: () => void;
  href?: string;
}

interface WorkplaceSelectionHubProps {
  facilities: FacilityResource[];
  isLoading: boolean;
  onSelectFacility: (facility: FacilityResource) => void;
  title?: string;
  subtitle?: string;
  selectedFacilityId?: string | null;
  maxVisible?: number;
  browseHref?: string;
  modeActions?: ModeAction[];
}

function facilityCardTone(status: string) {
  if (status === "ACTIVE") {
    return "border-sky-200 bg-white hover:border-sky-400 hover:bg-sky-50/80";
  }
  return "border-slate-200 bg-slate-50";
}

function formatEnumLabel(value?: string) {
  if (!value) return null;
  return value
    .toLowerCase()
    .split("_")
    .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join(" ");
}

function getFacilityOperatingModel(attributes: FacilityResource["attributes"]) {
  return attributes.operatingModel ?? {
    facilityTier: attributes.facilityTier,
    deploymentMode: attributes.deploymentMode,
    continuityClass: attributes.continuityClass,
    workflowArchetype: attributes.workflowArchetype,
  };
}

export function WorkplaceSelectionHub({
  facilities,
  isLoading,
  onSelectFacility,
  title = "Choose where you are working",
  subtitle = "Start with a facility, then narrow into the workspace and shift that define your live service context.",
  selectedFacilityId,
  maxVisible,
  browseHref = "/facility",
  modeActions = [],
}: WorkplaceSelectionHubProps) {
  const visibleFacilities =
    typeof maxVisible === "number" ? facilities.slice(0, maxVisible) : facilities;
  const hasMore = typeof maxVisible === "number" && facilities.length > maxVisible;

  return (
    <section className="overflow-hidden rounded-[28px] border border-slate-200 bg-[radial-gradient(circle_at_top_left,_rgba(14,165,233,0.18),_transparent_38%),linear-gradient(135deg,#f8fbff_0%,#ffffff_45%,#f8fafc_100%)] shadow-sm">
      <div className="border-b border-slate-200/80 px-6 py-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div className="max-w-2xl">
            <div className="inline-flex items-center gap-2 rounded-full border border-sky-200 bg-sky-50 px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.24em] text-sky-700">
              <Sparkles className="h-3.5 w-3.5" />
              Experience Entry
            </div>
            <h2 className="mt-4 text-2xl font-semibold tracking-tight text-slate-950">
              {title}
            </h2>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">
              {subtitle}
            </p>
          </div>

          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            <div className="rounded-2xl border border-white bg-white/80 px-4 py-3">
              <p className="text-[11px] uppercase tracking-[0.22em] text-slate-400">
                Facilities
              </p>
              <p className="mt-2 text-lg font-semibold text-slate-950">{facilities.length}</p>
            </div>
            <div className="rounded-2xl border border-white bg-white/80 px-4 py-3">
              <p className="text-[11px] uppercase tracking-[0.22em] text-slate-400">
                Step 1
              </p>
              <p className="mt-2 text-sm font-semibold text-slate-950">Pick facility</p>
            </div>
            <div className="rounded-2xl border border-white bg-white/80 px-4 py-3">
              <p className="text-[11px] uppercase tracking-[0.22em] text-slate-400">
                Next
              </p>
              <p className="mt-2 text-sm font-semibold text-slate-950">Workspace and shift</p>
            </div>
          </div>
        </div>
      </div>

      <div className="px-6 py-6">
        {isLoading ? (
          <div className="flex items-center justify-center gap-3 rounded-[24px] border border-dashed border-slate-200 bg-white/70 px-6 py-12 text-sm text-slate-500">
            <Loader2 className="h-5 w-5 animate-spin text-sky-500" />
            Loading facilities...
          </div>
        ) : visibleFacilities.length === 0 ? (
          <div className="rounded-[24px] border border-dashed border-slate-200 bg-white/70 px-6 py-12 text-center">
            <Building2 className="mx-auto h-10 w-10 text-slate-300" />
            <p className="mt-4 text-sm font-medium text-slate-700">No facilities available yet</p>
            <p className="mt-1 text-sm text-slate-500">
              Once facilities are provisioned, they will appear here for inline selection.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
            {visibleFacilities.map((facility) => {
              const attributes = facility.attributes;
              const isSelected = selectedFacilityId === facility.id;
              const capabilities = attributes.capabilities ?? [];
              const operatingModel = getFacilityOperatingModel(attributes);
              const tierLabel = formatEnumLabel(operatingModel.facilityTier);
              const deploymentLabel = formatEnumLabel(operatingModel.deploymentMode);
              const continuityLabel = formatEnumLabel(operatingModel.continuityClass);

              return (
                <button
                  key={facility.id}
                  type="button"
                  onClick={() => onSelectFacility(facility)}
                  className={[
                    "group rounded-[24px] border p-5 text-left shadow-sm transition-all",
                    facilityCardTone(attributes.status),
                    isSelected ? "ring-2 ring-sky-400 ring-offset-2" : "",
                  ].join(" ")}
                >
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex min-w-0 items-start gap-4">
                      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-sky-100 text-sky-700 transition-colors group-hover:bg-sky-200">
                        <Building2 className="h-5 w-5" />
                      </div>
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <p className="truncate text-base font-semibold text-slate-950">
                            {attributes.name}
                          </p>
                          <span
                            className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${
                              attributes.status === "ACTIVE"
                                ? "bg-emerald-100 text-emerald-700"
                                : "bg-slate-200 text-slate-600"
                            }`}
                          >
                            {attributes.status}
                          </span>
                        </div>
                        <p className="mt-1 text-sm text-slate-500">{attributes.facilityType}</p>
                        <div className="mt-3 flex items-center gap-2 text-xs text-slate-500">
                          <MapPin className="h-3.5 w-3.5" />
                          <span>{attributes.code}</span>
                        </div>
                        {(tierLabel || deploymentLabel || continuityLabel) && (
                          <div className="mt-3 flex flex-wrap gap-2">
                            {tierLabel && (
                              <span className="rounded-full bg-sky-100 px-2.5 py-1 text-[11px] font-medium text-sky-700">
                                {tierLabel}
                              </span>
                            )}
                            {deploymentLabel && (
                              <span className="rounded-full bg-emerald-100 px-2.5 py-1 text-[11px] font-medium text-emerald-700">
                                {deploymentLabel}
                              </span>
                            )}
                            {continuityLabel && (
                              <span className="rounded-full bg-violet-100 px-2.5 py-1 text-[11px] font-medium text-violet-700">
                                {continuityLabel}
                              </span>
                            )}
                          </div>
                        )}
                      </div>
                    </div>

                    <div className="flex items-center gap-2 text-sm font-medium text-sky-700">
                      Enter
                      <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-0.5" />
                    </div>
                  </div>

                  <div className="mt-5 flex flex-wrap gap-2">
                    {capabilities.length > 0 ? (
                      capabilities.slice(0, 4).map((capability) => (
                        <span
                          key={capability}
                          className="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-medium text-slate-600"
                        >
                          {capability.replace(/_/g, " ")}
                        </span>
                      ))
                    ) : (
                      <span className="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-medium text-slate-500">
                        Core services
                      </span>
                    )}
                  </div>
                </button>
              );
            })}
          </div>
        )}

        {(hasMore || modeActions.length > 0) && (
          <div className="mt-6 flex flex-col gap-4 border-t border-slate-200/80 pt-5">
            {modeActions.length > 0 && (
              <div className="grid grid-cols-1 gap-3 lg:grid-cols-3">
                {modeActions.map((action) => {
                  const content = (
                    <>
                      <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-950 text-white">
                        <action.icon className="h-4.5 w-4.5" />
                      </div>
                      <div className="mt-4">
                        <p className="text-sm font-semibold text-slate-950">{action.label}</p>
                        <p className="mt-1 text-sm leading-6 text-slate-500">
                          {action.description}
                        </p>
                      </div>
                    </>
                  );

                  if (action.href) {
                    return (
                      <Link
                        key={action.label}
                        href={action.href}
                        className="rounded-[22px] border border-slate-200 bg-white/80 p-4 transition hover:border-slate-300 hover:bg-white"
                      >
                        {content}
                      </Link>
                    );
                  }

                  return (
                    <button
                      key={action.label}
                      type="button"
                      onClick={action.onClick}
                      className="rounded-[22px] border border-slate-200 bg-white/80 p-4 text-left transition hover:border-slate-300 hover:bg-white"
                    >
                      {content}
                    </button>
                  );
                })}
              </div>
            )}

            {hasMore && (
              <div className="flex justify-end">
                <Link
                  href={browseHref}
                  className="inline-flex items-center gap-2 rounded-full border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:border-sky-300 hover:text-sky-700"
                >
                  Browse all {facilities.length} facilities
                  <ArrowRight className="h-4 w-4" />
                </Link>
              </div>
            )}
          </div>
        )}
      </div>
    </section>
  );
}
