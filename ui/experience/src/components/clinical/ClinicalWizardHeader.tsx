"use client";

import { useCallback, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ChevronLeft, ChevronRight, Home, Loader2, Save } from "lucide-react";
import {
  DEFAULT_CLINICAL_WIZARD_STEPS,
  type EncounterNavContext,
  buildWizardStepHref,
  encounterSectionForWizardStepId,
  isSectionVisible,
  wizardStepIndexForPathname,
} from "@/lib/clinical/encounter-workspace-nav";
import { useClinicalWorkflowConfig } from "./ClinicalWorkflowContext";
import { useRoleGroup } from "@/hooks/useRoleGroup";

export interface ClinicalWizardHeaderProps {
  patientId: string;
  encounterId: string | null;
  pathname: string;
  validateBeforeNext?: () => boolean | Promise<boolean>;
  onSaveDraft?: () => void | Promise<void>;
}

export function ClinicalWizardHeader({
  patientId,
  encounterId,
  pathname,
  validateBeforeNext,
  onSaveDraft,
}: ClinicalWizardHeaderProps) {
  const router = useRouter();
  const role = useRoleGroup();
  const roleFlags = useMemo(
    () => ({
      isClinical: role.isClinical,
      isPrescriber: role.isPrescriber,
      isDispenser: role.isDispenser,
      isAdmin: role.isAdmin,
    }),
    [role.isAdmin, role.isClinical, role.isDispenser, role.isPrescriber],
  );
  const workflow = useClinicalWorkflowConfig();
  const steps = workflow?.steps ?? DEFAULT_CLINICAL_WIZARD_STEPS;
  const ctx: EncounterNavContext = useMemo(() => ({ patientId, encounterId }), [patientId, encounterId]);
  const currentIdx = wizardStepIndexForPathname(pathname, patientId, steps);
  const [pending, setPending] = useState<"next" | "back" | "save" | null>(null);

  const stepAllowedAt = useCallback(
    (idx: number) => {
      const step = steps[idx];
      if (!step) return false;
      return isSectionVisible(encounterSectionForWizardStepId(step.id), roleFlags);
    },
    [roleFlags, steps],
  );

  const canGoBack = useMemo(() => {
    for (let i = currentIdx - 1; i >= 0; i -= 1) {
      if (stepAllowedAt(i)) return true;
    }
    return false;
  }, [currentIdx, stepAllowedAt]);

  const canGoNext = useMemo(() => {
    for (let i = currentIdx + 1; i < steps.length; i += 1) {
      if (stepAllowedAt(i)) return true;
    }
    return false;
  }, [currentIdx, stepAllowedAt, steps.length]);

  const go = useCallback(
    async (direction: "next" | "back") => {
      let targetIdx = direction === "next" ? currentIdx + 1 : currentIdx - 1;
      while (targetIdx >= 0 && targetIdx < steps.length && !stepAllowedAt(targetIdx)) {
        targetIdx += direction === "next" ? 1 : -1;
      }
      const step = steps[targetIdx];
      if (!step) return;
      if (direction === "next" && validateBeforeNext) {
        const ok = await validateBeforeNext();
        if (!ok) return;
      }
      setPending(direction);
      try {
        router.push(buildWizardStepHref(step.id, ctx));
      } finally {
        setPending(null);
      }
    },
    [ctx, currentIdx, router, stepAllowedAt, steps, validateBeforeNext],
  );

  const save = useCallback(async () => {
    setPending("save");
    try {
      if (onSaveDraft) await onSaveDraft();
      else window.dispatchEvent(new CustomEvent("impilo:ehr-save-draft", { detail: { patientId, encounterId } }));
    } finally {
      setPending(null);
    }
  }, [encounterId, onSaveDraft, patientId]);

  return (
    <div className="border-b bg-gradient-to-r from-slate-50 via-white to-impilo-50/40 px-4 py-3 shrink-0">
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex items-center gap-2 min-w-0">
          <Link
            href={`/ehr/${patientId}`}
            className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-gray-200 bg-white text-gray-600 hover:bg-gray-50"
            title="Patient overview"
          >
            <Home className="h-4 w-4" />
          </Link>
          <button
            type="button"
            disabled={!canGoBack || pending !== null}
            onClick={() => void go("back")}
            className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-gray-200 bg-white text-gray-700 hover:bg-gray-50 disabled:opacity-40 disabled:pointer-events-none"
            title="Previous step"
          >
            {pending === "back" ? <Loader2 className="h-4 w-4 animate-spin" /> : <ChevronLeft className="h-5 w-5" />}
          </button>
          <button
            type="button"
            disabled={!canGoNext || pending !== null}
            onClick={() => void go("next")}
            className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-impilo-200 bg-impilo-600 text-white hover:bg-impilo-700 disabled:opacity-40 disabled:pointer-events-none"
            title="Next step"
          >
            {pending === "next" ? <Loader2 className="h-4 w-4 animate-spin" /> : <ChevronRight className="h-5 w-5" />}
          </button>
          <button
            type="button"
            disabled={pending !== null}
            onClick={() => void save()}
            className="hidden sm:inline-flex h-9 items-center gap-1.5 rounded-lg border border-gray-200 bg-white px-3 text-xs font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-40"
            title="Save draft or progress"
          >
            {pending === "save" ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Save className="h-3.5 w-3.5" />}
            Save
          </button>
        </div>

        <div className="flex-1 min-w-0">
          <p className="text-[10px] font-semibold uppercase tracking-wider text-gray-400">Encounter workflow</p>
          <div className="mt-1 flex gap-1 overflow-x-auto pb-1">
            {steps.map((step, stepIndex) => {
              const done = stepIndex < currentIdx;
              const active = stepIndex === currentIdx;
              const href = buildWizardStepHref(step.id, ctx);
              const section = encounterSectionForWizardStepId(step.id);
              const stepAllowed = isSectionVisible(section, roleFlags);
              const pillClass = [
                "whitespace-nowrap rounded-full px-2.5 py-1 text-[11px] font-medium border transition-colors",
                active ? "border-impilo-400 bg-impilo-100 text-impilo-800"
                : done ? "border-emerald-200 bg-emerald-50 text-emerald-800"
                : "border-gray-200 bg-white text-gray-600 hover:border-impilo-200",
                !stepAllowed ? "opacity-40 cursor-not-allowed pointer-events-none" : "",
              ].join(" ");
              if (!stepAllowed) {
                return (
                  <span
                    key={step.id}
                    aria-disabled
                    className={pillClass}
                    title="Not available for your role in this workspace"
                  >
                    {stepIndex + 1}. {step.label}
                  </span>
                );
              }
              return (
                <Link
                  key={step.id}
                  href={href}
                  aria-current={active ? "step" : undefined}
                  className={pillClass}
                  title={step.label}
                >
                  {stepIndex + 1}. {step.label}
                </Link>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
