"use client";

/**
 * CartItem -- Single row in the Encounter Cart showing an ordered item
 * with type badge, priority selector, optional notes, and remove button.
 */

import { X, FlaskConical, Scan, Pill, HeartPulse } from "lucide-react";
import { useState } from "react";

export type CartItemType = "LAB" | "IMAGING" | "MED" | "NURSING";
export type CartPriority = "STAT" | "URGENT" | "ROUTINE";

export interface CartItemData {
  id: string;
  code: string;
  name: string;
  type: CartItemType;
  priority: CartPriority;
  dose?: string;
  frequency?: string;
  route?: string;
  duration?: string;
  notes?: string;
  removable?: boolean;
}

const TYPE_CONFIG: Record<CartItemType, { label: string; color: string; icon: typeof FlaskConical }> = {
  LAB:     { label: "LAB",     color: "bg-primary-soft text-primary",     icon: FlaskConical },
  IMAGING: { label: "IMAGING", color: "bg-purple-100 text-warning-foreground", icon: Scan },
  MED:     { label: "MED",     color: "bg-green-100 text-green-700",   icon: Pill },
  NURSING: { label: "NURSING", color: "bg-amber-100 text-warning-foreground",   icon: HeartPulse },
};

const PRIORITY_COLORS: Record<CartPriority, string> = {
  STAT:    "bg-red-100 text-danger border-red-300",
  URGENT:  "bg-orange-100 text-orange-700 border-orange-300",
  ROUTINE: "bg-neutral-100 text-muted-foreground border-border",
};

interface CartItemProps {
  item: CartItemData;
  onRemove: (id: string) => void;
  onPriorityChange: (id: string, priority: CartPriority) => void;
  onNotesChange: (id: string, notes: string) => void;
}

export function CartItem({ item, onRemove, onPriorityChange, onNotesChange }: CartItemProps) {
  const [showNotes, setShowNotes] = useState(!!item.notes);
  const cfg = TYPE_CONFIG[item.type];
  const Icon = cfg.icon;

  return (
    <div className="flex flex-col gap-1.5 rounded-lg border border-border bg-card px-3 py-2">
      <div className="flex items-center gap-2">
        {/* Type badge */}
        <span className={`inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase ${cfg.color}`}>
          <Icon className="h-3 w-3" />
          {cfg.label}
        </span>

        {/* Name + code */}
        <span className="flex-1 truncate text-sm font-medium text-foreground">{item.name}</span>
        <span className="text-[10px] text-muted-foreground">{item.code}</span>

        {/* Priority pills */}
        <div className="flex gap-1">
          {(["STAT", "URGENT", "ROUTINE"] as CartPriority[]).map((p) => (
            <button
              key={p}
              onClick={() => onPriorityChange(item.id, p)}
              className={`rounded-full border px-2 py-0.5 text-[10px] font-medium transition-colors ${
                item.priority === p ? PRIORITY_COLORS[p] : "border-transparent bg-background text-muted-foreground hover:bg-neutral-100"
              }`}
            >
              {p}
            </button>
          ))}
        </div>

        {/* Notes toggle */}
        <button
          onClick={() => setShowNotes(!showNotes)}
          className="text-xs text-muted-foreground hover:text-muted-foreground"
          title="Toggle notes"
        >
          {showNotes ? "hide" : "note"}
        </button>

        {/* Remove */}
        <button
          onClick={() => onRemove(item.id)}
          disabled={item.removable === false}
          className="rounded p-0.5 text-muted-foreground hover:bg-danger-soft hover:text-red-500 disabled:cursor-not-allowed disabled:opacity-30"
          title="Remove"
        >
          <X className="h-3.5 w-3.5" />
        </button>
      </div>

      {/* Inline notes */}
      {showNotes && (
        <input
          type="text"
          value={item.notes ?? ""}
          onChange={(e) => onNotesChange(item.id, e.target.value)}
          placeholder="Add clinical notes..."
          className="w-full rounded border border-border bg-background px-2 py-1 text-xs text-foreground placeholder:text-muted-foreground focus:border-primary/25 focus:outline-none"
        />
      )}
    </div>
  );
}
