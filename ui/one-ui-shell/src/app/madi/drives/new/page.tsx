"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { CalendarPlus, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useCreateDrive } from "@/hooks/queries/useMadi";

export default function NewDrivePage() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const create = useCreateDrive();
  const [title, setTitle] = useState("");
  const [driveType, setDriveType] = useState("COMMUNITY");
  const [startAt, setStartAt] = useState("");
  const [endAt, setEndAt] = useState("");
  const [capacity, setCapacity] = useState("50");

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    create.mutate(
      {
        title,
        drive_type: driveType,
        start_at: new Date(startAt).toISOString(),
        end_at: new Date(endAt).toISOString(),
        capacity: Number(capacity) || undefined,
        created_by: user?.displayName ?? user?.id,
      },
      {
        onSuccess: (drive) => {
          const id = (drive as { driveId?: string }).driveId;
          if (id) router.push(`/madi/drives/${id}`);
          else router.push("/madi/drives");
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell title="New Donation Drive" subtitle="Schedule a facility or outreach collection event" icon={<CalendarPlus className="h-6 w-6" />}>
        <form onSubmit={handleSubmit} className="max-w-lg space-y-4 rounded-2xl border border-gray-200 bg-white p-6">
          <label className="block text-sm font-medium text-gray-700">
            Title
            <input required value={title} onChange={(e) => setTitle(e.target.value)} className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm" />
          </label>
          <label className="block text-sm font-medium text-gray-700">
            Drive type
            <select value={driveType} onChange={(e) => setDriveType(e.target.value)} className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm bg-white">
              <option value="COMMUNITY">Community</option>
              <option value="FACILITY">Facility</option>
              <option value="MOBILE">Mobile unit</option>
            </select>
          </label>
          <div className="grid grid-cols-2 gap-3">
            <label className="block text-sm font-medium text-gray-700">
              Start
              <input type="datetime-local" required value={startAt} onChange={(e) => setStartAt(e.target.value)} className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm" />
            </label>
            <label className="block text-sm font-medium text-gray-700">
              End
              <input type="datetime-local" required value={endAt} onChange={(e) => setEndAt(e.target.value)} className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm" />
            </label>
          </div>
          <label className="block text-sm font-medium text-gray-700">
            Capacity
            <input type="number" min={1} value={capacity} onChange={(e) => setCapacity(e.target.value)} className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm" />
          </label>
          <button type="submit" disabled={create.isPending} className="inline-flex items-center gap-2 rounded-xl bg-rose-600 px-4 py-2 text-sm font-medium text-white hover:bg-rose-700 disabled:opacity-50">
            {create.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            Create drive
          </button>
        </form>
      </PageShell>
    </AppLayout>
  );
}
