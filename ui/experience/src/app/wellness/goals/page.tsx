"use client";

/**
 * Health Goals — Health OS §5, §6
 * Set and track personal health and wellness targets.
 * Route: /wellness/goals | Zone: wellness | Guard: auth
 */

import { Target, Plus } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const GOAL_TEMPLATES = [
  { title: "Steps & Movement", description: "Daily step count and active minutes targets", color: "bg-impilo-50 border-impilo-200" },
  { title: "Weight Management", description: "Set a target weight and track progress over time", color: "bg-green-50 border-green-200" },
  { title: "Nutrition", description: "Daily calorie, water intake, or macronutrient goals", color: "bg-orange-50 border-orange-200" },
  { title: "Sleep Quality", description: "Target sleep duration and consistency goals", color: "bg-indigo-50 border-indigo-200" },
  { title: "Blood Pressure", description: "Maintain blood pressure within target range", color: "bg-red-50 border-red-200" },
  { title: "Blood Glucose", description: "Keep blood sugar within healthy levels", color: "bg-amber-50 border-amber-200" },
];

export default function WellnessGoalsPage() {
  return (
    <AppLayout>
      <PageShell
        title="Health Goals"
        subtitle="Set and track personal health and wellness targets"
        icon={<Target className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Actions */}
          <div className="flex items-center justify-end">
            <button className="inline-flex items-center gap-2 rounded-lg bg-green-600 px-4 py-2 text-sm font-medium text-white hover:bg-green-700 transition-colors">
              <Plus className="h-4 w-4" />
              Set New Goal
            </button>
          </div>

          {/* Active goals - empty state */}
          <div>
            <h3 className="text-sm font-semibold text-gray-900 mb-3">Active Goals</h3>
            <div className="rounded-lg border border-dashed border-gray-300 bg-gray-50 p-8 text-center">
              <Target className="mx-auto h-10 w-10 text-gray-400" />
              <p className="mt-3 text-sm text-gray-600">No active goals. Choose a template below to get started.</p>
            </div>
          </div>

          {/* Goal templates */}
          <div>
            <h3 className="text-sm font-semibold text-gray-900 mb-3">Goal Templates</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {GOAL_TEMPLATES.map(({ title, description, color }) => (
                <div
                  key={title}
                  className={`rounded-xl border p-5 hover:shadow-md transition-all cursor-pointer ${color}`}
                >
                  <h4 className="font-semibold text-gray-900 mb-1">{title}</h4>
                  <p className="text-sm text-gray-600">{description}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
