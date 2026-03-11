"use client";

/**
 * Health Marketplace — Hub with cards for Catalog, Orders, Vendors, Bookings.
 * Route: /marketplace | pageTitle: "Health Marketplace"
 */

import Link from "next/link";
import { ShoppingBag, Package, Store, CalendarCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const MARKETPLACE_SECTIONS = [
  {
    title: "Service Catalog",
    description: "Browse available health services, products, and supplies",
    href: "/marketplace/catalog",
    icon: ShoppingBag,
    color: "bg-blue-100 text-blue-600",
  },
  {
    title: "My Orders",
    description: "Track and manage your purchase orders",
    href: "/marketplace/orders",
    icon: Package,
    color: "bg-green-100 text-green-600",
  },
  {
    title: "Vendors",
    description: "View registered vendors and suppliers",
    href: "/marketplace/vendors",
    icon: Store,
    color: "bg-purple-100 text-purple-600",
  },
  {
    title: "Bookings",
    description: "Schedule and manage service bookings",
    href: "/marketplace/bookings",
    icon: CalendarCheck,
    color: "bg-orange-100 text-orange-600",
  },
] as const;

export default function MarketplacePage() {
  return (
    <AppLayout>
      <PageShell
        title="Health Marketplace"
        subtitle="Procure services, manage orders, and connect with vendors"
      >
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {MARKETPLACE_SECTIONS.map((section) => {
            const Icon = section.icon;
            return (
              <Link
                key={section.href}
                href={section.href}
                className="bg-white rounded-lg border border-gray-200 p-6 hover:border-blue-300 hover:shadow-md transition-all group"
              >
                <div className="flex items-start gap-4">
                  <div
                    className={`w-12 h-12 rounded-lg ${section.color} flex items-center justify-center shrink-0`}
                  >
                    <Icon className="w-6 h-6" />
                  </div>
                  <div>
                    <h3 className="font-medium text-gray-900 group-hover:text-blue-600 transition-colors">
                      {section.title}
                    </h3>
                    <p className="text-sm text-gray-500 mt-1">{section.description}</p>
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
