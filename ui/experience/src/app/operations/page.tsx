"use client";

/**
 * Operations Hub — absorbs ops-console sidecar
 * VITO ops, BUTANO ops, asset management, equipment management.
 * Route: /operations | Guard: role ADMIN
 */

import Link from "next/link";
import { Settings2, Users, Database, Package, Wrench } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const SECTIONS = [
  { href: "/operations/vito", label: "Identity Operations", description: "VITO client registry: dedup, issuance queue, match review, card management", Icon: Users },
  { href: "/operations/butano", label: "SHR Operations", description: "BUTANO reconciliation, FHIR stats, trigger management", Icon: Database },
  { href: "/operations/assets", label: "Asset Management", description: "Track, assign, and lifecycle-manage organizational assets", Icon: Package },
  { href: "/operations/equipment", label: "Equipment Management", description: "Clinical equipment: calibration, maintenance, device linkage", Icon: Wrench },
];

export default function OperationsPage() {
  return (
    <AppLayout>
      <PageShell title="Operations" subtitle="System operations, asset governance, and service management" icon={<Settings2 className="h-6 w-6" />}>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {SECTIONS.map(({ href, label, description, Icon }) => (
            <Link key={href} href={href} className="rounded-lg border border-gray-200 bg-white p-5 hover:border-impilo-400 hover:shadow-sm transition-all">
              <div className="flex items-center gap-3 mb-2">
                <div className="rounded-lg bg-slate-100 p-2"><Icon className="h-5 w-5 text-slate-700" /></div>
                <h3 className="font-semibold text-gray-900">{label}</h3>
              </div>
              <p className="text-sm text-gray-600">{description}</p>
            </Link>
          ))}
        </div>
      </PageShell>
    </AppLayout>
  );
}
