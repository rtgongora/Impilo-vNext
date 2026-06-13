"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const LINKS = [
  { href: "/nhume", label: "Hub" },
  { href: "/nhume/dashboard", label: "Dashboard" },
  { href: "/nhume/deliveries", label: "Deliveries" },
  { href: "/nhume/dispatcher", label: "Dispatcher" },
  { href: "/nhume/map", label: "Map" },
  { href: "/nhume/fleet", label: "Fleet" },
  { href: "/nhume/couriers", label: "Couriers" },
  { href: "/nhume/courier", label: "Driver" },
  { href: "/nhume/policies", label: "Policies" },
  { href: "/nhume/autonomous", label: "Autonomous" },
  { href: "/nhume/analytics", label: "Analytics" },
] as const;

export function NhumeNav() {
  const pathname = usePathname();
  return (
    <nav className="mb-4 flex flex-wrap gap-1 border-b border-border pb-2">
      {LINKS.map((link) => {
        const active = pathname === link.href || (link.href !== "/nhume" && pathname.startsWith(link.href));
        return (
          <Link
            key={link.href}
            href={link.href}
            className={`rounded-full px-3 py-1 text-xs font-medium ${
              active ? "bg-primary text-white" : "bg-neutral-100 text-foreground hover:bg-primary-soft"
            }`}
          >
            {link.label}
          </Link>
        );
      })}
    </nav>
  );
}
