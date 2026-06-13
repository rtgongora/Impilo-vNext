"use client";

/**
 * Client Registration — Health OS §10
 * Register and manage API clients and credentials.
 * Route: /developer/clients | Zone: developer | Guard: role ADMIN
 */

import { Key, Plus, Search, Shield } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function ClientsPage() {
  return (
    <AppLayout>
      <PageShell
        title="Client Registration"
        subtitle="Register and manage API clients, credentials, and access scopes"
        icon={<Key className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Actions bar */}
          <div className="flex items-center justify-between">
            <div className="relative w-72">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <input
                type="text"
                placeholder="Search clients..."
                className="w-full rounded-lg border border-border py-2 pl-10 pr-4 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              />
            </div>
            <button className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary transition-colors">
              <Plus className="h-4 w-4" />
              Register Client
            </button>
          </div>

          {/* Empty state */}
          <div className="rounded-lg border border-dashed border-border bg-background p-12 text-center">
            <Shield className="mx-auto h-12 w-12 text-muted-foreground" />
            <h3 className="mt-4 text-sm font-semibold text-foreground">No registered clients</h3>
            <p className="mt-2 text-sm text-muted-foreground">
              Register an API client to obtain credentials for programmatic access to Health OS services.
            </p>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
