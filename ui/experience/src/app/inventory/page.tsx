"use client";

/**
 * Inventory Hub — Card grid for inventory management.
 * Route: /inventory
 */

import Link from "next/link";
import { Package, ClipboardList, ArrowLeftRight, FileInput } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const INVENTORY_SECTIONS = [
  {
    title: "Stock Items",
    description: "View current inventory items and stock levels",
    href: "/inventory/counts",
    icon: Package,
    color: "bg-blue-100 text-blue-600",
  },
  {
    title: "Stock Counts",
    description: "Physical stock count records and discrepancies",
    href: "/inventory/counts",
    icon: ClipboardList,
    color: "bg-purple-100 text-purple-600",
  },
  {
    title: "Movements",
    description: "Track stock movements between locations",
    href: "/inventory/movements",
    icon: ArrowLeftRight,
    color: "bg-amber-100 text-amber-600",
  },
  {
    title: "Requisitions",
    description: "Manage stock requisition requests",
    href: "/inventory/requisitions",
    icon: FileInput,
    color: "bg-green-100 text-green-600",
  },
];

export default function InventoryHubPage() {
  return (
    <AppLayout>
      <PageShell title="Inventory" subtitle="Manage stock items, counts, movements, and requisitions">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {INVENTORY_SECTIONS.map((section) => (
            <Link
              key={section.title}
              href={section.href}
              className="bg-white rounded-lg border border-gray-200 p-5 hover:border-blue-300 hover:shadow-md transition-all group"
            >
              <div className="flex items-start gap-4">
                <div className={`w-10 h-10 rounded-lg flex items-center justify-center shrink-0 ${section.color}`}>
                  <section.icon className="w-5 h-5" />
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
