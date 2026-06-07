"use client";

import Link from "next/link";
import { CalendarDays, Plus, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useMadiDrives } from "@/hooks/queries/useMadi";

export default function MadiDrivesPage() {
  const { data, isPending, isError } = useMadiDrives();
  const drives = data ?? [];

  return (
    <AppLayout>
      <PageShell title="Donation Drives" subtitle="Facility workspace for blood collection events" icon={<CalendarDays className="h-6 w-6" />}>
        <div className="flex justify-end mb-4">
          <Link href="/madi/drives/new" className="inline-flex items-center gap-2 rounded-xl bg-rose-600 px-4 py-2 text-sm font-medium text-white hover:bg-rose-700">
            <Plus className="h-4 w-4" /> New drive
          </Link>
        </div>

        {isPending && (
          <div className="flex items-center gap-2 text-gray-500">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading drives…
          </div>
        )}

        {isError && (
          <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
            Drives could not be loaded. Check facility context and retry.
          </div>
        )}

        {!isPending && !isError && drives.length === 0 && (
          <div className="rounded-2xl border border-dashed border-gray-300 p-12 text-center">
            <CalendarDays className="mx-auto h-10 w-10 text-gray-400" />
            <h3 className="mt-3 font-semibold text-gray-900">No drives yet</h3>
            <p className="mt-1 text-sm text-gray-600">Create your first community or facility drive.</p>
            <Link href="/madi/drives/new" className="mt-4 inline-flex rounded-xl bg-rose-600 px-4 py-2 text-sm font-medium text-white">
              Create drive
            </Link>
          </div>
        )}

        {drives.length > 0 && (
          <div className="rounded-2xl border border-gray-200 bg-white overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-xs uppercase text-gray-500">
                <tr>
                  <th className="px-4 py-3 text-left">Title</th>
                  <th className="px-4 py-3 text-left">Type</th>
                  <th className="px-4 py-3 text-left">Status</th>
                  <th className="px-4 py-3 text-left">Schedule</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {drives.map((d) => (
                  <tr key={d.driveId} className="hover:bg-gray-50">
                    <td className="px-4 py-3">
                      <Link href={`/madi/drives/${d.driveId}`} className="font-medium text-rose-700 hover:text-rose-900">
                        {d.title}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-gray-700">{d.driveType}</td>
                    <td className="px-4 py-3 text-gray-700">{d.status ?? "DRAFT"}</td>
                    <td className="px-4 py-3 text-gray-500 text-xs">
                      {new Date(d.startAt).toLocaleString()} – {new Date(d.endAt).toLocaleString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
