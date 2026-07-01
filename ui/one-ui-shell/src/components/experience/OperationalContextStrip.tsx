"use client";

/**
 * Global operational mode selector — single login, explicit top-level context.
 * Does not replace facility/workspace/shift sequencing; it labels the user's current hat.
 */

import type { ComponentType } from "react";
import { useRouter } from "next/navigation";
import { Building2, Heart, Shield, ShieldCheck, Stethoscope, UserCog } from "lucide-react";
import {
  OPERATIONAL_MODE_DEF,
  type OperationalMode,
} from "@/lib/operational-context";
import { useExperienceEntry } from "@/providers/ExperienceEntryProvider";

const MODE_ICONS: Record<OperationalMode, ComponentType<{ className?: string }>> = {
  my_life: Heart,
  my_professional: Stethoscope,
  facility_work: Building2,
  registry_admin: ShieldCheck,
  organization_admin: UserCog,
};

export function OperationalContextStrip({ embedded = false, inline = false }: { embedded?: boolean; inline?: boolean }) {
  const router = useRouter();
  const {
    availableOperationalModes,
    operationalMode,
    setOperationalMode,
    facilityWorkSubcontext,
  } = useExperienceEntry();

  if (availableOperationalModes.length === 0) return null;

  function activateMode(mode: OperationalMode) {
    setOperationalMode(mode);
    if (mode === "registry_admin") {
      router.push("/registry-admin");
    } else if (mode === "organization_admin") {
      router.push("/organization-admin");
    }
  }

  if (inline) {
    return (
      <div className="hidden min-w-0 flex-1 items-center gap-1.5 overflow-x-auto xl:flex">
        <span className="mr-1 shrink-0 text-[10px] font-semibold uppercase tracking-[0.18em] text-slate-500">
          Context
        </span>
        {availableOperationalModes.map((mode) => {
          const def = OPERATIONAL_MODE_DEF[mode];
          const Icon = MODE_ICONS[mode];
          const active = operationalMode === mode;
          const trust = def.trustTier === "registry";
          return (
            <button
              key={mode}
              type="button"
              onClick={() => activateMode(mode)}
              title={def.description}
              className={`inline-flex h-7 shrink-0 items-center gap-1.5 rounded-full border px-2.5 text-xs font-medium transition-colors ${
                active
                  ? trust
                    ? "border-amber-400 bg-amber-100 text-amber-950"
                    : def.trustTier === "elevated"
                      ? "border-violet-400 bg-violet-100 text-violet-950"
                      : "border-sky-400 bg-sky-100 text-sky-950"
                  : "border-slate-200 bg-slate-50 text-slate-600 hover:bg-white"
              }`}
            >
              <Icon className="h-3.5 w-3.5 shrink-0 opacity-80" />
              <span>{def.shortLabel}</span>
              {trust && <Shield className="h-3 w-3 text-amber-700" aria-hidden />}
            </button>
          );
        })}
      </div>
    );
  }

  return (
    <div className={embedded ? "bg-slate-50/90 px-4 py-1.5 sm:px-6" : "border-b border-slate-200 bg-slate-50/90 px-4 py-2"}>
      <div className="mx-auto flex max-w-[1600px] flex-col gap-2 md:flex-row md:items-center md:justify-between">
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="mr-1 text-[10px] font-semibold uppercase tracking-[0.2em] text-slate-500">
            Context
          </span>
          {availableOperationalModes.map((mode) => {
            const def = OPERATIONAL_MODE_DEF[mode];
            const Icon = MODE_ICONS[mode];
            const active = operationalMode === mode;
            const trust = def.trustTier === "registry";
            return (
              <button
                key={mode}
                type="button"
                onClick={() => activateMode(mode)}
                title={def.description}
                className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium transition-colors ${
                  active
                    ? trust
                      ? "border-amber-400 bg-amber-100 text-amber-950"
                      : def.trustTier === "elevated"
                        ? "border-violet-400 bg-violet-100 text-violet-950"
                        : "border-sky-400 bg-sky-100 text-sky-950"
                    : "border-transparent bg-white/80 text-slate-600 hover:border-slate-200 hover:bg-white"
                }`}
              >
                <Icon className="h-3.5 w-3.5 shrink-0 opacity-80" />
                <span>{def.shortLabel}</span>
                {trust && (
                  <Shield className="h-3 w-3 text-amber-700" aria-hidden />
                )}
              </button>
            );
          })}
        </div>
        {operationalMode === "facility_work" && (
          <p className="text-[11px] leading-snug text-slate-500 md:max-w-md md:text-right">
            Facility sequencing unchanged: pick facility, workspace, then shift for guarded clinical routes.
            {facilityWorkSubcontext ? (
              <span className="ml-1 font-medium text-slate-700">
                Subcontext: {facilityWorkSubcontext.replace(/_/g, " ")}
              </span>
            ) : (
              <span className="ml-1">
                Work subcontext (triage, ward, lab, …) will attach here in a later batch.
              </span>
            )}
          </p>
        )}
        {operationalMode === "registry_admin" && (
          <p className="text-[11px] leading-snug text-amber-900/80 md:max-w-md md:text-right">
            Registry Admin is a high-trust plane — separate from facility shift context. Downstream registry
            hubs will respect elevated review rules.
          </p>
        )}
        {operationalMode === "organization_admin" && (
          <p className="text-[11px] leading-snug text-violet-900/80 md:max-w-md md:text-right">
            Organization Admin covers facility and enterprise operations — not sovereign registry governance.
            Use Registry Admin for national registry and trust-fabric changes.
          </p>
        )}
      </div>
    </div>
  );
}
