"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Package, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useBloodBankStock } from "@/hooks/queries/useMadi";
import { getStoredBloodBankId } from "@/lib/madi-session";

export default function BloodBankStockPage() {
  const facility = useFacilityStore((s) => s.facility);
  const [bloodBankId, setBloodBankId] = useState("");

  useEffect(() => {
    if (facility?.id) setBloodBankId(getStoredBloodBankId(facility.id));
  }, [facility?.id]);

  const { data, isPending, isError } = useBloodBankStock(bloodBankId || undefined);
  const rows = data ?? [];

  return (
    <AppLayout>
      <PageShell title="Blood Stock" subtitle="Available inventory by group and component" icon={<Package className="h-6 w-6" />}>
        {!bloodBankId ? (
          <div className="rounded-2xl border border-dashed border-border p-8 text-center">
            <Link href="/madi/blood-bank" className="text-sm font-medium text-rose-600">Configure blood bank →</Link>
          </div>
        ) : (
          <>
            {isPending && (
              <div className="flex items-center gap-2 text-muted-foreground">
                <Loader2 className="h-5 w-5 animate-spin" /> Loading stock…
              </div>
            )}
            {isError && (
              <div className="rounded-xl border border-danger/28 bg-danger-soft p-4 text-sm text-rose-800">Stock could not be loaded.</div>
            )}
            {!isPending && !isError && rows.length === 0 && (
              <div className="rounded-2xl border border-dashed border-border p-12 text-center">
                <Package className="mx-auto h-10 w-10 text-muted-foreground" />
                <p className="mt-3 text-sm text-muted-foreground">No stock balances recorded yet.</p>
                <Link href="/madi/processing" className="mt-3 inline-block text-sm text-rose-600">Receive units via processing →</Link>
              </div>
            )}
            {rows.length > 0 && (
              <table className="w-full text-sm rounded-2xl border border-border bg-card overflow-hidden">
                <thead className="bg-background text-xs uppercase text-muted-foreground">
                  <tr>
                    <th className="px-4 py-3 text-left">Item</th>
                    <th className="px-4 py-3 text-left">Group</th>
                    <th className="px-4 py-3 text-left">Component</th>
                    <th className="px-4 py-3 text-right">Available</th>
                    <th className="px-4 py-3 text-right">Reserved</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {rows.map((row, i) => (
                    <tr key={row.id ?? i}>
                      <td className="px-4 py-3 font-mono text-xs">{row.inventoryItemCode ?? "—"}</td>
                      <td className="px-4 py-3">{row.bloodGroup ?? "—"}</td>
                      <td className="px-4 py-3">{row.componentType ?? "—"}</td>
                      <td className="px-4 py-3 text-right">{row.quantityAvailable ?? 0}</td>
                      <td className="px-4 py-3 text-right">{row.quantityReserved ?? 0}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </>
        )}
      </PageShell>
    </AppLayout>
  );
}
