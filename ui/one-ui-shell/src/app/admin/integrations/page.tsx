"use client";

/**
 * Diagnostics Integration Status — honest configured/not-configured adapter states (spec §27 #11).
 * Route: /admin/integrations | Zone: admin | Guard: role ADMIN
 *
 * Real data via the experience-bff diagnostics proxy → OROS integration-status.
 */

import { CheckCircle2, Loader2, XCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useIntegrationStatus } from "@/hooks/queries/useDiagnosticsOrders";

export default function IntegrationsStatusPage() {
  const statusQ = useIntegrationStatus();
  const adapters = statusQ.data?.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Integration Status" subtitle="Configured / not-configured external adapters for the diagnostics journey">
        {statusQ.isLoading ? (
          <div className="flex items-center justify-center gap-2 p-12 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading integration status…
          </div>
        ) : statusQ.isError ? (
          <div className="p-8 text-center text-sm text-danger">Unable to load integration status from OROS.</div>
        ) : (
          <div className="overflow-hidden rounded-lg border border-gray-100">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-left text-xs uppercase text-muted-foreground">
                <tr>
                  <th className="px-4 py-2">Adapter</th>
                  <th className="px-4 py-2">Category</th>
                  <th className="px-4 py-2">Direction</th>
                  <th className="px-4 py-2">Status</th>
                  <th className="px-4 py-2">Detail</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {adapters.map((a) => (
                  <tr key={a.adapter} data-testid="integration-row">
                    <td className="px-4 py-3 font-medium">{a.adapter}</td>
                    <td className="px-4 py-3">{a.category}</td>
                    <td className="px-4 py-3">{a.direction}</td>
                    <td className="px-4 py-3">
                      {a.configured ? (
                        <span className="inline-flex items-center gap-1 text-success-foreground">
                          <CheckCircle2 className="h-4 w-4" /> Configured
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 text-muted-foreground">
                          <XCircle className="h-4 w-4" /> Not configured
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">{a.detail}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
