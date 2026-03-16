"use client";

/**
 * Finance Dashboard — Hub with card grid linking to finance sub-pages.
 * Route: /finance | pageTitle: "Finance Dashboard"
 */

import Link from "next/link";
import { Receipt, FileText, CreditCard, BookOpen } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const FINANCE_SECTIONS = [
  {
    title: "Billing",
    description: "Manage invoices and billing records",
    href: "/finance/billing",
    icon: Receipt,
    color: "bg-blue-100 text-blue-600",
  },
  {
    title: "Claims",
    description: "Submit and track insurance claims",
    href: "/finance/claims",
    icon: FileText,
    color: "bg-green-100 text-green-600",
  },
  {
    title: "Payments",
    description: "View payment records and transactions",
    href: "/finance/payments",
    icon: CreditCard,
    color: "bg-purple-100 text-purple-600",
  },
  {
    title: "Tariffs",
    description: "Manage service tariff schedules",
    href: "/finance/tariffs",
    icon: BookOpen,
    color: "bg-amber-100 text-amber-600",
  },
] as const;

export default function FinancePage() {
  return (
    <AppLayout>
      <PageShell
        title="Finance Dashboard"
        subtitle="Financial management and revenue cycle"
      >
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {FINANCE_SECTIONS.map((section) => {
            const Icon = section.icon;
            return (
              <Link
                key={section.href}
                href={section.href}
                className="bg-white rounded-lg border border-gray-200 p-5 hover:border-blue-300 hover:shadow-md transition-all group"
              >
                <div className="flex items-start gap-3">
                  <div
                    className={`w-10 h-10 rounded-lg ${section.color} flex items-center justify-center shrink-0`}
                  >
                    <Icon className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="font-medium text-gray-900 text-sm group-hover:text-blue-600 transition-colors">
                      {section.title}
                    </h3>
                    <p className="text-xs text-gray-500 mt-0.5">{section.description}</p>
                  </div>
                </div>
              </Link>
            );
          })}
        </div>
      </PageShell>
    </AppLayout>
  );
}
