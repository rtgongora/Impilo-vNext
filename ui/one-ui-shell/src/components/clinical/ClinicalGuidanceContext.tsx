"use client";

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";

export type ClinicalGuidanceDockTab = "pathways" | "prescribing";

type DockState = { open: boolean; tab: ClinicalGuidanceDockTab };

export type ClinicalGuidanceContextValue = {
  dock: DockState;
  pathwaysHighlighted: boolean;
  openDock: (tab: ClinicalGuidanceDockTab) => void;
  closeDock: () => void;
  togglePathwaysDock: () => void;
};

const ClinicalGuidanceContext = createContext<ClinicalGuidanceContextValue | null>(null);

export function ClinicalGuidanceProvider({ children }: { children: ReactNode }) {
  const [dock, setDock] = useState<DockState>({ open: false, tab: "pathways" });

  const openDock = useCallback((tab: ClinicalGuidanceDockTab) => {
    setDock({ open: true, tab });
  }, []);

  const closeDock = useCallback(() => {
    setDock((d) => ({ ...d, open: false }));
  }, []);

  const togglePathwaysDock = useCallback(() => {
    setDock((d) => {
      if (d.open && d.tab === "pathways") {
        return { open: false, tab: "pathways" };
      }
      return { open: true, tab: "pathways" };
    });
  }, []);

  const pathwaysHighlighted = dock.open && dock.tab === "pathways";

  const value = useMemo(
    () => ({
      dock,
      pathwaysHighlighted,
      openDock,
      closeDock,
      togglePathwaysDock,
    }),
    [dock, pathwaysHighlighted, openDock, closeDock, togglePathwaysDock],
  );

  return (
    <ClinicalGuidanceContext.Provider value={value}>{children}</ClinicalGuidanceContext.Provider>
  );
}

export function useClinicalGuidance(): ClinicalGuidanceContextValue {
  const ctx = useContext(ClinicalGuidanceContext);
  if (!ctx) {
    throw new Error("useClinicalGuidance must be used within ClinicalGuidanceProvider");
  }
  return ctx;
}

/** Safe for components that may render outside EHR (e.g. storybook). */
export function useClinicalGuidanceOptional(): ClinicalGuidanceContextValue | null {
  return useContext(ClinicalGuidanceContext);
}
