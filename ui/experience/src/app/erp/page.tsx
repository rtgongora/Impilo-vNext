"use client";

/**
 * Native ERP hub — links to GL, HR, procurement, and fixed assets.
 * Route: /erp
 */

import Link from "next/link";
import { Landmark, Users, Truck, Building2, ArrowLeft } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const SECTIONS = [
  {
    title: "General ledger",
    description: "Accounts, periods, journals, trial balance, statements, budgets",
    href: "/erp/gl",
    icon: Landmark,
    color: "bg-emerald-100 text-emerald-700",
  },
  {
    title: "HR & payroll",
    description: "Employees, leave, attendance, payroll runs, payslips",
    href: "/erp/hr",
    icon: Users,
    color: "bg-violet-100 text-violet-700",
  },
  {
    title: "Procurement",
    description: "Suppliers, requisitions, POs, GRN, invoices, RFQs",
    href: "/erp/procurement",
    icon: Truck,
    color: "bg-amber-100 text-amber-700",
  },
  {
    title: "Fixed assets",
    description: "Asset financial details and depreciation schedule",
    href: "/erp/assets",
    icon: Building2,
    color: "bg-sky-100 text-sky-700",
  },
] as const;

export default function ErpHubPage() {
  return (
    <AppLayout>
      <PageShell title="Native ERP" subtitle="Institutional ledger, people, spend, and capital assets">
        <div className="mb-4">
          <Link
            href="/finance"
            className="inline-flex items-center gap-2 text-sm text-slate-600 hover:text-slate-900"
          >
            <ArrowLeft className="h-4 w-4" />
            Back to Finance
          </Link>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          {SECTIONS.map((s) => {
            const Icon = s.icon;
            return (
              <Link
                key={s.href}
                href={s.href}
                className="group flex gap-4 rounded-xl border border-slate-200 bg-white p-5 shadow-sm transition hover:border-slate-300 hover:shadow"
              >
                <div
                  className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-lg ${s.color}`}
                >
                  <Icon className="h-6 w-6" />
                </div>
                <div>
                  <h2 className="font-semibold text-slate-900 group-hover:text-impilo-600">{s.title}</h2>
                  <p className="mt-1 text-sm text-slate-600">{s.description}</p>
                </div>
              </Link>
            );
          })}
        </div>
      </PageShell>
    </AppLayout>
  );
}
