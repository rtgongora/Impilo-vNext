/**
 * OpsReportsHubScreen — Tier-3 wave 4 parity for web operations + reports professional landings.
 */
import React, { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { ProfessionalHubBody } from "../../components/ProfessionalHubBody";
import { fetchOpsReportsHub, type OpsReportsSection } from "../../services/opsReportsService";

const FALLBACK_SECTIONS: OpsReportsSection[] = [
  { id: "operations", title: "Operations", web_path: "/operations", hint: "Day-to-day facility and platform operations." },
  { id: "operations_vito", title: "Identity Operations", web_path: "/operations/vito", hint: "VITO / identity exchange operations." },
  { id: "operations_butano", title: "SHR Operations", web_path: "/operations/butano", hint: "Shared health record connectivity and operations." },
  { id: "operations_assets", title: "Asset Management", web_path: "/operations/assets", hint: "Track and maintain physical assets." },
  { id: "operations_equipment", title: "Equipment Management", web_path: "/operations/equipment", hint: "Devices, maintenance, and calibration." },
  { id: "reports", title: "Reports", web_path: "/reports", hint: "Reporting home and saved views." },
  { id: "reports_facility", title: "Facility Reports", web_path: "/reports/facility", hint: "Utilization, throughput, and site KPIs." },
  { id: "reports_clinical", title: "Clinical Reports", web_path: "/reports/clinical", hint: "Clinical quality and outcomes summaries." },
  { id: "reports_operational", title: "Operational Reports", web_path: "/reports/operational", hint: "Ops dashboards and SLAs." },
  { id: "reports_custom", title: "Custom Reports", web_path: "/reports/custom", hint: "User-defined report definitions." },
  { id: "reports_detail", title: "Report Details", web_path: "/reports/[id]", hint: "Drill into a single report run or export." },
];

export function OpsReportsHubScreen() {
  const { data, isPending, isError, refetch, isRefetching } = useQuery({
    queryKey: ["ops-reports-hub"],
    queryFn: fetchOpsReportsHub,
    retry: 1,
  });

  const sections = useMemo(() => {
    const remote = data?.sections?.filter((s) => s?.id && s?.title);
    return remote && remote.length > 0 ? remote : FALLBACK_SECTIONS;
  }, [data]);

  return (
    <ProfessionalHubBody
      rootTestID="ops-reports-hub-screen"
      heading="Operations & Reports"
      description="Canonical web landings for platform operations and reporting. Full workflows open in the workspace when available."
      sections={sections}
      isPending={isPending}
      isError={isError}
      refreshedAt={data?.refreshed_at}
      isRefetching={isRefetching}
      onRefresh={() => refetch()}
      getSectionTestId={(id) => `ops-reports-section-${id}`}
    />
  );
}
