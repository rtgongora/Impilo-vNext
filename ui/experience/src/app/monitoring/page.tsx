"use client";

/**
 * Remote Monitoring Hub — Health OS §2
 * Devices, readings, chronic care, alerts.
 * Route: /monitoring | Zone: monitoring | Guard: auth
 */

import Link from "next/link";
import { Monitor as MonitorIcon, Smartphone, TrendingUp, AlertTriangle, FileHeart } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const SECTIONS = [
  { href: "/monitoring/devices", label: "My Devices", description: "Paired health devices and sensors", Icon: Smartphone },
  { href: "/monitoring/readings", label: "Readings & Trends", description: "View vitals and measurements from devices", Icon: TrendingUp },
  { href: "/monitoring/alerts", label: "Monitoring Alerts", description: "Threshold breaches and care escalations", Icon: AlertTriangle },
  { href: "/monitoring/care-plans", label: "Chronic Care Plans", description: "Active care plans linked to monitoring", Icon: FileHeart },
];

export default function MonitoringPage() {
  return (
    <AppLayout>
      <PageShell title="Remote Monitoring" subtitle="Health OS §2 — Devices, readings, chronic care plans, and alerts" icon={<MonitorIcon className="h-6 w-6" />}>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {SECTIONS.map(({ href, label, description, Icon }) => (
            <Link key={href} href={href} className="rounded-lg border border-gray-200 bg-white p-5 hover:border-blue-400 hover:shadow-sm transition-all">
              <div className="flex items-center gap-3 mb-2">
                <div className="rounded-lg bg-orange-50 p-2"><Icon className="h-5 w-5 text-orange-600" /></div>
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
