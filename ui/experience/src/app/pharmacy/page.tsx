"use client";

/**
 * Pharmacy Hub — Card grid for pharmacy features.
 * Route: /pharmacy
 */

import Link from "next/link";
import { Pill, FileText, Package } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const PHARMACY_SECTIONS = [
  {
    title: "Prescriptions",
    description: "View and manage patient prescriptions",
    href: "/pharmacy/prescriptions",
    icon: FileText,
    color: "bg-blue-100 text-blue-600",
  },
  {
    title: "Dispense",
    description: "Dispense medications for approved prescriptions",
    href: "/pharmacy/dispense",
    icon: Pill,
    color: "bg-green-100 text-green-600",
  },
  {
    title: "Stock",
    description: "Monitor pharmacy stock levels and reorder points",
    href: "/pharmacy/stock",
    icon: Package,
    color: "bg-amber-100 text-amber-600",
  },
];

export default function PharmacyHubPage() {
  return (
    <AppLayout>
      <PageShell title="Pharmacy" subtitle="Manage prescriptions, dispensing, and stock">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {PHARMACY_SECTIONS.map((section) => (
            <Link
              key={section.href}
              href={section.href}
              className="bg-white rounded-lg border border-gray-200 p-5 hover:border-blue-300 hover:shadow-md transition-all group"
            >
              <div className="flex flex-col items-center text-center gap-3">
                <div className={`w-12 h-12 rounded-lg flex items-center justify-center ${section.color}`}>
                  <section.icon className="w-6 h-6" />
                </div>
                <div>
                  <h3 className="font-medium text-gray-900 group-hover:text-blue-700">{section.title}</h3>
                  <p className="text-xs text-gray-500 mt-1">{section.description}</p>
                </div>
              </div>
            </Link>
          ))}
        </div>
      </PageShell>
    </AppLayout>
  );
}
