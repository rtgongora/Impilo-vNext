"use client";
import { QueryResultPanel } from "@/components/common/QueryResultPanel";
import { useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { AlertTriangle, Loader2, Package, Search, ShoppingBag, ShoppingCart } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useMarketplaceCatalog } from "@/hooks/queries/useMarketplace";
import { useProductRegistrySearch } from "@/hooks/queries/useProductRegistry";
import { useCommerceValidateCart, useCommerceCreateOrder } from "@/hooks/queries/useCommerceFlow";
import { useFacilityStore } from "@/hooks/useFacilityStore";

const CATEGORIES = ["All", "Logistics", "Biomedical", "Laboratory", "Engineering", "GENERAL"];
type CatalogMode = "facility_marketplace" | "product_registry";

function safeString(value: unknown): string {
  if (typeof value === "string") return value;
  if (typeof value === "number") return String(value);
  return "";
}

function extractRegistryItems(payload: unknown): Array<Record<string, unknown>> {
  if (!payload || typeof payload !== "object") return [];
  // best-effort common shapes:
  // - { items: [...] } or { content: [...] } or { data: [...] }
  const o = payload as Record<string, unknown>;
  const candidates = [o.items, o.content, o.data];
  for (const c of candidates) {
    if (Array.isArray(c)) return c.filter((x): x is Record<string, unknown> => Boolean(x) && typeof x === "object");
  }
  return [];
}

function extractRegistryCode(item: Record<string, unknown>): string {
  const candidates = [
    item.msikaCoreCode,
    item.msika_core_code,
    item.canonicalCode,
    item.canonical_code,
    item.code,
    item.itemId,
    item.item_id,
    item.id,
  ];
  for (const c of candidates) {
    const s = safeString(c);
    if (s) return s;
  }
  return "";
}

function extractRegistryName(item: Record<string, unknown>): string {
  const candidates = [item.displayName, item.display_name, item.name, item.title];
  for (const c of candidates) {
    const s = safeString(c);
    if (s) return s;
  }
  const code = extractRegistryCode(item);
  return code ? `Registry item ${code}` : "Registry item";
}

function tryExtractOrderId(payload: unknown): string | null {
  if (!payload || typeof payload !== "object") return null;
  const o = payload as Record<string, unknown>;
  const candidates = [o.orderId, o.order_id, o.id];
  for (const c of candidates) {
    const s = safeString(c);
    if (s) return s;
  }
  // some upstreams wrap: { data: { orderId } }
  if (o.data && typeof o.data === "object") {
    const d = o.data as Record<string, unknown>;
    const s = safeString(d.orderId ?? d.order_id ?? d.id);
    if (s) return s;
  }
  return null;
}

