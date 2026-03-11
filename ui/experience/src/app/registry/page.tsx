"use client";

/**
 * Registry Hub — Cards for Providers, Facilities, Terminology, Products.
 * Route: /registry | pageTitle: "Registry Hub"
 */

import Link from "next/link";
import { UserCheck, Building2, BookOpen, Package } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const REGISTRY_SECTIONS = [
  {
    title: "Providers",
    description: "View and manage registered healthcare providers",
    href: "/registry/providers",
    icon: UserCheck,
    color: "bg-blue-100 text-blue-600",
  },
  {
    title: "Facilities",
    description: "Browse the facility registry and profiles",
    href: "/registry/facilities",
    icon: Building2,
    color: "bg-green-100 text-green-600",
  },
  {
    title: "Terminology",
    description: "Browse clinical terminology and coding systems",
    href: "/registry/terminology",
    icon: BookOpen,
    color: "bg-purple-100 text-purple-600",
  },
  {
    title: "Products",
    description: "View registered pharmaceutical and medical products",
    href: "/registry/products",
    icon: Package,
    color: "bg-orange-100 text-orange-600",
  },
] as const;

export default function RegistryHubPage() {
  return (
    <AppLayout>
      <PageShell
        title="Registry Hub"
        subtitle="Central registries for providers, facilities, terminology, and products"
      >
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {REGISTRY_SECTIONS.map((section) => {
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
