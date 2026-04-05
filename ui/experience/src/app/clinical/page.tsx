"use client";

/**
 * Clinical Care Hub — 10-tile entry point for clinical workflows.
 * Route: /clinical
 *
 * Lovable reference: Clinical Care category with 10 module tiles.
 * Runtime maps each tile to the existing clinical surface.
 */

import Link from "next/link";
import {
  ClipboardList, Users, Stethoscope, Bed, Calendar,
  ArrowRightLeft, Clock, Gauge, DoorOpen, MessageSquare,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useRoleGroup } from "@/hooks/useRoleGroup";

const CLINICAL_MODULES = [
  { label: "My Dashboard", description: "Your worklist, tasks, and alerts", href: "/queue", icon: ClipboardList, color: "bg-blue-100 text-blue-600" },
  { label: "Communication", description: "Messages, pages & calls", href: "/home/notifications", icon: MessageSquare, color: "bg-blue-100 text-blue-600" },
  { label: "Patient Queue", description: "Waiting patients & triage", href: "/queue", icon: Users, color: "bg-orange-100 text-orange-600" },
  { label: "Patient Encounters", description: "Clinical documentation & care", href: "/queue/search", icon: Stethoscope, color: "bg-blue-100 text-blue-600", clinical: true },
  { label: "Bed Management", description: "Ward status & admissions", href: "/beds", icon: Bed, color: "bg-purple-100 text-purple-600", clinical: true },
  { label: "Appointments", description: "Scheduling & booking", href: "/scheduling", icon: Calendar, color: "bg-cyan-100 text-cyan-600" },
  { label: "Discharge & Exit", description: "Discharges, deaths & exits", href: "/queue", icon: DoorOpen, color: "bg-amber-100 text-amber-600", clinical: true },
  { label: "Shift Handoff", description: "Care continuity reports", href: "/shift/handover", icon: ArrowRightLeft, color: "bg-teal-100 text-teal-600", clinical: true },
  { label: "Control Tower", description: "Real-time facility operations", href: "/queue", icon: Gauge, color: "bg-rose-100 text-rose-600", admin: true },
  { label: "Operations & Roster", description: "Shifts, roster & workforce", href: "/shift", icon: Clock, color: "bg-cyan-100 text-cyan-600" },
];

export default function ClinicalHubPage() {
  const { isClinical } = useRoleGroup();

  return (
    <AppLayout>
      <PageShell title="Clinical Care" subtitle="Patient encounters, assessments, and care delivery">
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
          {CLINICAL_MODULES.map((mod) => {
            const Icon = mod.icon;
            return (
              <Link key={mod.label} href={mod.href}
                className="bg-white rounded-lg border border-gray-200 p-5 hover:border-blue-300 hover:shadow-md transition-all group text-center">
                <div className={`w-12 h-12 rounded-lg ${mod.color} flex items-center justify-center mx-auto mb-3`}>
                  <Icon className="w-6 h-6" />
                </div>
                <h3 className="text-sm font-medium text-gray-900 group-hover:text-blue-600 transition-colors">
                  {mod.label}
                </h3>
                <p className="text-xs text-gray-500 mt-1">{mod.description}</p>
              </Link>
            );
          })}
        </div>
      </PageShell>
    </AppLayout>
  );
}
