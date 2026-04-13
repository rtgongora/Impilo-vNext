"use client";

/**
 * Lab Hub — absorbs oros-web sidecar
 * Worklists, orders, results, catalog, reconciliation.
 * Route: /lab | Guard: shift (clinical staff)
 */

import Link from "next/link";
import { FlaskConical, ListChecks, FileSearch, BookOpen, RefreshCcw } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const SECTIONS = [
  { href: "/lab/worklist", label: "Lab Worklist", description: "Pending and in-progress lab orders for this facility", Icon: ListChecks },
  { href: "/lab/results", label: "Results Review", description: "Review and authorize lab results", Icon: FileSearch },
  { href: "/lab/catalog", label: "Test Catalog", description: "Available lab tests and capabilities", Icon: BookOpen },
  { href: "/lab/reconciliation", label: "Reconciliation", description: "Reconcile lab orders with external LIS", Icon: RefreshCcw },
];

export default function LabPage() {
  return (
    <AppLayout>
      <PageShell title="Laboratory" subtitle="Lab orders, results, worklists, and test catalog" icon={<FlaskConical className="h-6 w-6" />}>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {SECTIONS.map(({ href, label, description, Icon }) => (
            <Link key={href} href={href} className="rounded-lg border border-gray-200 bg-white p-5 hover:border-impilo-400 hover:shadow-sm transition-all">
              <div className="flex items-center gap-3 mb-2">
                <div className="rounded-lg bg-violet-50 p-2"><Icon className="h-5 w-5 text-violet-600" /></div>
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
