"use client";

/**
 * Care Delegation — Health OS §4
 * Delegate or accept care responsibilities for dependants.
 * Route: /caregiving/delegation | Zone: caregiving | Guard: auth
 */

import { Share2, UserPlus, UserCheck, Clock } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const TABS = [
  { label: "Active Delegations", Icon: UserCheck, description: "Care responsibilities you have delegated or accepted" },
  { label: "Pending Requests", Icon: Clock, description: "Delegation requests awaiting your response" },
  { label: "Invite Caregiver", Icon: UserPlus, description: "Invite someone to share care responsibilities" },
];

export default function DelegationPage() {
  return (
    <AppLayout>
      <PageShell
        title="Care Delegation"
        subtitle="Delegate or accept care responsibilities for dependants"
        icon={<Share2 className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {TABS.map(({ label, Icon, description }) => (
              <div
                key={label}
                className="rounded-lg border border-border bg-card p-5 hover:border-purple-400 hover:shadow-sm transition-all cursor-pointer"
              >
                <div className="flex items-center gap-3 mb-2">
                  <div className="rounded-lg bg-warning-soft p-2">
                    <Icon className="h-5 w-5 text-purple-600" />
                  </div>
                  <h3 className="font-semibold text-foreground">{label}</h3>
                </div>
                <p className="text-sm text-muted-foreground">{description}</p>
              </div>
            ))}
          </div>

          {/* Empty state */}
          <div className="rounded-lg border border-dashed border-border bg-background p-12 text-center">
            <Share2 className="mx-auto h-12 w-12 text-muted-foreground" />
            <h3 className="mt-4 text-sm font-semibold text-foreground">No delegations</h3>
            <p className="mt-2 text-sm text-muted-foreground">
              You have no active care delegation arrangements. Invite a caregiver to share responsibilities.
            </p>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
