"use client";

import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Users, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NhumeRecordPanel } from "@/components/nhume/NhumeRecordPanel";
import { useNhumeCourier } from "@/hooks/useNhume";

const COURIER_FIELDS = [
  { key: "display_name", label: "Display name" },
  { key: "status", label: "Status" },
  { key: "verification_status", label: "Verification" },
  { key: "phone", label: "Phone" },
  { key: "email", label: "Email" },
  { key: "vehicle_mode", label: "Vehicle mode" },
  { key: "zone_id", label: "Zone" },
] as const;

export default function NhumeCourierDetailPage() {
  const params = useParams<{ courierId: string }>();
  const id = params?.courierId as string | undefined;
  const { data, isPending, isError } = useNhumeCourier(id);
  const record = (data ?? {}) as Record<string, unknown>;

  return (
    <AppLayout>
      <PageShell title="Courier Profile" subtitle="Affiliations, verification, training and zones" icon={<Users className="h-6 w-6" />}>
        <div className="mb-4">
          <Link href="/nhume/couriers" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /> Back to couriers
          </Link>
        </div>
        {isPending && (
          <div className="rounded-2xl border border-border bg-card p-10 text-center text-muted-foreground">
            <Loader2 className="inline-block h-5 w-5 animate-spin text-teal-500 mr-2" /> Loading…
          </div>
        )}
        {isError && <div className="rounded-2xl border border-danger/28 bg-danger-soft p-4 text-sm text-rose-800">Courier not found.</div>}
        {data && (
          <div className="rounded-2xl border border-border bg-card p-5">
            <h2 className="text-xl font-bold text-foreground">{String(record.display_name ?? id)}</h2>
            <NhumeRecordPanel record={record} fields={[...COURIER_FIELDS]} />
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
