"use client";

import { useState, useCallback, useMemo } from "react";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/** Encounter menu section identifiers matching EncounterMenu sections. */
export type EncounterSectionId =
  | "summary"
  | "timeline"
  | "vitals"
  | "conditions"
  | "history"
  | "allergies"
  | "immunizations"
  | "medications"
  | "orders"
  | "results"
  | "imaging"
  | "care-plans"
  | "care-team"
  | "goals"
  | "family-history"
  | "social-history"
  | "functional-status"
  | "advance-directives"
  | "procedures"
  | "growth-chart"
  | "assessments"
  | "consults"
  | "teleconsults"
  | "documents"
  | "notes"
  | "discharge";

export type SectionStatus =
  | "not-started"
  | "in-progress"
  | "completed"
  | "skipped"
  | "attention";

export type SectionPriority = "high" | "medium" | "low" | "skip";

export interface SectionRecommendation {
  priority: SectionPriority;
  reason: string;
}

export interface UseEncounterWizardReturn {
  /** Status of each section */
  sectionStatuses: Record<EncounterSectionId, SectionStatus>;
  /** Mark a section as visited / in-progress */
  markVisited: (section: EncounterSectionId) => void;
  /** Mark a section as completed */
  markCompleted: (section: EncounterSectionId) => void;
  /** Get recommended next section */
  recommendedNext: EncounterSectionId | null;
  /** Context-aware recommendations for each section */
  recommendations: Record<EncounterSectionId, SectionRecommendation>;
  /** Overall progress percentage (0-100) */
  progress: number;
  /** Navigate to next section in flow */
  nextSection: (current: EncounterSectionId) => EncounterSectionId | null;
  /** Navigate to previous section in flow */
  prevSection: (current: EncounterSectionId) => EncounterSectionId | null;
  /** Sections that need attention based on patient context */
  attentionSections: EncounterSectionId[];
}

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** Ordered list of sections that form the primary encounter workflow. */
const SECTION_ORDER: EncounterSectionId[] = [
  "summary",
  "timeline",
  "vitals",
  "conditions",
  "history",
  "allergies",
  "immunizations",
  "medications",
  "orders",
  "results",
  "imaging",
  "care-plans",
  "care-team",
  "goals",
  "family-history",
  "social-history",
  "functional-status",
  "advance-directives",
  "procedures",
  "growth-chart",
  "assessments",
  "consults",
  "teleconsults",
  "documents",
  "notes",
  "discharge",
];

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Simulates context-aware intelligence based on patient data.
 * In production this would analyse patient data, pending orders, CDS alerts, etc.
 */
function getRecommendations(): Record<EncounterSectionId, SectionRecommendation> {
  return {
    summary: { priority: "high", reason: "Review patient summary first" },
    timeline: { priority: "low", reason: "Timeline available for reference" },
    vitals: { priority: "high", reason: "Vitals recording due" },
    conditions: { priority: "high", reason: "2 active problems need review" },
    history: { priority: "medium", reason: "Confirm past medical history" },
    allergies: { priority: "high", reason: "Allergy verification required" },
    immunizations: { priority: "low", reason: "No immunizations due" },
    medications: { priority: "medium", reason: "Medication reconciliation pending" },
    orders: { priority: "high", reason: "3 pending lab results" },
    results: { priority: "medium", reason: "Recent results to review" },
    imaging: { priority: "low", reason: "No imaging ordered" },
    "care-plans": { priority: "medium", reason: "Care plan needs update" },
    "care-team": { priority: "low", reason: "Team assignments current" },
    goals: { priority: "low", reason: "Goals on track" },
    "family-history": { priority: "low", reason: "Family history documented" },
    "social-history": { priority: "low", reason: "Social history documented" },
    "functional-status": { priority: "skip", reason: "Not applicable for visit type" },
    "advance-directives": { priority: "skip", reason: "Not applicable" },
    procedures: { priority: "low", reason: "No procedures scheduled" },
    "growth-chart": { priority: "skip", reason: "Adult patient" },
    assessments: { priority: "medium", reason: "Clinical assessment pending" },
    consults: { priority: "low", reason: "No pending consults" },
    teleconsults: { priority: "skip", reason: "No teleconsult scheduled" },
    documents: { priority: "low", reason: "Scan documents if available" },
    notes: { priority: "medium", reason: "Clinical note due" },
    discharge: { priority: "low", reason: "Complete when ready to discharge" },
  };
}

// ---------------------------------------------------------------------------
// Hook
// ---------------------------------------------------------------------------

export function useEncounterWizard(): UseEncounterWizardReturn {
  const [sectionStatuses, setSectionStatuses] = useState<
    Record<EncounterSectionId, SectionStatus>
  >(() => {
    const initial: Record<string, SectionStatus> = {};
    SECTION_ORDER.forEach((id) => {
      initial[id] = "not-started";
    });
    return initial as Record<EncounterSectionId, SectionStatus>;
  });

  const recommendations = useMemo(() => getRecommendations(), []);

  const markVisited = useCallback((section: EncounterSectionId) => {
    setSectionStatuses((prev) => {
      if (prev[section] === "completed") return prev;
      return { ...prev, [section]: "in-progress" };
    });
  }, []);

  const markCompleted = useCallback((section: EncounterSectionId) => {
    setSectionStatuses((prev) => ({ ...prev, [section]: "completed" }));
  }, []);

  const progress = useMemo(() => {
    const total = SECTION_ORDER.length;
    const completed = SECTION_ORDER.filter(
      (id) =>
        sectionStatuses[id] === "completed" || sectionStatuses[id] === "skipped"
    ).length;
    return Math.round((completed / total) * 100);
  }, [sectionStatuses]);

  const recommendedNext = useMemo((): EncounterSectionId | null => {
    // Find the first high-priority section that is not completed
    const highPriority = SECTION_ORDER.filter(
      (id) =>
        recommendations[id].priority === "high" &&
        sectionStatuses[id] !== "completed"
    );
    if (highPriority.length > 0) return highPriority[0];

    // Then medium priority
    const medPriority = SECTION_ORDER.filter(
      (id) =>
        recommendations[id].priority === "medium" &&
        sectionStatuses[id] !== "completed"
    );
    if (medPriority.length > 0) return medPriority[0];

    // Then any not-started
    const notStarted = SECTION_ORDER.filter(
      (id) => sectionStatuses[id] === "not-started"
    );
    return notStarted.length > 0 ? notStarted[0] : null;
  }, [sectionStatuses, recommendations]);

  const attentionSections = useMemo(() => {
    return SECTION_ORDER.filter(
      (id) =>
        recommendations[id].priority === "high" &&
        sectionStatuses[id] !== "completed"
    );
  }, [sectionStatuses, recommendations]);

  const nextSection = useCallback(
    (current: EncounterSectionId): EncounterSectionId | null => {
      const idx = SECTION_ORDER.indexOf(current);
      return idx < SECTION_ORDER.length - 1 ? SECTION_ORDER[idx + 1] : null;
    },
    []
  );

  const prevSection = useCallback(
    (current: EncounterSectionId): EncounterSectionId | null => {
      const idx = SECTION_ORDER.indexOf(current);
      return idx > 0 ? SECTION_ORDER[idx - 1] : null;
    },
    []
  );

  return {
    sectionStatuses,
    markVisited,
    markCompleted,
    recommendedNext,
    recommendations,
    progress,
    nextSection,
    prevSection,
    attentionSections,
  };
}
