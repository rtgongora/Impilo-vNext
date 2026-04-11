"use client";

/**
 * Screening Schedule — Health OS §5, §6
 * View due and upcoming health screenings.
 * Route: /wellness/screenings | Zone: wellness | Guard: auth
 */

import { CalendarCheck, Clock, CheckCircle2, AlertCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const SCREENING_TYPES = [
  { title: "Blood Pressure Check", frequency: "Every 12 months", status: "due", description: "Routine blood pressure measurement" },
  { title: "Cholesterol Panel", frequency: "Every 5 years", status: "upcoming", description: "Lipid panel including total cholesterol, LDL, HDL, triglycerides" },
  { title: "Blood Glucose", frequency: "Every 3 years", status: "upcoming", description: "Fasting blood glucose or HbA1c for diabetes screening" },
  { title: "BMI Assessment", frequency: "Every 12 months", status: "due", description: "Body mass index measurement and weight tracking" },
  { title: "Vision Screening", frequency: "Every 2 years", status: "upcoming", description: "Visual acuity and eye health assessment" },
  { title: "Dental Check-up", frequency: "Every 6 months", status: "overdue", description: "Routine dental examination and cleaning" },
];

export default function WellnessScreeningsPage() {
  return (
    <AppLayout>
      <PageShell
        title="Screening Schedule"
        subtitle="View due and upcoming health screenings based on your age, gender, and risk factors"
        icon={<CalendarCheck className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Status summary */}
          <div className="grid grid-cols-3 gap-4">
            {[
              { label: "Overdue", count: 1, Icon: AlertCircle, color: "border-red-200 bg-red-50 text-red-700" },
              { label: "Due Now", count: 2, Icon: Clock, color: "border-amber-200 bg-amber-50 text-amber-700" },
              { label: "Up to Date", count: 3, Icon: CheckCircle2, color: "border-green-200 bg-green-50 text-green-700" },
            ].map(({ label, count, Icon, color }) => (
              <div key={label} className={`rounded-lg border p-4 text-center ${color}`}>
                <Icon className="mx-auto h-6 w-6 mb-1" />
                <p className="text-2xl font-bold">{count}</p>
                <p className="text-sm font-medium">{label}</p>
              </div>
            ))}
          </div>

          {/* Screening list */}
          <div className="space-y-3">
            {SCREENING_TYPES.map(({ title, frequency, status, description }) => (
              <div key={title} className="flex items-center justify-between rounded-lg border border-gray-200 bg-white p-4 hover:shadow-sm transition-all">
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-1">
                    <h3 className="font-semibold text-gray-900">{title}</h3>
                    <span className={`rounded px-2 py-0.5 text-xs font-medium ${
                      status === "overdue" ? "bg-red-50 text-red-700" :
                      status === "due" ? "bg-amber-50 text-amber-700" :
                      "bg-green-50 text-green-700"
                    }`}>
                      {status === "overdue" ? "Overdue" : status === "due" ? "Due" : "Upcoming"}
                    </span>
                  </div>
                  <p className="text-sm text-gray-600">{description}</p>
                  <p className="text-xs text-gray-400 mt-1">Recommended: {frequency}</p>
                </div>
                <button className="ml-4 rounded-lg border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50 transition-colors">
                  Schedule
                </button>
              </div>
            ))}
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
