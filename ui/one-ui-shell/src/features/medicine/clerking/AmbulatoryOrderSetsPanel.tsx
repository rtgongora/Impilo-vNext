"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Package, ShoppingCart } from "lucide-react";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { AMBULATORY_ORDER_SETS_BUILT } from "@/data/orderSets";
import { EncounterCart } from "@/components/ehr/cart/EncounterCart";
import type { CartItemData } from "@/components/ehr/cart/CartItem";

/**
 * Live ambulatory order sets from OROS. Mounts EncounterCart once AMBULATORY_ORDER_SETS_BUILT.
 * Batch place goes through BFF → OROS /v1/order-sets/{id}/place (real orders, not pathway-apply).
 */
export function AmbulatoryOrderSetsPanel({
  patientId,
  encounterId,
}: {
  patientId: string;
  encounterId?: string;
}) {
  const qc = useQueryClient();
  const [cartOpen, setCartOpen] = useState(false);
  const [lastPlace, setLastPlace] = useState<string | null>(null);

  const sets = useQuery({
    queryKey: ["order-sets"],
    queryFn: () => apiClient.get<ApiResponse<Record<string, unknown>[]>>("/internal/v1/order-sets"),
    enabled: AMBULATORY_ORDER_SETS_BUILT,
  });

  const place = useMutation({
    mutationFn: (setId: string) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>(`/internal/v1/order-sets/${setId}/place`, {
        patient_cpid: patientId,
        encounter_ref: encounterId || undefined,
      }),
    onSuccess: (res) => {
      const ids = res.data?.order_ids;
      setLastPlace(Array.isArray(ids) ? ids.join(", ") : "placed");
      void qc.invalidateQueries({ queryKey: ["order-sets"] });
    },
  });

  if (!AMBULATORY_ORDER_SETS_BUILT) {
    return (
      <p className="text-sm text-muted-foreground">
        Ambulatory order sets are not built in this generation.
      </p>
    );
  }

  return (
    <div className="space-y-4" data-testid="ambulatory-order-sets-panel">
      <div className="rounded-lg border border-border bg-card p-4 space-y-3">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <Package className="h-4 w-4" />
            <h3 className="text-sm font-semibold">Ambulatory order sets (OROS)</h3>
          </div>
          <button
            type="button"
            onClick={() => setCartOpen(true)}
            className="inline-flex items-center gap-1 rounded border border-border px-2 py-1 text-xs"
          >
            <ShoppingCart className="h-3 w-3" /> Open cart
          </button>
        </div>
        <p className="text-xs text-muted-foreground">
          Indication-based sets place real OROS orders via batch place — not CKP pathway-apply.
          EncounterCart / OrderSetPicker are mounted for browse-and-add; batch place below is the SoR path.
        </p>
        {sets.isError && (
          <p className="text-sm text-warning">Order sets unavailable — not an empty catalogue claim.</p>
        )}
        <ul className="space-y-2">
          {(sets.data?.data ?? []).map((set) => (
            <li
              key={String(set.set_id)}
              className="flex items-start justify-between gap-2 rounded border border-border p-2"
            >
              <div>
                <p className="text-sm font-medium">{String(set.set_name)}</p>
                <p className="text-xs text-muted-foreground">
                  {String(set.indication ?? "")} — {String(set.description ?? "")}
                </p>
              </div>
              <button
                type="button"
                disabled={place.isPending || !patientId}
                onClick={() => place.mutate(String(set.set_id))}
                className="rounded bg-primary px-3 py-1 text-xs text-white disabled:opacity-50"
              >
                {place.isPending ? <Loader2 className="h-3 w-3 animate-spin" /> : "Place set"}
              </button>
            </li>
          ))}
        </ul>
        {lastPlace && (
          <p className="text-xs text-success" data-testid="order-set-placed">
            Placed order ids: {lastPlace}
          </p>
        )}
      </div>

      <EncounterCart
        open={cartOpen}
        onClose={() => setCartOpen(false)}
        onSubmit={(_items: CartItemData[]) => {
          // Cart browse remains UI composition; SoR batch place is the OROS place endpoint above.
          setCartOpen(false);
        }}
      />
    </div>
  );
}
