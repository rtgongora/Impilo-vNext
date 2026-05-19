"use client";

/**
 * Drivers & Couriers — admin list.
 */

import Link from "next/link";
import { Users, Loader2, Plus } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useNhumeCouriers } from "@/hooks/useNhume";

const STATUS_TONE: Record<string, string> = {
  AVAILABLE: "bg-emerald-50 text-emerald-700 border-emerald-200",
  ON_DUTY: "bg-indigo-50 text-indigo-700 border-indigo-200",
  OFF_DUTY: "bg-gray-100 text-gray-700 border-gray-200",
  SUSPENDED: "bg-rose-50 text-rose-700 border-rose-200",
  UNVERIFIED: "bg-amber-50 text-amber-800 border-amber-200",
};

export default function NhumeCouriersPage() {
  const { data, isPending } = useNhumeCouriers();
  const rows = data?.data ?? [];
  return (
    <AppLayout>
      <PageShell
        title="Drivers & Couriers"
        subtitle="Profiles, training, affiliations and Trust-Layer verification"
        icon={<Users className="h-6 w-6" />}
      >
        <div className="flex justify-end mb-4">
          <Link href="/nhume/couriers?create=1" className="inline-flex items-center gap-2 rounded-xl bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700">
            <Plus className="h-4 w-4" /> Add courier
          </Link>
        </div>
        {isPending ? (
          <div className="rounded-2xl border border-gray-200 bg-white p-10 text-center text-gray-500">
            <Loader2 className="inline-block h-5 w-5 animate-spin text-teal-500 mr-2" /> Loading…
          </div>
        ) : rows.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-gray-300 bg-gray-50 p-12 text-center">
            <Users className="mx-auto h-10 w-10 text-gray-400" />
            <h3 className="mt-3 font-semibold text-gray-900">No couriers registered</h3>
            <p className="mt-1 text-sm text-gray-600">Onboard drivers, CHWs, facility runners or operator profiles.</p>
          </div>
        ) : (
          <div className="rounded-2xl border border-gray-200 bg-white overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
                <tr>
                  <th className="px-4 py-3 text-left">Name</th>
                  <th className="px-4 py-3 text-left">Role</th>
                  <th className="px-4 py-3 text-left">Org / facility</th>
                  <th className="px-4 py-3 text-left">Availability</th>
                  <th className="px-4 py-3 text-left">Verified</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {rows.map((r) => {
                  const id = String((r as Record<string, unknown>).courier_id ?? (r as Record<string, unknown>).id ?? "");
                  const c = r as Record<string, unknown>;
                  const status = String(c.availability_status ?? "OFF_DUTY").toUpperCase();
                  return (
                    <tr key={id} className="hover:bg-gray-50">
                      <td className="px-4 py-3">
                        <Link href={`/nhume/couriers/${id}`} className="font-medium text-teal-700 hover:text-teal-900">
                          {String(c.display_name ?? id)}
                        </Link>
                      </td>
                      <td className="px-4 py-3 text-gray-700">{String(c.role ?? c.profile_kind ?? "—")}</td>
                      <td className="px-4 py-3 text-gray-700">{String(c.organisation_ref ?? c.facility_ref ?? "—")}</td>
                      <td className="px-4 py-3">
                        <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[11px] ${STATUS_TONE[status] ?? STATUS_TONE.OFF_DUTY}`}>{status}</span>
                      </td>
                      <td className="px-4 py-3 text-xs text-gray-500">{String(c.trust_verification_status ?? "—")}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
