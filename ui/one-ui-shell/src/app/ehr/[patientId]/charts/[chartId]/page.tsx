"use client";

import { useParams } from "next/navigation";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { WardChartGrid } from "@/components/ward/charts/WardChartGrid";
import { getChartById } from "@/data/wardChartTypes";

export default function WardChartDetailPage() {
  const { patientId, chartId } = useParams<{ patientId: string; chartId: string }>();
  const chart = getChartById(chartId);

  if (!chart) {
    return (
      <EHRLayout>
        <PageShell title="Chart not found" subtitle="Unknown chart type." />
      </EHRLayout>
    );
  }

  return (
    <EHRLayout>
      <PageShell title={chart.name} subtitle={chart.description}>
        <WardChartGrid chart={chart} patientId={patientId} persistToApi />
      </PageShell>
    </EHRLayout>
  );
}
