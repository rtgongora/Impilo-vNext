"use client";

import { useState } from "react";
import Link from "next/link";
import { MapPin, Loader2, CalendarDays } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useDrivesNearMe, useMadiDrives } from "@/hooks/queries/useMadi";

export default function DonorDrivesPage() {
  const [siteRef, setSiteRef] = useState("");
  const [draftSite, setDraftSite] = useState("");
  const nearMe = useDrivesNearMe(siteRef || undefined);
  const allDrives = useMadiDrives();

  const nearRows = nearMe.data ?? [];
  const tenantDrives = allDrives.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Donation Drives Near Me"
        subtitle="Find voluntary blood donation events in your area"
        icon={<CalendarDays className="h-6 w-6" />}
      >
        <div className="mb-6 flex flex-wrap gap-2 items-end">
          <label className="text-sm text-gray-700 flex-1 min-w-[200px]">
            Ndila site reference (optional)
            <input
              value={draftSite}
              onChange={(e) => setDraftSite(e.target.value)}
              placeholder="e.g. facility or community site ref"
              className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm"
            />
          </label>
          <button
            type="button"
            onClick={() => setSiteRef(draftSite.trim())}
            className="rounded-xl bg-rose-600 px-4 py-2 text-sm font-medium text-white hover:bg-rose-700"
          >
            Search nearby
          </button>
        </div>

        {siteRef && nearMe.isPending && (
          <div className="flex items-center gap-2 text-gray-500 mb-4">
            <Loader2 className="h-4 w-4 animate-spin" /> Searching…
          </div>
        )}

        {siteRef && !nearMe.isPending && nearRows.length === 0 && (
          <div className="rounded-2xl border border-dashed border-gray-300 p-8 text-center mb-6">
            <MapPin className="mx-auto h-8 w-8 text-gray-400" />
            <p className="mt-2 text-sm text-gray-600">No drives found within 25 km of that site.</p>
          </div>
        )}

        {nearRows.length > 0 && (
          <section className="mb-8">
            <h2 className="text-sm font-semibold text-gray-900 mb-3">Near you</h2>
            <ul className="space-y-2">
              {nearRows.map((row, i) => (
                <li key={String(row.drive_id ?? i)} className="rounded-xl border border-gray-200 bg-white p-4 text-sm">
                  <p className="font-medium text-gray-900">{String(row.title ?? "Donation drive")}</p>
                  {row.start_at != null && row.start_at !== "" ? (
                    <p className="text-gray-500 mt-1">{new Date(String(row.start_at)).toLocaleString()}</p>
                  ) : null}
                </li>
              ))}
            </ul>
          </section>
        )}

        <section>
          <h2 className="text-sm font-semibold text-gray-900 mb-3">All published drives</h2>
          {allDrives.isPending && (
            <div className="flex items-center gap-2 text-gray-500">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading drives…
            </div>
          )}
          {!allDrives.isPending && tenantDrives.length === 0 && (
            <div className="rounded-2xl border border-dashed border-gray-300 p-8 text-center">
              <p className="text-sm text-gray-600">No drives scheduled yet.</p>
              <Link href="/madi/donor" className="mt-2 inline-block text-sm text-rose-600">Back to donor hub</Link>
            </div>
          )}
          <ul className="space-y-2">
            {tenantDrives.map((d) => (
              <li key={d.driveId} className="rounded-xl border border-gray-200 bg-white p-4 text-sm flex justify-between items-center">
                <div>
                  <p className="font-medium text-gray-900">{d.title}</p>
                  <p className="text-gray-500">{d.driveType} · {d.status ?? "DRAFT"}</p>
                  <p className="text-xs text-gray-400 mt-1">
                    {new Date(d.startAt).toLocaleString()} – {new Date(d.endAt).toLocaleString()}
                  </p>
                </div>
              </li>
            ))}
          </ul>
        </section>
      </PageShell>
    </AppLayout>
  );
}