export default function CatalogPage() {
  const router = useRouter();
  const facility = useFacilityStore((s) => s.facility);
  const [mode, setMode] = useState<CatalogMode>("facility_marketplace");
  const [searchTerm, setSearchTerm] = useState("");
  const [category, setCategory] = useState("All");
  const catalogQuery = useMarketplaceCatalog(searchTerm, category);
  const items = catalogQuery.data ?? [];

  const registryQ = useProductRegistrySearch({ q: searchTerm, size: 20, page: 0 });
  const registryItems = useMemo(() => extractRegistryItems(registryQ.data), [registryQ.data]);
  const [cart, setCart] = useState<Array<{ msikaCoreCode: string; name: string; qty: number; unitPrice: number }>>([]);
  const validateCart = useCommerceValidateCart();
  const createOrder = useCommerceCreateOrder();
  const [orderType, setOrderType] = useState("MSIKA_ORDER");
  const [cartError, setCartError] = useState<string | null>(null);

  function addToCart(item: Record<string, unknown>) {
    const code = extractRegistryCode(item);
    if (!code) return;
    const name = extractRegistryName(item);
    setCart((prev) => {
      const idx = prev.findIndex((x) => x.msikaCoreCode === code);
      if (idx >= 0) {
        const next = [...prev];
        next[idx] = { ...next[idx], qty: next[idx].qty + 1 };
        return next;
      }
      return [...prev, { msikaCoreCode: code, name, qty: 1, unitPrice: 0 }];
    });
  }

  async function runValidate() {
    setCartError(null);
    if (cart.length === 0) {
      setCartError("Add at least one registry item to validate.");
      return;
    }
    if (!facility?.id) {
      setCartError("Select a facility before validating regulated items.");
      return;
    }
    validateCart.mutate({
      items: cart.map((c) => ({ msikaCoreCode: c.msikaCoreCode, qty: c.qty })),
      channel: "facility",
      facilityRef: `tuso:${facility.id}`,
    });
  }

  async function runCreateOrder() {
    setCartError(null);
    if (cart.length === 0) {
      setCartError("Add at least one registry item before creating an order.");
      return;
    }
    if (!facility?.id) {
      setCartError("Select a facility before creating procurement orders.");
      return;
    }
    createOrder.mutate(
      {
        orderType,
        facilityId: facility.id,
        lines: cart.map((c) => ({
          msikaCoreCode: c.msikaCoreCode,
          qty: c.qty,
          unitPrice: c.unitPrice,
        })),
      },
      {
        onSuccess: (res) => {
          const orderId = tryExtractOrderId(res);
          if (orderId) router.push(`/marketplace/orders/${orderId}`);
          else setCartError("Order created, but the response did not include an order id.");
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell title="Marketplace Catalog" subtitle="Real marketplace service and supply listings available across the operating network.">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <div className="inline-flex rounded-lg border border-slate-200 bg-white p-1 text-xs">
            <button
              type="button"
              onClick={() => setMode("facility_marketplace")}
              className={`rounded-md px-3 py-1.5 font-medium ${mode === "facility_marketplace" ? "bg-slate-900 text-white" : "text-slate-700 hover:bg-slate-50"}`}
            >
              Facility marketplace
            </button>
            <button
              type="button"
              onClick={() => setMode("product_registry")}
              className={`rounded-md px-3 py-1.5 font-medium ${mode === "product_registry" ? "bg-slate-900 text-white" : "text-slate-700 hover:bg-slate-50"}`}
            >
              Product registry (MSIKA)
            </button>
          </div>
          <Link href="/finance/commerce-integrations" className="text-xs font-medium text-indigo-700 hover:underline">
            Commerce & payer integration map
          </Link>
        </div>

        <div className="mb-6 flex flex-col gap-3 sm:flex-row">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
            <input
              type="text"
              placeholder={mode === "product_registry" ? "Search MSIKA registry (q)..." : "Search services or supplies..."}
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
              className="w-full rounded-lg border border-gray-300 py-2 pl-10 pr-4 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
            />
          </div>
          {mode === "facility_marketplace" ? (
            <select
              value={category}
              onChange={(event) => setCategory(event.target.value)}
              className="rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
            >
              {CATEGORIES.map((value) => <option key={value} value={value}>{value}</option>)}
            </select>
          ) : null}
        </div>

        {mode === "facility_marketplace" ? (
          catalogQuery.isLoading ? (
            <div className="flex items-center justify-center py-16 text-sm text-gray-500">
              <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading catalog...
            </div>
          ) : items.length === 0 ? (
            <div className="rounded-lg border border-gray-200 bg-white p-12 text-center">
              <ShoppingBag className="mx-auto mb-3 h-10 w-10 text-gray-300" />
              <p className="text-sm text-gray-500">No marketplace listings matched the current search.</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
              {items.map((item) => (
                <div key={item.id} className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                  <div className="mb-3 flex h-32 items-center justify-center rounded-xl bg-slate-100">
                    <Package className="h-10 w-10 text-slate-300" />
                  </div>
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <h3 className="text-sm font-semibold text-slate-900">{item.name}</h3>
                      <p className="mt-1 text-xs text-slate-500">{item.category} � {item.facilityName}</p>
                    </div>
                    <span className={`rounded-full px-2 py-1 text-xs font-semibold ${item.availability === "AVAILABLE" ? "bg-emerald-100 text-emerald-800" : "bg-slate-100 text-slate-700"}`}>
                      {item.availability}
                    </span>
                  </div>
                  <p className="mt-3 text-sm text-slate-600">{item.description}</p>
                  <div className="mt-4 flex items-center justify-between text-sm">
                    <span className="font-semibold text-slate-900">{item.currency} {item.price.toFixed(2)}</span>
                    <span className="text-slate-500">{item.rating ? `${item.rating.toFixed(1)} rating` : "No rating yet"}</span>
                  </div>
                </div>
              ))}
            </div>
          )
        ) : (
          <div className="grid grid-cols-1 gap-6 xl:grid-cols-[1.2fr_0.8fr]">
            <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h2 className="text-lg font-semibold text-slate-900">Registry lookup (real)</h2>
                  <p className="mt-1 text-sm text-slate-600">
                    Backed by <code className="text-xs">GET /internal/v1/product-registry/search</code>.
                  </p>
                </div>
                {registryQ.isLoading ? <Loader2 className="h-4 w-4 animate-spin text-slate-400" /> : null}
              </div>

              {registryQ.isError ? (
                <div className="mt-4 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
                  <div className="flex items-center gap-2 font-medium">
                    <AlertTriangle className="h-4 w-4" /> Could not reach product registry
                  </div>
                  <p className="mt-1 text-xs text-red-800/80">
                    This page requires the MSIKA proxy contract via Experience BFF. See the integration map for contract notes.
                  </p>
                </div>
              ) : registryItems.length === 0 ? (
                <div className="mt-6 rounded-lg border border-gray-200 bg-white p-10 text-center text-sm text-gray-500">
                  <ShoppingBag className="mx-auto mb-3 h-10 w-10 text-gray-300" />
                  No registry items returned for this search.
                </div>
              ) : (
                <div className="mt-4 space-y-3">
                  {registryItems.slice(0, 30).map((it, idx) => {
                    const code = extractRegistryCode(it);
                    const name = extractRegistryName(it);
                    return (
                      <div key={`${code}-${idx}`} className="flex items-start justify-between gap-3 rounded-xl border border-slate-200 p-3">
                        <div className="min-w-0">
                          <p className="text-sm font-semibold text-slate-900 truncate">{name}</p>
                          <p className="mt-0.5 text-xs text-slate-500 break-all">{code || "No code field on this row"}</p>
                        </div>
                        <button
                          type="button"
                          disabled={!code}
                          onClick={() => addToCart(it)}
                          className="inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-800 hover:bg-slate-50 disabled:opacity-50"
                        >
                          <ShoppingCart className="h-3.5 w-3.5" /> Add
                        </button>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <h2 className="text-lg font-semibold text-slate-900">Cart to procurement order (MSIKA Flow)</h2>
              <p className="mt-1 text-sm text-slate-600">
                Validate via <code className="text-xs">POST /internal/v1/commerce/cart/validate</code> and create via{" "}
                <code className="text-xs">POST /internal/v1/commerce/orders</code>.
              </p>

              <div className="mt-4 space-y-3">
                <label className="block text-sm">
                  <span className="mb-1 block font-medium text-slate-700">Order type</span>
                  <input value={orderType} onChange={(e) => setOrderType(e.target.value)} className="w-full rounded-lg border border-slate-300 px-3 py-2" />
                </label>

                {cart.length === 0 ? (
                  <p className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-600">
                    Add registry items to build a cart. Nothing is simulated.
                  </p>
                ) : (
                  <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                    <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Cart lines</p>
                    <div className="mt-2 space-y-2 text-sm">
                      {cart.map((c) => (
                        <div key={c.msikaCoreCode} className="flex items-center justify-between gap-2">
                          <div className="min-w-0">
                            <p className="font-medium text-slate-900 truncate">{c.name}</p>
                            <p className="text-[11px] text-slate-500 break-all">{c.msikaCoreCode} - Qty {c.qty}</p>
                          </div>
                          <button
                            type="button"
                            onClick={() => setCart((prev) => prev.filter((x) => x.msikaCoreCode !== c.msikaCoreCode))}
                            className="text-xs font-medium text-slate-600 hover:text-slate-900"
                          >
                            Remove
                          </button>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {cartError ? <p className="text-sm text-red-700">{cartError}</p> : null}

                <div className="flex flex-wrap items-center justify-end gap-2 pt-1">
                  <button
                    type="button"
                    onClick={() => void runValidate()}
                    disabled={validateCart.isPending}
                    className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-800 hover:bg-slate-50 disabled:opacity-50"
                  >
                    {validateCart.isPending ? "Validating..." : "Validate cart"}
                  </button>
                  <button
                    type="button"
                    onClick={() => void runCreateOrder()}
                    disabled={createOrder.isPending}
                    className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
                  >
                    {createOrder.isPending ? "Creating..." : "Create order"}
                  </button>
                </div>

                {validateCart.data ? (
                  <QueryResultPanel title="Validate Cart" isPending={validateCart.isPending} isLoading={validateCart.isPending} isError={validateCart.isError} error={validateCart.error} data={validateCart.data} />
                ) : null}
              </div>
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
