"use client";

import Link from "next/link";
import { History, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useDonorByPerson, useDonorHistory } from "@/hooks/queries/useMadi";

export default function DonorHistoryPage() {
  const user = useAuthStore((s) => s.user);
  const personCpid = user?.healthId || user?.id || "";
  const { data: donor, isPending: donorPending } = useDonorByPerson(personCpid || undefined);
  const { data: history, isPending: histPending } = useDonorHistory(donor?.donorId);

  const donations = history?.donations ?? [];
  const screenings = history?.screenings ?? [];

  return (
    <AppLayout>
      <PageShell title="Donation History" subtitle="Your past donations and screenings" icon={<History className="h-6 w-6" />}>
        {(donorPending || histPending) && (
          <div className="flex items-center gap-2 text-gray-500">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading history…
          </div>
        )}

        {!donorPending && !donor && (
          <div className="rounded-2xl border border-dashed border-gray-300 p-8 text-center">
            <p className="text-sm text-gray-600">Register as a donor to build your history.</p>
            <Link href="/madi/donor/register" className="mt-3 inline-block text-sm font-medium text-rose-600">
              Register →
            </Link>
          </div>
        )}

        {donor && !histPending && donations.length === 0 && screenings.length === 0 && (
          <div className="rounded-2xl border border-dashed border-gray-300 p-8 text-center">
            <p className="text-sm text-gray-600">No donations recorded yet.</p>
            <Link href="/madi/donor/drives" className="mt-3 inline-block text-sm font-medium text-rose-600">
              Find a drive →
            </Link>
          </div>
        )}

        {donations.length > 0 && (
          <section className="mb-6">
            <h2 className="text-sm font-semibold text-gray-900 mb-2">Donations</h2>
            <ul className="space-y-2">
              {donations.map((d, i) => (
                <li key={i} className="rounded-xl border border-gray-200 bg-white p-3 text-sm">
                  {String((d as Record<string, unknown>).bag_number ?? "Donation")} ·{" "}
                  {(d as Record<string, unknown>).collected_at
                    ? new Date(String((d as Record<string, unknown>).collected_at)).toLocaleDateString()
                    : "Date pending"}
                </li>
              ))}
            </ul>
          </section>
        )}

        {screenings.length > 0 && (
          <section>
            <h2 className="text-sm font-semibold text-gray-900 mb-2">Screenings</h2>
            <ul className="space-y-2">
              {screenings.map((s, i) => (
                <li key={i} className="rounded-xl border border-gray-200 bg-white p-3 text-sm">
                  Result: {String((s as Record<string, unknown>).result ?? "—")}
                </li>
              ))}
            </ul>
          </section>
        )}
      </PageShell>
    </AppLayout>
  );
}
