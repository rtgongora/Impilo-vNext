"use client";

import { useEffect } from "react";

export interface ShellDockMenuItem {
  id: string;
  label: string;
  destructive?: boolean;
  onSelect: () => void;
}

interface ShellDockContextMenuProps {
  open: boolean;
  x: number;
  y: number;
  items: ShellDockMenuItem[];
  onClose: () => void;
}

/** Lightweight fixed-position context menu for dock pins and task chips. */
export function ShellDockContextMenu({ open, x, y, items, onClose }: ShellDockContextMenuProps) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!open || items.length === 0) return null;

  return (
    <>
      <button
        type="button"
        className="fixed inset-0 z-[10001]"
        aria-label="Close context menu"
        onClick={onClose}
      />
      <div
        role="menu"
        className="fixed z-[10002] min-w-[11rem] rounded-xl border border-border bg-card py-1 shadow-xl"
        style={{ left: x, top: y }}
      >
        {items.map((item) => (
          <button
            key={item.id}
            type="button"
            role="menuitem"
            className={`block w-full px-3 py-2 text-left text-sm hover:bg-neutral-100 ${
              item.destructive ? "text-danger" : "text-foreground"
            }`}
            onClick={() => {
              item.onSelect();
              onClose();
            }}
          >
            {item.label}
          </button>
        ))}
      </div>
    </>
  );
}
