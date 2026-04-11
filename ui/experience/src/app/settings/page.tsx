"use client";

/**
 * Settings Hub — Card grid linking to settings sub-pages.
 * Route: /settings | pageTitle: "Settings"
 */

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { User, ShieldCheck, Monitor, Bell, Plug, Shield } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";

const SETTINGS_SECTIONS = [
  {
    title: "Account",
    description: "Manage your profile, email, and contact information",
    href: "/settings/account",
    icon: User,
    color: "bg-blue-100 text-blue-600",
  },
  {
    title: "Security",
    description: "Password, multi-factor authentication, and active sessions",
    href: "/settings/security",
    icon: ShieldCheck,
    color: "bg-red-100 text-red-600",
  },
  {
    title: "Display",
    description: "Theme, font size, and layout density preferences",
    href: "/settings/display",
    icon: Monitor,
    color: "bg-purple-100 text-purple-600",
  },
  {
    title: "Notifications",
    description: "Configure email, SMS, and push notification preferences",
    href: "/settings/notifications",
    icon: Bell,
    color: "bg-amber-100 text-amber-600",
  },
  {
    title: "Integrations",
    description: "Manage connected services and API integrations",
    href: "/settings/integrations",
    icon: Plug,
    color: "bg-green-100 text-green-600",
  },
  {
    title: "Privacy & Data",
    description: "Consent management, data rights, and account deletion",
    href: "/settings/privacy",
    icon: Shield,
    color: "bg-indigo-100 text-indigo-600",
  },
] as const;

export default function SettingsPage() {
  const searchParams = useSearchParams();
  const fromOrg = searchParams.get("from") === "organization-admin";
  const withPlane = (href: string) => (fromOrg ? `${href}?from=organization-admin` : href);

  return (
    <AppLayout>
      <PageShell
        title="Settings"
        subtitle="Configure your account and preferences"
      >
        <OrganizationPlaneContextBar />
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {SETTINGS_SECTIONS.map((section) => {
            const Icon = section.icon;
            return (
              <Link
                key={section.href}
                href={withPlane(section.href)}
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
