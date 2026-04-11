"use client";

/**
 * Developer Portal — absorbs developer-console sidecar
 * API catalog, client registration, sandbox, federation.
 * Route: /developer | Guard: role ADMIN
 */

import Link from "next/link";
import { Code2, BookOpen, Key, FlaskConical } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const SECTIONS = [
  { href: "/developer/api-catalog", label: "API Catalog", description: "Browse and explore Health OS API endpoints", Icon: BookOpen },
  { href: "/developer/clients", label: "Client Registration", description: "Register and manage API clients and credentials", Icon: Key },
  { href: "/developer/sandbox", label: "Sandbox", description: "Test API calls in an isolated sandbox environment", Icon: FlaskConical },
];

export default function DeveloperPage() {
  return (
    <AppLayout>
      <PageShell title="Developer Portal" subtitle="Health OS §10 — API catalog, client management, and sandbox" icon={<Code2 className="h-6 w-6" />}>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {SECTIONS.map(({ href, label, description, Icon }) => (
            <Link key={href} href={href} className="rounded-lg border border-gray-200 bg-white p-5 hover:border-blue-400 hover:shadow-sm transition-all">
              <div className="flex items-center gap-3 mb-2">
                <div className="rounded-lg bg-indigo-50 p-2"><Icon className="h-5 w-5 text-indigo-600" /></div>
                <h3 className="font-semibold text-gray-900">{label}</h3>
              </div>
              <p className="text-sm text-gray-600">{description}</p>
            </Link>
          ))}
        </div>
      </PageShell>
    </AppLayout>
  );
}
