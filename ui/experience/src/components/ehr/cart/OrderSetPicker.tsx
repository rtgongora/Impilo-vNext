"use client";

/**
 * OrderSetPicker -- Browse and select pre-configured clinical order sets.
 * Shows category tabs, set cards with item-count badges, and expand-to-preview.
 */

import { useState } from "react";
import { ChevronDown, ChevronUp, Package, Plus } from "lucide-react";
import { ORDER_SETS, getOrderSetCategories } from "@/data/orderSets";
import type { OrderSet, OrderSetItem } from "@/data/orderSets";

const TYPE_DOT: Record<string, string> = {
  INVESTIGATION: "bg-impilo-500",
  MEDICATION: "bg-green-500",
  NURSING: "bg-amber-500",
  DIET: "bg-pink-400",
  ACTIVITY: "bg-teal-400",
  REFERRAL: "bg-indigo-400",
};

function itemCountsByType(items: OrderSetItem[]): Record<string, number> {
  const counts: Record<string, number> = {};
  items.forEach((i) => {
    counts[i.type] = (counts[i.type] ?? 0) + 1;
  });
  return counts;
}

interface OrderSetPickerProps {
  onUseSet: (items: OrderSetItem[]) => void;
}

export function OrderSetPicker({ onUseSet }: OrderSetPickerProps) {
  const categories = getOrderSetCategories();
  const [activeCategory, setActiveCategory] = useState<string>(categories[0] ?? "");
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const filtered = activeCategory
    ? ORDER_SETS.filter((os) => os.category === activeCategory)
    : ORDER_SETS;

  return (
    <div className="flex flex-col gap-3">
      {/* Category tabs */}
      <div className="flex flex-wrap gap-1.5">
        {categories.map((cat) => (
          <button
            key={cat}
            onClick={() => setActiveCategory(cat)}
            className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
              activeCategory === cat
                ? "bg-impilo-500 text-white"
                : "bg-gray-100 text-gray-600 hover:bg-gray-200"
            }`}
          >
            {cat}
          </button>
        ))}
      </div>

      {/* Order set cards */}
      <div className="flex flex-col gap-2">
        {filtered.map((os: OrderSet) => {
          const counts = itemCountsByType(os.items);
          const isExpanded = expandedId === os.id;

          return (
            <div
              key={os.id}
              className="rounded-lg border border-gray-200 bg-white transition-shadow hover:shadow-sm"
            >
              {/* Card header */}
              <div className="flex items-start gap-3 px-4 py-3">
                <Package className="mt-0.5 h-5 w-5 flex-shrink-0 text-impilo-400" />
                <div className="min-w-0 flex-1">
                  <h4 className="text-sm font-semibold text-gray-900">{os.name}</h4>
                  <p className="text-xs text-gray-500">{os.description}</p>
                  {/* Item type badges */}
                  <div className="mt-1.5 flex flex-wrap gap-1.5">
                    {Object.entries(counts).map(([type, count]) => (
                      <span
                        key={type}
                        className="inline-flex items-center gap-1 rounded-full bg-gray-50 px-2 py-0.5 text-[10px] text-gray-600"
                      >
                        <span className={`inline-block h-1.5 w-1.5 rounded-full ${TYPE_DOT[type] ?? "bg-gray-400"}`} />
                        {count} {type.toLowerCase()}
                      </span>
                    ))}
                  </div>
                </div>

                {/* Actions */}
                <div className="flex items-center gap-1.5">
                  <button
                    onClick={() => setExpandedId(isExpanded ? null : os.id)}
                    className="rounded p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600"
                    title={isExpanded ? "Collapse" : "Preview items"}
                  >
                    {isExpanded ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                  </button>
                  <button
                    onClick={() => onUseSet(os.items)}
                    className="inline-flex items-center gap-1 rounded-md bg-impilo-500 px-3 py-1.5 text-xs font-medium text-white hover:bg-impilo-600 transition-colors"
                  >
                    <Plus className="h-3 w-3" />
                    Use This Set
                  </button>
                </div>
              </div>

              {/* Expandable preview */}
              {isExpanded && (
                <div className="border-t border-gray-100 px-4 py-2">
                  <div className="grid grid-cols-1 gap-1">
                    {os.items.map((item, idx) => (
                      <div key={`${item.code}-${idx}`} className="flex items-center gap-2 text-xs text-gray-600">
                        <span className={`inline-block h-1.5 w-1.5 rounded-full ${TYPE_DOT[item.type] ?? "bg-gray-400"}`} />
                        <span className="flex-1">{item.name}</span>
                        {item.dose && <span className="text-gray-400">{item.dose} {item.route}</span>}
                        <span
                          className={`rounded-full px-1.5 py-0.5 text-[9px] font-medium ${
                            item.priority === "STAT"
                              ? "bg-red-50 text-red-600"
                              : item.priority === "URGENT"
                              ? "bg-orange-50 text-orange-600"
                              : "bg-gray-50 text-gray-500"
                          }`}
                        >
                          {item.priority}
                        </span>
                        {!item.removable && (
                          <span className="text-[9px] text-red-400" title="Required — cannot be removed">REQ</span>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
