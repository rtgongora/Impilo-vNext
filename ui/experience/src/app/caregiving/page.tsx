"use client";

/**
 * Caregiving Hub — Health OS §4
 * Delegated care, family caregivers, dependant management.
 * Route: /caregiving | Zone: caregiving | Guard: auth
 */

import Link from "next/link";
import { HeartHandshake, Users, Share2, ListChecks, Bell } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const SECTIONS = [
  { href: "/caregiving/dependants", label: "My Dependants", description: "View and manage people you care for", Icon: Users },
  { href: "/caregiving/delegation", label: "Care Delegation", description: "Delegate or accept care responsibilities", Icon: Share2 },
  { href: "/caregiving/tasks", label: "Care Tasks", description: "View and complete assigned care tasks", Icon: ListChecks },
  { href: "/caregiving/notifications", label: "Care Alerts", description: "Notifications about your dependants' health", Icon: Bell },
];

export default function CaregivingPage() {
  return (
    <AppLayout>
      <PageShell title="Caregiving Hub" subtitle="Health OS §4 — Delegated care, family caregivers, dependant management" icon={<HeartHandshake className="h-6 w-6" />}>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {SECTIONS.map(({ href, label, description, Icon }) => (
            <Link key={href} href={href} className="rounded-lg border border-gray-200 bg-white p-5 hover:border-blue-400 hover:shadow-sm transition-all">
              <div className="flex items-center gap-3 mb-2">
                <div className="rounded-lg bg-purple-50 p-2"><Icon className="h-5 w-5 text-purple-600" /></div>
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
