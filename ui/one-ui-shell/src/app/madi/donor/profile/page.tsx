"use client";

import Link from "next/link";
import { User, Loader2, AlertTriangle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useDonorByPerson, useDonorNextEligibility } from "@/hooks/queries/useMadi";

export default function DonorProfilePage() {
  const user = useAuthStore((s) => s.user);
  const personCpid = user?.healthId || user?.id || "";
  const { data: donor, isPending, isError } = useDonorByPerson(personCpid || undefined);
  const { data: eligibility } = useDonorNextEligibility(donor?.donorId);

  return (
    <AppLayout>
      <PageShell title="Donor Profile" subtitle="Your voluntary donor record" icon={<User className="h-6 w-6" />}>
        {isPending && (
          <div className="flex items-center gap-2 text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading profile…
          </div>
        )}

        {isError && (
          <div className="rounded-xl border border-danger/28 bg-danger-soft p-4 text-sm text-rose-800 flex gap-2">
            <AlertTriangle className="h-4 w-4 shrink-0" />
            Profile could not be loaded.
          </div>
        )}

        {!isPending && !donor && (
          <div className="rounded-2xl border border-dashed border-border p-8 text-center">
            <p className="text-sm text-muted-foreground">No donor profile found for this account.</p>
            <Link href="/madi/donor/register" className="mt-3 inline-block text-sm font-medium text-rose-600 hover:text-rose-800">
              Register as a donor →
            </Link>
          </div>
        )}

        {donor && (
          <dl className="rounded-2xl border border-border bg-card divide-y divide-gray-100 text-sm">
            <div className="flex justify-between px-4 py-3">
              <dt className="text-muted-foreground">Blood group</dt>
              <dd className="font-medium text-foreground">{donor.bloodGroup}{donor.rhFactor ? ` (${donor.rhFactor})` : ""}</dd>
            </div>
            <div className="flex justify-between px-4 py-3">
              <dt className="text-muted-foreground">Status</dt>
              <dd className="font-medium text-foreground">{donor.status ?? "ACTIVE"}</dd>
            </div>
            {eligibility && (
              <div className="px-4 py-3">
                <dt className="text-muted-foreground mb-1">Next eligibility</dt>
                <dd className="text-foreground">
                  {eligibility.eligible === false
                    ? eligibility.reason ?? "Deferred — check back later"
                    : eligibility.nextEligibleAt
                      ? `Eligible from ${new Date(String(eligibility.nextEligibleAt)).toLocaleDateString()}`
                      : "Eligible when confirmed at screening"}
                </dd>
              </div>
            )}
          </dl>
        )}
      </PageShell>
    </AppLayout>
  );
}
