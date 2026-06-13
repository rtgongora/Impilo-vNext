"use client";

/**
 * EncounterStepNav -- Bottom navigation bar for stepping through encounter
 * sections with Mark Complete, Previous / Next, and smart recommended-next.
 */

import { useParams, usePathname, useRouter } from "next/navigation";
import {
  ChevronLeft,
  ChevronRight,
  CheckCircle2,
  Sparkles,
} from "lucide-react";
import {
  useEncounterWizard,
  type EncounterSectionId,
} from "@/hooks/useEncounterWizard";

/** Label lookup for human-readable section names. */
const SECTION_LABELS: Record<EncounterSectionId, string> = {
  summary: "Summary",
  timeline: "Timeline",
  vitals: "Vitals",
  conditions: "Conditions",
  history: "History",
  allergies: "Allergies",
  immunizations: "Immunizations",
  medications: "Medications",
  orders: "Orders",
  results: "Results",
  imaging: "Imaging",
  "care-plans": "Care Plans",
  "care-team": "Care Team",
  goals: "Goals",
  "family-history": "Family History",
  "social-history": "Social History",
  "functional-status": "Functional Status",
  "advance-directives": "Advance Directives",
  procedures: "Procedures",
  "growth-chart": "Growth Chart",
  assessments: "Assessments",
  consults: "Consults",
  teleconsults: "Teleconsults",
  documents: "Documents",
  notes: "Notes",
  discharge: "Discharge",
};

export function EncounterStepNav() {
  const router = useRouter();
  const params = useParams();
  const pathname = usePathname();
  const patientId = params?.patientId as string | undefined;
  const wizard = useEncounterWizard();

  if (!patientId) return null;

  // Derive current section from the URL
  const prefix = `/ehr/${patientId}/`;
  const currentSegment = pathname.startsWith(prefix)
    ? pathname.slice(prefix.length).split("/")[0]
    : null;
  const activeSection = (currentSegment ?? "summary") as EncounterSectionId;

  const prev = wizard.prevSection(activeSection);
  const next = wizard.nextSection(activeSection);

  const navigateTo = (section: EncounterSectionId) => {
    router.push(`/ehr/${patientId}/${section}`);
  };

  return (
    <div className="flex items-center justify-between border-t border-border bg-background/60 px-5 py-3">
      {/* Previous */}
      <div>
        {prev && (
          <button
            onClick={() => navigateTo(prev)}
            className="flex items-center gap-2 px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground rounded-md hover:bg-neutral-100 transition-colors"
          >
            <ChevronLeft className="w-4 h-4" />
            {SECTION_LABELS[prev]}
          </button>
        )}
      </div>

      {/* Mark Complete */}
      <button
        onClick={() => wizard.markCompleted(activeSection)}
        disabled={wizard.sectionStatuses[activeSection] === "completed"}
        className={`flex items-center gap-2 px-4 py-1.5 text-sm rounded-md border transition-colors ${
          wizard.sectionStatuses[activeSection] === "completed"
            ? "border-green-300 bg-green-50 text-green-700 cursor-default"
            : "border-border bg-card text-foreground hover:bg-background"
        }`}
      >
        <CheckCircle2 className="w-4 h-4" />
        {wizard.sectionStatuses[activeSection] === "completed"
          ? "Completed"
          : "Mark Complete"}
      </button>

      {/* Next / Recommended */}
      <div className="flex items-center gap-2">
        {wizard.recommendedNext &&
          wizard.recommendedNext !== activeSection &&
          wizard.recommendedNext !== next && (
            <button
              onClick={() => navigateTo(wizard.recommendedNext!)}
              className="flex items-center gap-2 px-3 py-1.5 text-sm text-primary hover:bg-primary-soft rounded-md transition-colors"
            >
              <Sparkles className="w-4 h-4" />
              {SECTION_LABELS[wizard.recommendedNext]}
            </button>
          )}
        {next && (
          <button
            onClick={() => navigateTo(next)}
            className="flex items-center gap-2 px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground rounded-md hover:bg-neutral-100 transition-colors"
          >
            {SECTION_LABELS[next]}
            <ChevronRight className="w-4 h-4" />
          </button>
        )}
      </div>
    </div>
  );
}
