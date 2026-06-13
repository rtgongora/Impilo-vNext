"use client";

/**
 * Federation — Trusted pods and federation configuration table.
 * Route: /admin/federation | pageTitle: "Federation"
 */

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, Globe, ShieldAlert } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { RegistryPlaneContextBar } from "@/components/experience/RegistryPlaneContextBar";
import { PageShell } from "@/components/PageShell";

export default function FederationPage() {
  const searchParams = useSearchParams();
  const fromRegistryAdmin = searchParams.get("from") === "registry-admin";

  return (
    <AppLayout>
      <PageShell
        title="Federation"
        subtitle="Configure identity federation and trust relationships"
      >
        <RegistryPlaneContextBar />
        <div className="mb-4">
          <Link
            href={fromRegistryAdmin ? "/registry/trust" : "/admin"}
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            {fromRegistryAdmin ? "Back to trust hub" : "Back to administration"}
          </Link>
        </div>

        <div className="rounded-2xl border border-warning/35 bg-warning-soft p-6">
          <div className="flex items-start gap-3">
            <div className="rounded-xl bg-card p-3 text-warning-foreground">
              <Globe className="h-6 w-6" />
            </div>
            <div>
              <h2 className="font-semibold text-warning-foreground">Typed federation BFF unavailable</h2>
              <p className="mt-2 text-sm text-warning-foreground">
                This route no longer calls the legacy <code>/internal/v1/admin/federation</code> alias because no typed
                Experience BFF controller exists for that path. Federation and pod trust should stay unavailable in
                this UI until a canonical contract is added.
              </p>
              <div className="mt-4 rounded-lg border border-warning/35 bg-card p-3 text-xs text-warning-foreground">
                <ShieldAlert className="mr-1 inline h-4 w-4" />
                No fake federation pod table is rendered here.
              </div>
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
