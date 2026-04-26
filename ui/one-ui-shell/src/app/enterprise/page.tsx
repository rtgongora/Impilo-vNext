"use client";

/**
 * Enterprise Resource Plane — role-aware dashboard (facility + BFF-backed metrics).
 * Route: /enterprise
 */

import { EnterpriseResourceDashboard } from "@/components/enterprise/EnterpriseResourceDashboard";
import { EnterpriseWorkspaceShell } from "@/components/enterprise/EnterpriseWorkspaceShell";

export default function EnterpriseResourcesPage() {
  return (
    <EnterpriseWorkspaceShell
      title="Enterprise resources"
      subtitle="Commodities, inventory risk, procurement signals, and finance entry points — without synthetic operational KPIs."
    >
      <EnterpriseResourceDashboard />
    </EnterpriseWorkspaceShell>
  );
}
