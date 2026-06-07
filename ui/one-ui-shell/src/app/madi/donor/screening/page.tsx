"use client";

import Link from "next/link";
import { ClipboardCheck, Loader2, AlertTriangle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useDonorByPerson } from "@/hooks/queries/useMadi";

export default function DonorScreeningPage() {
  const user = useAuthStore((s) => s.user);
  const personCpid = user?.healthId || user?.id || "";
  const { data: donor, isPending, isError } = useDonorByPerson(personCpid || undefined);

  return (
    <AppLayout>
      <PageShell
        title="Donor Screening"
        subtitle="Eligibility checks happen in person with a trained nurse"
        icon={<ClipboardCheck className="h-6 w-6" />}
      >
        {isPending && (
          <div className="flex items-center gap-2 text-gray-500">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading…
          </div>
        )}

        {isError && (
          <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm flex gap-2">
            <AlertTriangle className="h-4 w-4" /> Could not verify donor status.
          </div>
        )}

        {!isPending && !donor && (
          <div className="rounded-2xl border border-dashed border-gray-300 p-8 text-center">
            <p className="text-sm text-gray-600">Register first, then attend a drive for screening.</p>
            <Link href="/madi/donor/register" className="mt-3 inline-block text-sm font-medium text-rose-600">
              Register as donor →
            </Link>
          </div>
        )}

        {donor && (
          <div className="space-y-4">
            <div className="rounded-2xl border border-gray-200 bg-white p-5 text-sm text-gray-700">
              <p>
                Screening is performed at a donation drive or blood bank — not on this page.
                Bring valid ID, eat a light meal, and hydrate. A nurse will ask health questions
                and may defer you if it is not safe to donate today.
              </p>
            </div>
            <Link
              href="/madi/donor/drives"
              className="inline-flex rounded-xl bg-rose-600 px-4 py-2 text-sm font-medium text-white hover:bg-rose-700"
            >
              Find a donation drive
            </Link>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
