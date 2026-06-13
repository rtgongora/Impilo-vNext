"use client";

/**
 * EncounterCart -- Multi-select clinical ordering interface.
 *
 * Slide-out panel with tabbed browsing (Investigations, Medications, Nursing,
 * Order Sets), a running cart summary, and a review-before-submit flow.
 */

import { useState, useMemo, useCallback } from "react";
import {
  Search,
  ShoppingCart,
  X,
  ChevronRight,
  ArrowLeft,
  Send,
  Plus,
  FlaskConical,
  Pill,
  HeartPulse,
  Package,
} from "lucide-react";
import {
  INVESTIGATION_CATALOG,
  getCommonInvestigations,
  getClinicalDomains,
  getInvestigationsByDomain,
} from "@/data/investigationCatalog";
import type { ClinicalDomain, Investigation } from "@/data/investigationCatalog";
import type { OrderSetItem } from "@/data/orderSets";
import { CartItem } from "./CartItem";
import type { CartItemData, CartItemType, CartPriority } from "./CartItem";
import { OrderSetPicker } from "./OrderSetPicker";

// ── Helpers ────────────────────────────────────────────────────────

let _nextId = 1;
function nextId(): string {
  return `cart-${_nextId++}`;
}

function mapInvestigationToCart(inv: Investigation): CartItemData {
  return {
    id: nextId(),
    code: inv.code,
    name: inv.name,
    type: inv.type === "IMAGING" ? "IMAGING" : "LAB",
    priority: "ROUTINE",
    removable: true,
  };
}

function mapOrderSetItemToCart(item: OrderSetItem): CartItemData {
  const typeMap: Record<string, CartItemType> = {
    INVESTIGATION: "LAB",
    MEDICATION: "MED",
    NURSING: "NURSING",
    DIET: "NURSING",
    ACTIVITY: "NURSING",
    REFERRAL: "NURSING",
  };
  return {
    id: nextId(),
    code: item.code,
    name: item.name,
    type: typeMap[item.type] ?? "LAB",
    priority: item.priority,
    dose: item.dose,
    frequency: item.frequency,
    route: item.route,
    duration: item.duration,
    notes: item.notes,
    removable: item.removable,
  };
}

// ── Tab definitions ────────────────────────────────────────────────

type TabId = "investigations" | "medications" | "nursing" | "order-sets";

const TABS: { id: TabId; label: string; icon: typeof FlaskConical }[] = [
  { id: "investigations", label: "Investigations", icon: FlaskConical },
  { id: "medications", label: "Medications", icon: Pill },
  { id: "nursing", label: "Nursing", icon: HeartPulse },
  { id: "order-sets", label: "Order Sets", icon: Package },
];

// ── Main component ─────────────────────────────────────────────────

interface EncounterCartProps {
  open: boolean;
  onClose: () => void;
  onSubmit?: (items: CartItemData[]) => void;
}

