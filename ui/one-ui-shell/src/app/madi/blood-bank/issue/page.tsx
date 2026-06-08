"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Droplet, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { getStoredBloodBankId } from "@/lib/madi-session";

export default function BloodBankIssuePage() {
  const facility = useFacilityStore((s) => s.facility);
  const router = useRouter();
  const [bloodBankId, setBloodBankId] = useState("");
  const [orderId, setOrderId] = useState("");

  useEffect(() => {
    if (facility?.id) setBloodBankId(getStoredBloodBankId(facility.id));
  }, [facility?.id]);

  return (
    <AppLayout>
      <PageShell title="Issue Blood" subtitle="Release matched units to clinical teams" icon={<Droplet className="h-6 w-6" />}>
        {!bloodBankId ? (
          <div className="rounded-2xl border border-dashed border-gray-300 p-8 text-center">
            <Link href="/madi/blood-bank" className="text-sm font-medium text-rose-600">Configure blood bank →</Link>
          </div>
        ) : (
          <div className="max-w-md space-y-4 rounded-2xl border border-gray-200 bg-white p-6">
            <p className="text-sm text-gray-600">
              Issue is performed on a submitted order after crossmatch. Open the order to reserve and issue units.
            </p>
            <input
              value={orderId}
              onChange={(e) => setOrderId(e.target.value)}
              placeholder="Blood order UUID"
              className="w-full rounded-xl border border-gray-300 px-3 py-2 text-sm font-mono"
            />
            <button
              type="button"
              disabled={!orderId.trim()}
              onClick={() => router.push(`/madi/orders/${orderId.trim()}`)}
              className="rounded-xl bg-rose-600 px-4 py-2 text-sm font-medium text-white hover:bg-rose-700 disabled:opacity-50"
            >
              Open order to issue
            </button>
            <Link href="/madi/transfusion" className="block text-sm text-rose-600 hover:underline">
              After issue → record transfusion
            </Link>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
