"use client";

/**
 * Sandbox — Health OS §10
 * Test API calls in an isolated sandbox environment.
 * Route: /developer/sandbox | Zone: developer | Guard: role ADMIN
 */

import { FlaskConical, Play, RotateCcw } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function SandboxPage() {
  return (
    <AppLayout>
      <PageShell
        title="Sandbox"
        subtitle="Test API calls in an isolated sandbox environment with synthetic data"
        icon={<FlaskConical className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Controls */}
          <div className="flex items-center gap-3">
            <select className="rounded-lg border border-border px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500">
              <option>TSHEPO — Authorization</option>
              <option>VITO — Identity</option>
              <option>VARAPI — Provider</option>
              <option>TUSO — Facility</option>
              <option>BUTANO — SHR</option>
              <option>Msika — Marketplace</option>
              <option>PCT — Claims</option>
              <option>OROS — Laboratory</option>
            </select>
            <select className="rounded-lg border border-border px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500">
              <option>GET</option>
              <option>POST</option>
              <option>PUT</option>
              <option>PATCH</option>
              <option>DELETE</option>
            </select>
            <input
              type="text"
              placeholder="/api/v1/..."
              className="flex-1 rounded-lg border border-border py-2 px-3 text-sm font-mono focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
            />
            <button className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary transition-colors">
              <Play className="h-4 w-4" />
              Send
            </button>
            <button className="inline-flex items-center gap-2 rounded-lg border border-border px-3 py-2 text-sm text-foreground hover:bg-background transition-colors">
              <RotateCcw className="h-4 w-4" />
              Reset
            </button>
          </div>

          {/* Request/Response panels */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <div className="rounded-lg border border-border bg-card">
              <div className="border-b border-border px-4 py-3">
                <h3 className="text-sm font-semibold text-foreground">Request Body</h3>
              </div>
              <div className="p-4">
                <textarea
                  rows={12}
                  placeholder='{\n  "key": "value"\n}'
                  className="w-full rounded-lg border border-border bg-background p-3 text-sm font-mono focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                />
              </div>
            </div>
            <div className="rounded-lg border border-border bg-card">
              <div className="border-b border-border px-4 py-3">
                <h3 className="text-sm font-semibold text-foreground">Response</h3>
              </div>
              <div className="p-4">
                <div className="rounded-lg bg-background border border-border p-3 min-h-[288px] text-sm font-mono text-muted-foreground">
                  Send a request to see the response...
                </div>
              </div>
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