export function EncounterCart({ open, onClose, onSubmit }: EncounterCartProps) {
  const [activeTab, setActiveTab] = useState<TabId>("investigations");
  const [cartItems, setCartItems] = useState<CartItemData[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [categoryFilter, setCategoryFilter] = useState<ClinicalDomain | "ALL">("ALL");
  const [isReviewing, setIsReviewing] = useState(false);

  // ── Cart operations ──────────────────────────────────────────────

  const addToCart = useCallback((item: CartItemData) => {
    setCartItems((prev) => {
      if (prev.some((existing) => existing.code === item.code && existing.type === item.type)) return prev;
      return [...prev, item];
    });
  }, []);

  const removeFromCart = useCallback((id: string) => {
    setCartItems((prev) => prev.filter((i) => i.id !== id));
  }, []);

  const updatePriority = useCallback((id: string, priority: CartPriority) => {
    setCartItems((prev) => prev.map((i) => (i.id === id ? { ...i, priority } : i)));
  }, []);

  const updateNotes = useCallback((id: string, notes: string) => {
    setCartItems((prev) => prev.map((i) => (i.id === id ? { ...i, notes } : i)));
  }, []);

  const updateDose = useCallback((id: string, dose: string) => {
    setCartItems((prev) => prev.map((i) => (i.id === id ? { ...i, dose } : i)));
  }, []);

  const updateFrequency = useCallback((id: string, frequency: string) => {
    setCartItems((prev) => prev.map((i) => (i.id === id ? { ...i, frequency } : i)));
  }, []);

  const addOrderSet = useCallback((items: OrderSetItem[]) => {
    const mapped = items.map(mapOrderSetItemToCart);
    setCartItems((prev) => {
      const existingCodes = new Set(prev.map((i) => `${i.type}:${i.code}`));
      const newItems = mapped.filter((m) => !existingCodes.has(`${m.type}:${m.code}`));
      return [...prev, ...newItems];
    });
  }, []);

  const handleSubmit = useCallback(() => {
    onSubmit?.(cartItems);
    setCartItems([]);
    setIsReviewing(false);
    onClose();
  }, [cartItems, onSubmit, onClose]);

  // ── Filtered investigations ──────────────────────────────────────

  const categories = useMemo(() => getClinicalDomains(), []);
  const commonInvestigations = useMemo(() => getCommonInvestigations(), []);

  const filteredInvestigations = useMemo(() => {
    let list =
      categoryFilter === "ALL"
        ? INVESTIGATION_CATALOG
        : getInvestigationsByDomain(categoryFilter);
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      list = list.filter(
        (inv: Investigation) =>
          inv.name.toLowerCase().includes(q) ||
          inv.code.toLowerCase().includes(q) ||
          inv.shortName.toLowerCase().includes(q)
      );
    }
    return list;
  }, [categoryFilter, searchQuery]);

  const isInCart = useCallback(
    (code: string) => cartItems.some((i) => i.code === code),
    [cartItems]
  );

  // ── Render nothing when closed ───────────────────────────────────

  if (!open) return null;

  // ── Review view ──────────────────────────────────────────────────

  if (isReviewing) {
    return (
      <div className="fixed inset-y-0 right-0 z-50 flex w-full max-w-lg flex-col border-l border-border bg-background shadow-xl">
        {/* Header */}
        <div className="flex items-center gap-3 border-b border-border bg-card px-4 py-3">
          <button onClick={() => setIsReviewing(false)} className="rounded p-1 hover:bg-neutral-100">
            <ArrowLeft className="h-4 w-4 text-muted-foreground" />
          </button>
          <h2 className="flex-1 text-sm font-semibold text-foreground">Review Orders ({cartItems.length})</h2>
          <button onClick={onClose} className="rounded p-1 hover:bg-neutral-100">
            <X className="h-4 w-4 text-muted-foreground" />
          </button>
        </div>

        {/* Editable items */}
        <div className="flex-1 overflow-y-auto px-4 py-3">
          <div className="flex flex-col gap-3">
            {cartItems.map((item) => (
              <div key={item.id} className="rounded-lg border border-border bg-card p-3">
                <div className="mb-2 flex items-center justify-between">
                  <span className="text-sm font-medium text-foreground">{item.name}</span>
                  <span className="text-[10px] text-muted-foreground">{item.code}</span>
                </div>
                {(item.type === "MED") && (
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="text-[10px] font-medium text-muted-foreground">Dose</label>
                      <input
                        type="text"
                        value={item.dose ?? ""}
                        onChange={(e) => updateDose(item.id, e.target.value)}
                        className="w-full rounded border border-border px-2 py-1 text-xs focus:border-primary/25 focus:outline-none"
                        placeholder="e.g. 500mg"
                      />
                    </div>
                    <div>
                      <label className="text-[10px] font-medium text-muted-foreground">Frequency</label>
                      <input
                        type="text"
                        value={item.frequency ?? ""}
                        onChange={(e) => updateFrequency(item.id, e.target.value)}
                        className="w-full rounded border border-border px-2 py-1 text-xs focus:border-primary/25 focus:outline-none"
                        placeholder="e.g. TDS"
                      />
                    </div>
                  </div>
                )}
                <div className="mt-2">
                  <label className="text-[10px] font-medium text-muted-foreground">Clinical Notes</label>
                  <input
                    type="text"
                    value={item.notes ?? ""}
                    onChange={(e) => updateNotes(item.id, e.target.value)}
                    className="w-full rounded border border-border px-2 py-1 text-xs focus:border-primary/25 focus:outline-none"
                    placeholder="Add notes..."
                  />
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Submit */}
        <div className="border-t border-border bg-card px-4 py-3">
          <button
            onClick={handleSubmit}
            disabled={cartItems.length === 0}
            className="flex w-full items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-medium text-white hover:bg-primary-hover disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors"
          >
            <Send className="h-4 w-4" />
            Submit {cartItems.length} Order{cartItems.length !== 1 ? "s" : ""}
          </button>
        </div>
      </div>
    );
  }

  // ── Main cart view ───────────────────────────────────────────────

  return (
    <div className="fixed inset-y-0 right-0 z-50 flex w-full max-w-lg flex-col border-l border-border bg-background shadow-xl">
      {/* Header */}
      <div className="flex items-center gap-3 border-b border-border bg-card px-4 py-3">
        <ShoppingCart className="h-5 w-5 text-primary" />
        <h2 className="flex-1 text-sm font-semibold text-foreground">Encounter Cart</h2>
        {cartItems.length > 0 && (
          <span className="rounded-full bg-primary-soft px-2 py-0.5 text-xs font-semibold text-primary">
            {cartItems.length}
          </span>
        )}
        <button onClick={onClose} className="rounded p-1 hover:bg-neutral-100">
          <X className="h-4 w-4 text-muted-foreground" />
        </button>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-border bg-card px-2">
        {TABS.map((tab) => {
          const Icon = tab.icon;
          return (
            <button
              key={tab.id}
              onClick={() => { setActiveTab(tab.id); setSearchQuery(""); setCategoryFilter("ALL"); }}
              className={`flex items-center gap-1.5 border-b-2 px-3 py-2.5 text-xs font-medium transition-colors ${
                activeTab === tab.id
                  ? "border-impilo-500 text-primary"
                  : "border-transparent text-muted-foreground hover:text-foreground"
              }`}
            >
              <Icon className="h-3.5 w-3.5" />
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* Tab content */}
      <div className="flex-1 overflow-y-auto">
        {/* ── Investigations tab ────────────────────────────────────── */}
        {activeTab === "investigations" && (
          <div className="flex flex-col gap-3 p-4">
            {/* Search */}
            <div className="relative">
              <Search className="absolute left-2.5 top-2 h-4 w-4 text-muted-foreground" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search investigations..."
                className="w-full rounded-lg border border-border bg-card py-2 pl-9 pr-3 text-sm placeholder:text-muted-foreground focus:border-primary/25 focus:outline-none"
              />
            </div>

            {/* Category filter */}
            <div className="flex flex-wrap gap-1.5">
              <button
                onClick={() => setCategoryFilter("ALL")}
                className={`rounded-full px-2.5 py-1 text-[11px] font-medium transition-colors ${
                  categoryFilter === "ALL" ? "bg-primary text-white" : "bg-neutral-100 text-muted-foreground hover:bg-neutral-100"
                }`}
              >
                All
              </button>
              {categories.map((cat) => (
                <button
                  key={cat}
                  onClick={() => setCategoryFilter(cat)}
                  className={`rounded-full px-2.5 py-1 text-[11px] font-medium transition-colors ${
                    categoryFilter === cat ? "bg-primary text-white" : "bg-neutral-100 text-muted-foreground hover:bg-neutral-100"
                  }`}
                >
                  {cat.charAt(0) + cat.slice(1).toLowerCase()}
                </button>
              ))}
            </div>

            {/* Common picks (only when no search and ALL category) */}
            {!searchQuery && categoryFilter === "ALL" && (
              <div>
                <h3 className="mb-1.5 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                  Common Investigations
                </h3>
                <div className="flex flex-wrap gap-1.5">
                  {commonInvestigations.map((inv) => (
                    <button
                      key={inv.code}
                      onClick={() => addToCart(mapInvestigationToCart(inv))}
                      disabled={isInCart(inv.code)}
                      className={`inline-flex items-center gap-1 rounded-full border px-2.5 py-1 text-xs font-medium transition-colors ${
                        isInCart(inv.code)
                          ? "border-green-200 bg-green-50 text-green-600 cursor-default"
                          : "border-border bg-card text-foreground hover:border-primary/25 hover:bg-primary-soft"
                      }`}
                    >
                      {isInCart(inv.code) ? "✓" : <Plus className="h-3 w-3" />}
                      {inv.shortName}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Full list */}
            <div className="flex flex-col gap-1.5">
              {filteredInvestigations.map((inv) => (
                <div
                  key={inv.code}
                  className="flex items-center gap-2 rounded-lg border border-border bg-card px-3 py-2"
                >
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-foreground truncate">{inv.name}</p>
                    <p className="text-[11px] text-muted-foreground">
                      {inv.code} &middot; {inv.domain.toLowerCase()} &middot; ~{inv.turnaroundHours}h TAT
                      {inv.isPanel && " · panel"}
                    </p>
                  </div>
                  <button
                    onClick={() => addToCart(mapInvestigationToCart(inv))}
                    disabled={isInCart(inv.code)}
                    className={`flex-shrink-0 rounded-md px-2.5 py-1 text-xs font-medium transition-colors ${
                      isInCart(inv.code)
                        ? "bg-green-50 text-green-600 cursor-default"
                        : "bg-primary-soft text-primary hover:bg-primary-soft"
                    }`}
                  >
                    {isInCart(inv.code) ? "Added" : "+ Add"}
                  </button>
                </div>
              ))}
              {filteredInvestigations.length === 0 && (
                <p className="py-6 text-center text-sm text-muted-foreground">No investigations match your search.</p>
              )}
            </div>
          </div>
        )}

        {/* ── Medications tab (placeholder browse) ─────────────────── */}
        {activeTab === "medications" && (
          <div className="flex flex-col items-center justify-center gap-2 p-8 text-center">
            <Pill className="h-8 w-8 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">
              Medication search coming soon.
            </p>
            <p className="text-xs text-muted-foreground">
              Use Order Sets tab to add medications from pre-configured protocols.
            </p>
          </div>
        )}

        {/* ── Nursing tab (placeholder browse) ─────────────────────── */}
        {activeTab === "nursing" && (
          <div className="flex flex-col items-center justify-center gap-2 p-8 text-center">
            <HeartPulse className="h-8 w-8 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">
              Nursing order search coming soon.
            </p>
            <p className="text-xs text-muted-foreground">
              Use Order Sets tab to add nursing orders from pre-configured protocols.
            </p>
          </div>
        )}

        {/* ── Order Sets tab ───────────────────────────────────────── */}
        {activeTab === "order-sets" && (
          <div className="p-4">
            <OrderSetPicker onUseSet={addOrderSet} />
          </div>
        )}
      </div>

      {/* ── Cart summary (bottom) ─────────────────────────────────── */}
      {cartItems.length > 0 && (
        <div className="border-t border-border bg-card">
          {/* Compact item list */}
          <div className="max-h-52 overflow-y-auto px-3 py-2">
            <div className="flex flex-col gap-1.5">
              {cartItems.map((item) => (
                <CartItem
                  key={item.id}
                  item={item}
                  onRemove={removeFromCart}
                  onPriorityChange={updatePriority}
                  onNotesChange={updateNotes}
                />
              ))}
            </div>
          </div>

          {/* Review & Submit */}
          <div className="border-t border-border px-4 py-3">
            <button
              onClick={() => setIsReviewing(true)}
              className="flex w-full items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-medium text-white hover:bg-primary-hover transition-colors"
            >
              Review & Submit
              <ChevronRight className="h-4 w-4" />
              <span className="rounded-full bg-primary px-2 py-0.5 text-xs">
                {cartItems.length}
              </span>
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
