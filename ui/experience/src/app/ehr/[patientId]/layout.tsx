import type { ReactNode } from "react";
import { EHRLayout } from "@/components/EHRLayout";

/**
 * Single Lovable-parity shell for all patient-chart and encounter routes.
 * Keeps Encounter Menu, wizard header, support strip, and floating assist
 * mounted consistently without per-page duplication.
 */
export default function PatientChartShellLayout({ children }: { children: ReactNode }) {
  return <EHRLayout>{children}</EHRLayout>;
}
