"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ClipboardList, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { getStoredBloodBankId } from "@/lib/madi-session";

export default function BloodBankOrdersPage() {
  const facility = useFacilityStore((s) => s.facility);
  const [bloodBankId, setBloodBankId] = useState("");

  useEffect(() => {
    if (facility?.id) setBloodBankId(getStoredBloodBankId(facility.id));
  }, [facility?.id]);

  return (
    <AppLayout>
      <PageShell title="Blood Bank Orders" subtitle="Incoming and fulfilled requests" icon={<ClipboardList className="h-6 w-6" />}>
        {!bloodBankId ? (
          <div className="rounded-2xl border border-dashed border-border p-8 text-center">
            <p className="text-sm text-muted-foreground">Configure your blood bank on the hub first.</p>
            <Link href="/madi/blood-bank" className="mt-3 inline-block text-sm font-medium text-rose-600">Blood bank hub →</Link>
          </div>
        ) : (
          <div className="space-y-4">
            <p className="text-sm text-muted-foreground">
              Bank <code className="rounded bg-neutral-100 px-1 text-xs">{bloodBankId}</code> — track clinical orders from{" "}
              <Link href="/madi/orders" className="text-rose-600 hover:underline">Order Blood</Link> and progress crossmatch/issue workflows.
            </p>
            <Link href="/madi/dashboard" className="inline-flex items-center gap-2 rounded-xl bg-rose-600 px-4 py-2 text-sm font-medium text-white hover:bg-rose-700">
              View facility dashboard
            </Link>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
