"use client";

/**
 * Administration — Admin dashboard with cards for all governance sub-pages.
 * Route: /admin | pageTitle: "Administration"
 */

import Link from "next/link";
import {
  Users,
  Shield,
  ScrollText,
  FileSearch,
  HeartHandshake,
  Smartphone,
  Key,
  Globe,
  Building,
  AlertTriangle,
  BookHeart,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const ADMIN_SECTIONS = [
  {
    title: "Users",
    description: "Manage user accounts, roles, and permissions",
    href: "/admin/users",
    icon: Users,
    color: "bg-blue-100 text-blue-600",
  },
  {
    title: "Roles",
    description: "Define and manage RBAC role definitions",
    href: "/admin/roles",
    icon: Shield,
    color: "bg-indigo-100 text-indigo-600",
  },
  {
    title: "Policies",
    description: "Configure ABAC policies and access rules",
    href: "/admin/policies",
    icon: ScrollText,
    color: "bg-purple-100 text-purple-600",
  },
  {
    title: "Audit",
    description: "View the complete audit trail of system actions",
    href: "/admin/audit",
    icon: FileSearch,
    color: "bg-green-100 text-green-600",
  },
  {
    title: "Consent",
    description: "Manage patient consent directives",
    href: "/admin/consent",
    icon: HeartHandshake,
    color: "bg-pink-100 text-pink-600",
  },
  {
    title: "Devices",
    description: "Register and manage trusted devices",
    href: "/admin/devices",
    icon: Smartphone,
    color: "bg-cyan-100 text-cyan-600",
  },
  {
    title: "Keys",
    description: "Manage cryptographic keys and certificates",
    href: "/admin/keys",
    icon: Key,
    color: "bg-yellow-100 text-yellow-600",
  },
  {
    title: "Federation",
    description: "Configure identity federation and trust relationships",
    href: "/admin/federation",
    icon: Globe,
    color: "bg-teal-100 text-teal-600",
  },
  {
    title: "Tenants",
    description: "Multi-tenant configuration and management",
    href: "/admin/tenants",
    icon: Building,
    color: "bg-orange-100 text-orange-600",
  },
  {
    title: "Break Glass",
    description: "Emergency access log and override management",
    href: "/admin/break-glass",
    icon: AlertTriangle,
    color: "bg-red-100 text-red-600",
  },
  {
    title: "Clinical Curation",
    description: "Review and approve national clinical knowledge proposals",
    href: "/admin/clinical-curation",
    icon: BookHeart,
    color: "bg-rose-100 text-rose-600",
  },
] as const;

export default function AdminPage() {
  return (
    <AppLayout>
      <PageShell
        title="Administration"
        subtitle="TSHEPO Governance and platform administration"
      >
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {ADMIN_SECTIONS.map((section) => {
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
