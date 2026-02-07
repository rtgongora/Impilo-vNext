"use client";

import React from "react";
import Link from "next/link";

/**
 * VITO Registry Dashboard — main overview page.
 * Shows key metrics and navigation to sub-pages.
 */
export default function VitoDashboardPage() {
  return (
    <div className="max-w-7xl mx-auto px-6 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-semibold text-neutral-900">
          VITO — Client Identity Registry
        </h1>
        <p className="text-sm text-neutral-500 mt-1">
          Sovereign Identity Node: WHO-compliant registry with SMART Card management
        </p>
      </div>

      {/* Quick nav cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
        <Link href="/vito/match-queue" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Identity Resolution</h3>
            <p className="text-2xl font-semibold text-neutral-900 mt-2">Match Queue</p>
            <p className="text-xs text-neutral-500 mt-1">
              Review and resolve pending identity matches
            </p>
          </div>
        </Link>

        <Link href="/vito/cards" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Card Management</h3>
            <p className="text-2xl font-semibold text-neutral-900 mt-2">SMART Cards</p>
            <p className="text-xs text-neutral-500 mt-1">
              Print, activate, and manage card lifecycle
            </p>
          </div>
        </Link>

        <Link href="/vito/config" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Configuration</h3>
            <p className="text-2xl font-semibold text-neutral-900 mt-2">Registry Mode</p>
            <p className="text-xs text-neutral-500 mt-1">
              Toggle OpenCR vs Standalone mode
            </p>
          </div>
        </Link>
      </div>

      {/* Client registry overview */}
      <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6">
        <h2 className="text-base font-semibold text-neutral-900 mb-4">
          Recent Registrations
        </h2>
        <p className="text-sm text-neutral-500">
          Client list and search functionality will be rendered here.
          Connect to VITO API: <code className="bg-neutral-100 px-1 py-0.5 rounded text-xs">/v1/clients</code>
        </p>
      </div>
    </div>
  );
}
