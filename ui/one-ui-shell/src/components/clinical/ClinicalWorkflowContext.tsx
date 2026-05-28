"use client";

import { createContext, useContext, type ReactNode } from "react";
import type { WizardStepModel } from "@/lib/clinical/encounter-workspace-nav";

export interface ClinicalWorkflowConfig {
  steps: WizardStepModel[];
}

const ClinicalWorkflowContext = createContext<ClinicalWorkflowConfig | null>(null);

export function ClinicalWorkflowProvider({
  value,
  children,
}: {
  value: ClinicalWorkflowConfig | null;
  children: ReactNode;
}) {
  return <ClinicalWorkflowContext.Provider value={value}>{children}</ClinicalWorkflowContext.Provider>;
}

export function useClinicalWorkflowConfig(): ClinicalWorkflowConfig | null {
  return useContext(ClinicalWorkflowContext);
}
