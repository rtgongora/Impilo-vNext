"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ClipboardList, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useCreateBloodOrder } from "@/hooks/queries/useMadi";

const COMPONENTS = ["WHOLE_BLOOD", "RED_CELLS", "PLATELETS", "PLASMA", "CRYOPRECIPITATE"];
const GROUPS = ["A", "B", "AB", "O"];

export default function OrderBloodPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const patientIdFromUrl = searchParams.get("patientId") ?? "";
  const user = useAuthStore((s) => s.user);
  const create = useCreateBloodOrder();
  const [patientCpid, setPatientCpid] = useState(patientIdFromUrl);
  const [bloodGroup, setBloodGroup] = useState("O");
  const [componentType, setComponentType] = useState("RED_CELLS");
  const [units, setUnits] = useState("1");

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    create.mutate(
      {
        patient_cpid: patientCpid,
        blood_group: bloodGroup,
        component_type: componentType,
        units_requested: Number(units) || 1,
        ordering_provider: user?.providerId ?? user?.id,
      },
      {
        onSuccess: (order) => {
          const id = String((order as { orderId?: string }).orderId ?? (order as { id?: string }).id ?? "");
          if (id) router.push(`/madi/orders/${id}`);
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell title="Order Blood" subtitle="Clinical blood product request" icon={<ClipboardList className="h-6 w-6" />}>
        <form onSubmit={handleSubmit} className="max-w-lg space-y-4 rounded-2xl border border-border bg-card p-6">
          <label className="block text-sm font-medium text-foreground">
            Patient CPID
            <input required value={patientCpid} onChange={(e) => setPatientCpid(e.target.value)} className="mt-1 w-full rounded-xl border border-border px-3 py-2 text-sm font-mono" placeholder="Patient canonical ID" />
          </label>
          <div className="grid grid-cols-2 gap-3">
            <label className="block text-sm font-medium text-foreground">
              Blood group
              <select value={bloodGroup} onChange={(e) => setBloodGroup(e.target.value)} className="mt-1 w-full rounded-xl border border-border px-3 py-2 text-sm bg-card">
                {GROUPS.map((g) => <option key={g} value={g}>{g}</option>)}
              </select>
            </label>
            <label className="block text-sm font-medium text-foreground">
              Component
              <select value={componentType} onChange={(e) => setComponentType(e.target.value)} className="mt-1 w-full rounded-xl border border-border px-3 py-2 text-sm bg-card">
                {COMPONENTS.map((c) => <option key={c} value={c}>{c.replace(/_/g, " ")}</option>)}
              </select>
            </label>
          </div>
          <label className="block text-sm font-medium text-foreground">
            Units requested
            <input type="number" min={1} value={units} onChange={(e) => setUnits(e.target.value)} className="mt-1 w-full rounded-xl border border-border px-3 py-2 text-sm" />
          </label>
          <button type="submit" disabled={create.isPending} className="inline-flex items-center gap-2 rounded-xl bg-rose-600 px-4 py-2 text-sm font-medium text-white hover:bg-rose-700 disabled:opacity-50">
            {create.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            Create order
          </button>
        </form>
        <p className="mt-4 text-xs text-muted-foreground">
          After creation, submit samples, crossmatch, reserve and issue from the{" "}
          <Link href="/madi/blood-bank" className="text-rose-600">blood bank workspace</Link>.
        </p>
      </PageShell>
    </AppLayout>
  );
}
