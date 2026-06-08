"use client";

import { useParams } from "next/navigation";
import Link from "next/link";
import { useState } from "react";
import { CalendarDays, Loader2, Send, UserPlus, Droplet } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useCloseDrive,
  useMadiDashboard,
  usePublishDrive,
  useRecordDonation,
} from "@/hooks/queries/useMadi";

export default function DriveDetailPage() {
  const params = useParams<{ driveId: string }>();
  const driveId = params.driveId;
  const { data: metrics, isPending } = useMadiDashboard({ scope: "drive", driveId });
  const publish = usePublishDrive(driveId);
  const close = useCloseDrive(driveId);
  const recordDonation = useRecordDonation(driveId);
  const [donorId, setDonorId] = useState("");
  const [bagNumber, setBagNumber] = useState("");

  return (
    <AppLayout>
      <PageShell title="Drive Detail" subtitle={String(metrics?.title ?? driveId)} icon={<CalendarDays className="h-6 w-6" />}>
        {isPending && (
          <div className="flex items-center gap-2 text-gray-500 mb-4">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading drive…
          </div>
        )}

        {metrics && (
          <div className="mb-6 grid grid-cols-2 md:grid-cols-4 gap-3">
            <div className="rounded-xl border border-gray-200 bg-white p-3">
              <p className="text-xs text-gray-500">Status</p>
              <p className="text-lg font-semibold">{String(metrics.status ?? "—")}</p>
            </div>
            <div className="rounded-xl border border-gray-200 bg-white p-3">
              <p className="text-xs text-gray-500">Capacity</p>
              <p className="text-lg font-semibold">{String(metrics.capacity ?? "—")}</p>
            </div>
          </div>
        )}

        <div className="flex flex-wrap gap-2 mb-6">
          <button
            type="button"
            onClick={() => publish.mutate({})}
            disabled={publish.isPending}
            className="inline-flex items-center gap-2 rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-medium text-rose-800 hover:bg-rose-100"
          >
            <Send className="h-4 w-4" /> Publish
          </button>
          <button
            type="button"
            onClick={() => close.mutate()}
            disabled={close.isPending}
            className="inline-flex items-center gap-2 rounded-xl border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            Close drive
          </button>
          <Link href="/madi/drives" className="inline-flex items-center gap-2 rounded-xl border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50">
            All drives
          </Link>
        </div>

        <section className="rounded-2xl border border-gray-200 bg-white p-5">
          <h2 className="text-sm font-semibold text-gray-900 flex items-center gap-2 mb-3">
            <Droplet className="h-4 w-4 text-rose-500" /> Record donation
          </h2>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              recordDonation.mutate({ donor_id: donorId, bag_number: bagNumber, volume_ml: 450 });
            }}
            className="grid grid-cols-1 md:grid-cols-3 gap-3"
          >
            <input required placeholder="Donor UUID" value={donorId} onChange={(e) => setDonorId(e.target.value)} className="rounded-xl border border-gray-300 px-3 py-2 text-sm" />
            <input required placeholder="Bag number" value={bagNumber} onChange={(e) => setBagNumber(e.target.value)} className="rounded-xl border border-gray-300 px-3 py-2 text-sm" />
            <button type="submit" disabled={recordDonation.isPending} className="rounded-xl bg-rose-600 text-white text-sm font-medium py-2 hover:bg-rose-700 disabled:opacity-50 inline-flex items-center justify-center gap-2">
              {recordDonation.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <UserPlus className="h-4 w-4" />}
              Record
            </button>
          </form>
        </section>
      </PageShell>
    </AppLayout>
  );
}
