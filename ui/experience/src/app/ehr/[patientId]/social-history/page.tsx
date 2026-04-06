"use client";

/**
 * Social History — Social determinants of health with structured assessments.
 * Route: /ehr/[patientId]/social-history | pageTitle: "Social History"
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import { Home, Loader2, Edit3, Save, X, Cigarette, Wine, Briefcase, GraduationCap, Car, Users, Heart } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface SocialSection {
  id: string;
  category: string;
  icon: string;
  status: string;
  detail: string;
  lastUpdated: string;
  riskLevel: "Low" | "Moderate" | "High" | "Unknown";
}

const MOCK_SOCIAL: SocialSection[] = [
  { id: "s-1", category: "Smoking", icon: "cigarette", status: "Former Smoker", detail: "Quit 3 years ago. 10 pack-year history. Used nicotine patches to quit.", lastUpdated: "2026-03-15", riskLevel: "Moderate" },
  { id: "s-2", category: "Alcohol Use", icon: "wine", status: "Social Drinker", detail: "1-2 drinks per week, typically wine with meals. No binge drinking. AUDIT-C score: 2.", lastUpdated: "2026-03-15", riskLevel: "Low" },
  { id: "s-3", category: "Substance Use", icon: "pill", status: "No current use", detail: "No history of recreational drug use. No IV drug use.", lastUpdated: "2026-03-15", riskLevel: "Low" },
  { id: "s-4", category: "Occupation", icon: "briefcase", status: "Office Worker", detail: "Accountant at a mid-size firm. Sedentary work 8hrs/day. No occupational hazards identified. Employed full-time.", lastUpdated: "2026-02-10", riskLevel: "Low" },
  { id: "s-5", category: "Education", icon: "education", status: "University Graduate", detail: "Bachelor of Commerce degree. Health literate — can read and understand medical information.", lastUpdated: "2025-11-20", riskLevel: "Low" },
  { id: "s-6", category: "Housing", icon: "home", status: "Stable Housing", detail: "Owns a 3-bedroom house in suburban area. Running water and electricity. No lead or mold concerns.", lastUpdated: "2025-11-20", riskLevel: "Low" },
  { id: "s-7", category: "Food Security", icon: "food", status: "Food Secure", detail: "No difficulty accessing nutritious food. Regular meals 3x daily. Follows a balanced diet.", lastUpdated: "2026-01-05", riskLevel: "Low" },
  { id: "s-8", category: "Transportation", icon: "car", status: "Has Own Vehicle", detail: "Personal vehicle available. Can reach healthcare facilities within 20 minutes. Valid driver licence.", lastUpdated: "2025-11-20", riskLevel: "Low" },
  { id: "s-9", category: "Social Support", icon: "users", status: "Strong Support Network", detail: "Married, 2 children. Active church community. Regular contact with extended family. No isolation concerns.", lastUpdated: "2026-01-05", riskLevel: "Low" },
];

const RISK_STYLES: Record<string, string> = {
  Low: "bg-green-100 text-green-700",
  Moderate: "bg-amber-100 text-amber-700",
  High: "bg-red-100 text-red-700",
  Unknown: "bg-gray-100 text-gray-600",
};

const ICON_MAP: Record<string, React.ElementType> = {
  cigarette: Cigarette,
  wine: Wine,
  pill: Heart,
  briefcase: Briefcase,
  education: GraduationCap,
  home: Home,
  food: Heart,
  car: Car,
  users: Users,
};

export default function SocialHistoryPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { data, isLoading } = useQuery({
    queryKey: ["social-history", patientId],
    queryFn: async () => ({ data: MOCK_SOCIAL }),
  });

  const sections = data?.data ?? [];
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editStatus, setEditStatus] = useState("");
  const [editDetail, setEditDetail] = useState("");

  function startEdit(section: SocialSection) {
    setEditingId(section.id);
    setEditStatus(section.status);
    setEditDetail(section.detail);
  }

  function cancelEdit() {
    setEditingId(null);
    setEditStatus("");
    setEditDetail("");
  }

  const highRiskCount = sections.filter((s) => s.riskLevel === "High").length;
  const moderateRiskCount = sections.filter((s) => s.riskLevel === "Moderate").length;

  return (
    <EHRLayout>
      <PageShell title="Social History" subtitle="Social determinants of health and lifestyle factors">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading social history...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Home className="w-5 h-5 text-teal-600" />
                <h2 className="text-lg font-semibold text-gray-900">Social Determinants of Health</h2>
              </div>
              <div className="flex items-center gap-2 text-xs">
                {highRiskCount > 0 && (
                  <span className="px-2.5 py-1 bg-red-100 text-red-700 rounded-full font-medium">{highRiskCount} High Risk</span>
                )}
                {moderateRiskCount > 0 && (
                  <span className="px-2.5 py-1 bg-amber-100 text-amber-700 rounded-full font-medium">{moderateRiskCount} Moderate Risk</span>
                )}
                {highRiskCount === 0 && moderateRiskCount === 0 && (
                  <span className="px-2.5 py-1 bg-green-100 text-green-700 rounded-full font-medium">All Low Risk</span>
                )}
              </div>
            </div>

            {/* Sections */}
            {sections.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <Home className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No social history recorded</p>
              </div>
            ) : (
              <div className="space-y-3">
                {sections.map((section) => {
                  const IconComp = ICON_MAP[section.icon] || Home;
                  const isEditing = editingId === section.id;

                  return (
                    <div key={section.id} className="bg-white rounded-lg border border-gray-200 p-5">
                      <div className="flex items-start gap-4">
                        <div className="w-10 h-10 rounded-lg bg-teal-100 text-teal-600 flex items-center justify-center shrink-0">
                          <IconComp className="w-5 h-5" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center justify-between mb-1">
                            <h3 className="font-medium text-gray-900 text-sm">{section.category}</h3>
                            <div className="flex items-center gap-2">
                              <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${RISK_STYLES[section.riskLevel]}`}>
                                {section.riskLevel} Risk
                              </span>
                              {!isEditing && (
                                <button onClick={() => startEdit(section)} className="p-1 text-gray-400 hover:text-blue-600 transition-colors">
                                  <Edit3 className="w-3.5 h-3.5" />
                                </button>
                              )}
                            </div>
                          </div>

                          {isEditing ? (
                            <div className="space-y-3 mt-2">
                              <div>
                                <label className="block text-xs font-medium text-gray-600 mb-1">Status</label>
                                <input type="text" value={editStatus} onChange={(e) => setEditStatus(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                              </div>
                              <div>
                                <label className="block text-xs font-medium text-gray-600 mb-1">Details</label>
                                <textarea value={editDetail} onChange={(e) => setEditDetail(e.target.value)} rows={3} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                              </div>
                              <div className="flex items-center gap-2">
                                <button className="inline-flex items-center gap-1 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700 transition-colors">
                                  <Save className="w-3 h-3" /> Save
                                </button>
                                <button onClick={cancelEdit} className="px-3 py-1.5 text-xs font-medium text-gray-600 rounded-lg border border-gray-300 hover:bg-gray-50 transition-colors">Cancel</button>
                              </div>
                            </div>
                          ) : (
                            <>
                              <p className="text-sm font-medium text-gray-700">{section.status}</p>
                              <p className="text-xs text-gray-500 mt-1">{section.detail}</p>
                              <p className="text-[10px] text-gray-400 mt-2">Last updated: {section.lastUpdated}</p>
                            </>
                          )}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
