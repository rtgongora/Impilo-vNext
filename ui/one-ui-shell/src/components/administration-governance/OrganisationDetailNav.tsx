"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const ORG_TABS = [
  { suffix: "", label: "Overview" },
  { suffix: "/users", label: "Users & Representatives" },
  { suffix: "/data-uploads", label: "Data Uploads" },
  { suffix: "/verification", label: "Verification" },
  { suffix: "/data-sharing", label: "Data Sharing" },
  { suffix: "/api-access", label: "API Access" },
  { suffix: "/contracts", label: "Contracts" },
  { suffix: "/audit", label: "Audit" },
] as const;

export function OrganisationDetailNav() {
  const pathname = usePathname();
  const match = pathname.match(/\/organisations\/([^/]+)/);
  const orgId = match?.[1];
  if (!orgId || orgId === "new") return null;

  const base = `/work/administration-governance/organisations/${orgId}`;

  return (
    <nav className="flex flex-wrap gap-2 border-b border-slate-200 pb-3" aria-label="Organisation sections">
      {ORG_TABS.map((tab) => {
        const href = `${base}${tab.suffix}`;
        const active = tab.suffix === "" ? pathname === base : pathname.startsWith(href);
        return (
          <Link
            key={tab.suffix || "overview"}
            href={href}
            className={`rounded-lg px-3 py-1.5 text-sm font-medium transition ${
              active
                ? "bg-indigo-700 text-white"
                : "bg-slate-100 text-slate-700 hover:bg-slate-200"
            }`}
          >
            {tab.label}
          </Link>
        );
      })}
    </nav>
  );
}
