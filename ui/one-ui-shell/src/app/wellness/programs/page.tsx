"use client";

/**
 * Prevention Programs — Health OS §5, §6
 * Enroll in wellness and prevention programmes.
 * Route: /wellness/programs | Zone: wellness | Guard: auth
 */

import { BookOpen, Search, Filter } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const PROGRAMS = [
  { title: "Diabetes Prevention", description: "Lifestyle changes to prevent or delay Type 2 diabetes", duration: "12 weeks", status: "Open" },
  { title: "Cardiovascular Wellness", description: "Heart-healthy eating, exercise, and stress management", duration: "8 weeks", status: "Open" },
  { title: "Maternal Wellness", description: "Prenatal and postnatal health, nutrition, and support", duration: "Ongoing", status: "Open" },
  { title: "Mental Wellness", description: "Stress management, mindfulness, and emotional resilience", duration: "6 weeks", status: "Open" },
  { title: "Smoking Cessation", description: "Structured programme to quit smoking with support", duration: "10 weeks", status: "Open" },
  { title: "Weight Management", description: "Evidence-based weight loss and healthy eating programme", duration: "16 weeks", status: "Coming Soon" },
];

export default function WellnessProgramsPage() {
  return (
    <AppLayout>
      <PageShell
        title="Prevention Programs"
        subtitle="Enroll in wellness and prevention programmes designed for your health needs"
        icon={<BookOpen className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Search and filter */}
          <div className="flex gap-3">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
              <input
                type="text"
                placeholder="Search programmes..."
                className="w-full rounded-lg border border-gray-300 py-2.5 pl-10 pr-4 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
              />
            </div>
            <button className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors">
              <Filter className="h-4 w-4" />
              Filters
            </button>
          </div>

          {/* My enrollments - empty state */}
          <div>
            <h3 className="text-sm font-semibold text-gray-900 mb-3">My Enrollments</h3>
            <div className="rounded-lg border border-dashed border-gray-300 bg-gray-50 p-6 text-center">
              <p className="text-sm text-gray-600">You are not enrolled in any programmes. Browse below to find one.</p>
            </div>
          </div>

          {/* Available programs */}
          <div>
            <h3 className="text-sm font-semibold text-gray-900 mb-3">Available Programmes</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {PROGRAMS.map(({ title, description, duration, status }) => (
                <div
                  key={title}
                  className="rounded-xl border border-gray-200 bg-white p-5 hover:border-emerald-400 hover:shadow-md transition-all cursor-pointer group"
                >
                  <div className="flex items-center justify-between mb-2">
                    <h4 className="font-semibold text-gray-900 group-hover:text-emerald-600 transition-colors">{title}</h4>
                    <span className={`rounded px-2 py-0.5 text-xs font-medium ${status === "Open" ? "bg-green-50 text-green-700" : "bg-gray-100 text-gray-500"}`}>
                      {status}
                    </span>
                  </div>
                  <p className="text-sm text-gray-600 mb-2">{description}</p>
                  <p className="text-xs text-gray-400">Duration: {duration}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
