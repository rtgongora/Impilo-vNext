"use client";

import type { LucideIcon } from "lucide-react";
import * as Icons from "lucide-react";

export function ShellIcon({ name, className }: { name: string; className?: string }) {
  const Cmp =
    (Icons as unknown as Record<string, LucideIcon>)[name] ?? Icons.AppWindow;
  return <Cmp className={className} />;
}
