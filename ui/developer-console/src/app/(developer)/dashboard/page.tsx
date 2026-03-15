"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { getDashboardStats } from "@/lib/developerApi";
import type { DashboardStats } from "@/types/developer";
import { useDeveloperSession } from "@/stores/sessionStore";
import { EnvironmentBadge } from "@/components/StatusBadge";

const STAT_CARDS: { key: keyof DashboardStats; label: string; color: string }[] = [
  { key: "total_clients", label: "Total Clients", color: "bg-brand-primary" },
  { key: "active_clients", label: "Active Clients", color: "bg-success" },
  { key: "sandbox_clients", label: "Sandbox Clients", color: "bg-info" },
  { key: "active_keys", label: "Active Keys", color: "bg-brand-accent-yellow" },
  { key: "revoked_keys", label: "Revoked Keys", color: "bg-danger" },
  { key: "certifications_passed", label: "Certs Passed", color: "bg-success" },
  { key: "certifications_failed", label: "Certs Failed", color: "bg-brand-accent-red" },
];

export default function DashboardPage() {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const environment = useDeveloperSession((s) => s.environment);

  useEffect(() => {
    async function load() {
      try {
        const data = await getDashboardStats();
        setStats(data);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load dashboard");
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="text-sm text-neutral-500">Loading dashboard...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-danger/10 border border-danger/20 rounded-lg p-4 text-sm text-danger">
        {error}
      </div>
    );
  }

  return (
    <div>
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-neutral-900">Developer Dashboard</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Partner ecosystem overview: clients, keys, certification, and readiness
          </p>
        </div>
        <EnvironmentBadge env={environment} />
      </div>

      {/* Stats grid */}
      {stats && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
          {STAT_CARDS.map(({ key, label, color }) => (
            <div
              key={key}
              className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-5"
            >
              <div className="flex items-center gap-2 mb-2">
                <div className={`w-2.5 h-2.5 rounded-full ${color}`} />
                <span className="text-xs font-medium text-neutral-500 uppercase tracking-wide">
                  {label}
                </span>
              </div>
              <p className="text-2xl font-semibold text-neutral-900">{stats[key]}</p>
            </div>
          ))}
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-5">
            <div className="flex items-center gap-2 mb-2">
              <div className="w-2.5 h-2.5 rounded-full bg-neutral-500" />
              <span className="text-xs font-medium text-neutral-500 uppercase tracking-wide">
                Total Keys
              </span>
            </div>
            <p className="text-2xl font-semibold text-neutral-900">{stats.total_keys}</p>
          </div>
        </div>
      )}

      {/* Quick actions */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
        <Link href="/clients/register" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Onboarding</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Register Client</p>
            <p className="text-xs text-neutral-500 mt-1">Register a new partner or application client</p>
          </div>
        </Link>
        <Link href="/certification" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-success/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Compliance</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Certification</p>
            <p className="text-xs text-neutral-500 mt-1">Run contract checks and view certification history</p>
          </div>
        </Link>
        <Link href="/catalog" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-info/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Discovery</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">API Catalog</p>
            <p className="text-xs text-neutral-500 mt-1">Browse APIs, schemas, and event contracts</p>
          </div>
        </Link>
      </div>

      {/* Onboarding checklist */}
      <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6">
        <h2 className="text-lg font-semibold text-neutral-900 mb-4">Onboarding Checklist</h2>
        <div className="space-y-3">
          {[
            { step: "1", label: "Register your client application", href: "/clients/register", done: stats && stats.total_clients > 0 },
            { step: "2", label: "Issue your first API key", href: "/clients", done: stats && stats.active_keys > 0 },
            { step: "3", label: "Configure sandbox environment", href: "/sandbox", done: stats && stats.sandbox_clients > 0 },
            { step: "4", label: "Run certification checks", href: "/certification", done: stats && stats.certifications_passed > 0 },
            { step: "5", label: "Check federation readiness", href: "/federation", done: false },
          ].map(({ step, label, href, done }) => (
            <Link key={step} href={href} className="flex items-center gap-3 group">
              <div className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-semibold ${
                done ? "bg-success text-white" : "bg-neutral-100 text-neutral-500 group-hover:bg-brand-primary/10 group-hover:text-brand-primary"
              }`}>
                {done ? (
                  <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                  </svg>
                ) : step}
              </div>
              <span className={`text-sm ${done ? "text-neutral-500 line-through" : "text-neutral-900 group-hover:text-brand-primary"}`}>
                {label}
              </span>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}
