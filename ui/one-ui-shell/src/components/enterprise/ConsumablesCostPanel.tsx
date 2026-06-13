"use client";

import Link from "next/link";
import { useMemo } from "react";
import { Loader2, Package } from "lucide-react";
import { useCostaIntelTariffLists } from "@/hooks/queries/useCostaIntel";
import { useInventoryOnHand } from "@/hooks/queries/useInventory";
import { useFacilityStore } from "@/hooks/useFacilityStore";

function extractOnHandRows(payload: unknown): Array<{ category: string; code: string; name: string; qty: number; unit: string }> {
  const raw = Array.isArray(payload)
    ? payload
    : payload && typeof payload === "object"
      ? (Array.isArray((payload as { data?: unknown }).data)
          ? (payload as { data: unknown[] }).data
          : Array.isArray((payload as { items?: unknown }).items)
            ? (payload as { items: unknown[] }).items
            : [])
      : [];
  return raw.map((row) => {
    const r = row as Record<string, unknown>;
    const attrs = (r.attributes as Record<string, unknown> | undefined) ?? r;
    return {
      category: String(attrs.category ?? attrs.item_category ?? "Consumables"),
      code: String(attrs.itemCode ?? attrs.item_code ?? attrs.productCode ?? attrs.sku ?? "—"),
      name: String(attrs.itemName ?? attrs.name ?? attrs.productName ?? "Item"),
      qty: Number(attrs.onHand ?? attrs.quantity ?? attrs.on_hand ?? attrs.quantityOnHand ?? 0),
      unit: String(attrs.unit ?? "ea"),
    };
  });
}

function matchTariffForItem(
  tariffs: Array<{ name?: string | null; externalCode?: string | null }>,
  code: string,
  name: string,
) {
  const codeLower = code.toLowerCase();
  const nameLower = name.toLowerCase();
  return tariffs.find((t) => {
    const ext = (t.externalCode ?? "").toLowerCase();
    const label = (t.name ?? "").toLowerCase();
    return (ext && (ext === codeLower || codeLower.includes(ext) || ext.includes(codeLower)))
      || (label && (label.includes(nameLower) || nameLower.includes(label)));
  });
}

export function ConsumablesCostPanel() {
  const facility = useFacilityStore((s) => s.facility);
  const onHandQ = useInventoryOnHand(facility?.id, { size: 100 });
  const tariffsQ = useCostaIntelTariffLists();

  const onHandRows = useMemo(() => extractOnHandRows(onHandQ.data), [onHandQ.data]);
  const consumableRows = useMemo(
    () =>
      onHandRows.filter((row) => {
        const cat = row.category.toLowerCase();
        return cat.includes("consum") || cat.includes("suppl") || cat.includes("commodit") || cat === "supplies";
      }),
    [onHandRows],
  );
  const tariffs = tariffsQ.data ?? [];

  const grouped = useMemo(() => {
    const map = new Map<string, typeof consumableRows>();
    for (const row of consumableRows) {
      const list = map.get(row.category) ?? [];
      list.push(row);
      map.set(row.category, list);
    }
    return [...map.entries()];
  }, [consumableRows]);

  return (
    <section
      className="rounded-2xl border border-info/25 bg-card p-5 shadow-sm"
      data-testid="consumables-cost-panel"
    >
      <div className="flex items-start gap-3">
        <Package className="mt-0.5 h-5 w-5 text-indigo-600" />
        <div className="flex-1 min-w-0">
          <h2 className="text-sm font-semibold text-foreground">Consumables cost alignment</h2>
          <p className="mt-1 text-xs text-muted-foreground">
            Maps inventory on-hand consumable categories to COSTA tariff lists (
            <code className="text-[10px]">GET /internal/v1/finance/costa-intel/tariff-lists</code>
            ). Charge capture stays in encounters; this panel is reference-only for enterprise charge sheets.
          </p>

          {!facility ? (
            <p className="mt-3 text-sm text-warning-foreground">Select a facility to load on-hand consumables.</p>
          ) : onHandQ.isLoading || tariffsQ.isLoading ? (
            <p className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading inventory and tariffs…
            </p>
          ) : onHandQ.isError ? (
            <p className="mt-3 text-sm text-danger">Could not load inventory on-hand from the BFF.</p>
          ) : grouped.length === 0 ? (
            <p className="mt-3 text-sm text-muted-foreground">
              No consumable/supply category rows on hand.{" "}
              <Link href="/inventory/stock-management" className="font-medium text-primary-hover underline">
                Stock management
              </Link>
            </p>
          ) : (
            <div className="mt-4 space-y-4">
              {grouped.map(([category, rows]) => (
                <div key={category} className="rounded-lg border border-border bg-background/80 p-3">
                  <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{category}</h3>
                  <ul className="mt-2 space-y-2 text-xs">
                    {rows.slice(0, 6).map((row) => {
                      const tariff = matchTariffForItem(tariffs, row.code, row.name);
                      return (
                        <li
                          key={`${row.code}-${row.name}`}
                          className="flex flex-wrap items-center justify-between gap-2 rounded-md bg-card px-2 py-1.5 border border-border"
                        >
                          <span className="font-medium text-foreground">
                            {row.name}{" "}
                            <span className="font-mono text-[10px] text-muted-foreground">({row.code})</span>
                          </span>
                          <span className="text-muted-foreground">
                            {row.qty} {row.unit}
                            {tariff ? (
                              <span className="ml-2 text-primary-hover">
                                → {tariff.name ?? tariff.externalCode}
                              </span>
                            ) : (
                              <span className="ml-2 text-warning-foreground">No tariff match</span>
                            )}
                          </span>
                        </li>
                      );
                    })}
                  </ul>
                </div>
              ))}
            </div>
          )}

          <div className="mt-4 flex flex-wrap gap-3 text-xs">
            <Link href="/finance/tariffs" className="font-medium text-primary-hover hover:underline">
              COSTA tariff library
            </Link>
            <Link href="/inventory/stock-management" className="font-medium text-primary-hover hover:underline">
              Inventory stock
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}
