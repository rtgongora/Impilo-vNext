"use client";

/**
 * Caregiving Hub — Health OS §4
 * Delegated care, family caregivers, dependant management.
 * Route: /caregiving | Zone: caregiving | Guard: auth
 */

import Link from "next/link";
import { HeartHandshake, Users, Share2, ListChecks, Bell, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useMyCaregivingLinkages, type CaregiverLinkage } from "@/hooks/queries/useCaregiverLinkage";

const SECTIONS = [
  { href: "/caregiving/dependants", label: "My Dependants", description: "View and manage people you care for", Icon: Users },
  { href: "/caregiving/delegation", label: "Care Delegation", description: "Delegate or accept care responsibilities", Icon: Share2 },
  { href: "/caregiving/tasks", label: "Care Tasks", description: "View and complete assigned care tasks", Icon: ListChecks },
  { href: "/caregiving/notifications", label: "Care Alerts", description: "Notifications about your dependants' health", Icon: Bell },
];

const LINKAGE_STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  SUSPENDED: "bg-amber-100 text-warning-foreground",
  REVOKED: "bg-red-100 text-danger",
  EXPIRED: "bg-neutral-100 text-muted-foreground",
};

export default function CaregivingPage() {
  const { data: linkagesData, isLoading } = useMyCaregivingLinkages();
  const linkages: CaregiverLinkage[] = linkagesData?.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Caregiving Hub" subtitle="Health OS §4 — Delegated care, family caregivers, dependant management" icon={<HeartHandshake className="h-6 w-6" />}>
        {linkages.length > 0 && (
          <div className="mb-6 bg-card rounded-lg border border-border p-5">
            <div className="flex items-center gap-2 mb-3">
              <Users className="h-4 w-4 text-purple-600" />
              <h3 className="text-sm font-medium text-foreground">My Dependants ({linkages.length})</h3>
            </div>
            <div className="space-y-2">
              {linkages.map((linkage) => {
                const statusStyle = LINKAGE_STATUS_STYLES[linkage.status] ?? "bg-neutral-100 text-muted-foreground";
                return (
                  <div key={linkage.id} className="flex items-center justify-between p-3 rounded-lg border border-border bg-background">
                    <div>
                      <p className="text-sm font-medium text-foreground">
                        {linkage.subjectDisplayName ?? linkage.subjectHealthId}
                      </p>
                      <p className="text-xs text-muted-foreground">
                        {linkage.relationship} — {linkage.linkageType}
                      </p>
                    </div>
                    <span className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${statusStyle}`}>
                      {linkage.status}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {isLoading && (
          <div className="mb-6 flex items-center justify-center py-8">
            <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading linkages...</span>
          </div>
        )}

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {SECTIONS.map(({ href, label, description, Icon }) => (
            <Link key={href} href={href} className="rounded-lg border border-border bg-card p-5 hover:border-impilo-400 hover:shadow-sm transition-all">
              <div className="flex items-center gap-3 mb-2">
                <div className="rounded-lg bg-warning-soft p-2"><Icon className="h-5 w-5 text-purple-600" /></div>
                <h3 className="font-semibold text-foreground">{label}</h3>
              </div>
              <p className="text-sm text-muted-foreground">{description}</p>
            </Link>
          ))}
        </div>
      </PageShell>
    </AppLayout>
  );
}
