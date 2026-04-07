"use client";

/**
 * ClinicalReferences — Popover-based clinical reference library
 * with role-filtered resources (guidelines, SOPs, protocols, procedures, etc.)
 */

import { useState } from "react";
import { useRouter } from "next/navigation";
import {
  BookOpen, FileText, ClipboardList, Stethoscope, Pill, Baby, Heart,
  Shield, ExternalLink, Search, Tablet, ScrollText, Scissors,
  FlaskConical, Headphones, HelpCircle,
} from "lucide-react";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { useAuthStore } from "@/hooks/useAuthStore";

type ProviderRole = "physician" | "specialist" | "registrar" | "consultant" | "nurse" | "midwife" | "chw" | "pharmacist" | "radiologist" | "pathologist";

interface ReferenceItem {
  icon: React.ElementType;
  label: string;
  description: string;
  category: string;
  roles: ProviderRole[];
}

const clinicalReferences: ReferenceItem[] = [
  { icon: Tablet, label: "Pill Identifier", description: "Identify medications by shape, colour, imprint & markings", category: "pill-id", roles: ["physician", "specialist", "registrar", "consultant", "nurse", "midwife", "pharmacist"] },
  { icon: Tablet, label: "Visual Drug Reference", description: "Photo library of common tablets, capsules & injectables", category: "pill-id", roles: ["physician", "specialist", "registrar", "consultant", "nurse", "pharmacist"] },
  { icon: ScrollText, label: "WHO Clinical Guidelines", description: "Latest WHO evidence-based treatment guidelines", category: "guidelines", roles: ["physician", "specialist", "registrar", "consultant", "nurse", "midwife", "pharmacist"] },
  { icon: ScrollText, label: "National STGs", description: "Standard Treatment Guidelines — current edition", category: "guidelines", roles: ["physician", "specialist", "registrar", "consultant", "nurse", "midwife", "chw", "pharmacist"] },
  { icon: ScrollText, label: "Antibiotic Stewardship Guide", description: "Empiric antibiotic selection & resistance patterns", category: "guidelines", roles: ["physician", "specialist", "registrar", "consultant", "pharmacist"] },
  { icon: ScrollText, label: "ART Guidelines", description: "Antiretroviral therapy initiation & monitoring", category: "guidelines", roles: ["physician", "specialist", "registrar", "consultant", "nurse", "pharmacist"] },
  { icon: Heart, label: "Cardiac Care Guidelines", description: "ACS, heart failure & arrhythmia management", category: "guidelines", roles: ["physician", "specialist", "consultant"] },
  { icon: Scissors, label: "Procedure Guides", description: "Step-by-step clinical procedure instructions", category: "procedures", roles: ["physician", "specialist", "registrar", "consultant"] },
  { icon: Scissors, label: "Bedside Procedures", description: "LP, thoracentesis, central line, arterial line", category: "procedures", roles: ["physician", "specialist", "registrar", "consultant"] },
  { icon: Scissors, label: "Nursing Procedures", description: "IV insertion, catheterisation, wound care, NG tube", category: "procedures", roles: ["nurse", "midwife"] },
  { icon: Baby, label: "Obstetric Procedures", description: "Assisted delivery, episiotomy, manual removal", category: "procedures", roles: ["midwife", "specialist", "consultant"] },
  { icon: HelpCircle, label: "Clinical Case Studies", description: "Real-world case presentations with analysis", category: "cases", roles: ["physician", "specialist", "registrar", "consultant", "nurse", "midwife", "pharmacist"] },
  { icon: FlaskConical, label: "Knowledge Quizzes", description: "Test clinical knowledge across specialties", category: "cases", roles: ["physician", "specialist", "registrar", "consultant", "nurse", "midwife", "pharmacist"] },
  { icon: Headphones, label: "Clinical Podcasts", description: "Audio summaries of latest evidence & guidelines", category: "podcasts", roles: ["physician", "specialist", "registrar", "consultant", "nurse", "midwife", "pharmacist"] },
  { icon: Headphones, label: "CPD Audio Modules", description: "Continuing professional development content", category: "podcasts", roles: ["physician", "specialist", "registrar", "consultant", "nurse", "midwife", "pharmacist"] },
  { icon: FileText, label: "Discharge Planning SOP", description: "Step-by-step discharge procedures", category: "sop", roles: ["physician", "specialist", "registrar", "consultant", "nurse"] },
  { icon: Shield, label: "Infection Control SOPs", description: "Hand hygiene, PPE, isolation protocols", category: "sop", roles: ["nurse", "midwife", "physician"] },
  { icon: FileText, label: "Dispensing SOPs", description: "Verification, labelling, patient counselling", category: "sop", roles: ["pharmacist"] },
  { icon: Stethoscope, label: "Clinical Assessment Guidelines", description: "History, examination & differential diagnosis", category: "guide", roles: ["physician", "specialist", "registrar", "consultant"] },
  { icon: ClipboardList, label: "Nursing Assessment Tools", description: "Vital signs, GCS, pain scales, falls risk", category: "guide", roles: ["nurse", "midwife"] },
  { icon: Pill, label: "Medication Administration Guide", description: "Routes, timing, double-check procedures", category: "guide", roles: ["nurse", "midwife"] },
  { icon: BookOpen, label: "Health Education Materials", description: "Patient counselling guides & pamphlets", category: "guide", roles: ["chw"] },
  { icon: Heart, label: "Emergency Protocols", description: "Code Blue, ACLS, trauma activation", category: "protocol", roles: ["physician", "specialist", "registrar", "consultant", "nurse"] },
  { icon: FileText, label: "Prescribing Protocols", description: "Standard treatment guidelines & dosing", category: "protocol", roles: ["physician", "specialist", "registrar", "consultant", "pharmacist"] },
  { icon: Baby, label: "Labour & Delivery Protocols", description: "Partograph, active management of 3rd stage", category: "protocol", roles: ["midwife"] },
  { icon: ClipboardList, label: "Surgical Safety Checklist", description: "WHO surgical safety checklist", category: "checklist", roles: ["physician", "specialist", "consultant"] },
  { icon: FileText, label: "Handover Checklist (ISBAR)", description: "Standardised clinical handover format", category: "checklist", roles: ["nurse", "midwife", "physician"] },
  { icon: Shield, label: "Patient Safety Incident Reporting", description: "How to report adverse events", category: "sop", roles: ["physician", "specialist", "registrar", "consultant", "nurse", "midwife", "chw", "pharmacist", "radiologist", "pathologist"] },
];

const categoryConfig: Record<string, { label: string; color: string }> = {
  "pill-id": { label: "Pill Identifier", color: "bg-cyan-50 text-cyan-700" },
  guidelines: { label: "Latest Guidelines", color: "bg-indigo-50 text-indigo-700" },
  procedures: { label: "Procedures", color: "bg-teal-50 text-teal-700" },
  cases: { label: "Cases & Quizzes", color: "bg-orange-50 text-orange-700" },
  podcasts: { label: "Podcasts", color: "bg-pink-50 text-pink-700" },
  guide: { label: "Guides", color: "bg-blue-50 text-blue-700" },
  sop: { label: "SOPs", color: "bg-amber-50 text-amber-700" },
  protocol: { label: "Protocols", color: "bg-emerald-50 text-emerald-700" },
  checklist: { label: "Checklists", color: "bg-rose-50 text-rose-700" },
};

const categoryOrder = ["pill-id", "guidelines", "procedures", "cases", "podcasts", "sop", "protocol", "guide", "checklist"];

// Map user roles to Lovable ProviderRole for reference filtering
function resolveProviderRole(roles: string[]): ProviderRole {
  const lowerRoles = roles.map(r => r.toLowerCase());
  if (lowerRoles.some(r => r.includes("consultant"))) return "consultant";
  if (lowerRoles.some(r => r.includes("specialist"))) return "specialist";
  if (lowerRoles.some(r => r.includes("registrar"))) return "registrar";
  if (lowerRoles.some(r => r.includes("physician") || r.includes("doctor") || r === "clinical")) return "physician";
  if (lowerRoles.some(r => r.includes("midwife") || r.includes("midwifery"))) return "midwife";
  if (lowerRoles.some(r => r.includes("nurse"))) return "nurse";
  if (lowerRoles.some(r => r.includes("pharmacist") || r.includes("dispenser"))) return "pharmacist";
  if (lowerRoles.some(r => r.includes("radiolog"))) return "radiologist";
  if (lowerRoles.some(r => r.includes("patholog"))) return "pathologist";
  if (lowerRoles.some(r => r.includes("chw") || r.includes("community"))) return "chw";
  return "physician"; // default fallback
}

export function ClinicalReferences() {
  const [open, setOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const router = useRouter();
  const { isClinical } = useRoleGroup();
  const user = useAuthStore((s) => s.user);

  const providerRole = resolveProviderRole(user?.roles ?? []);

  // Filter by role first (security), then by search query
  const roleFiltered = clinicalReferences.filter(
    (ref) => ref.roles.includes(providerRole)
  );
  const filtered = roleFiltered.filter(
    (ref) =>
      ref.label.toLowerCase().includes(searchQuery.toLowerCase()) ||
      ref.description.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const grouped = categoryOrder.reduce((acc, cat) => {
    acc[cat] = filtered.filter((r) => r.category === cat);
    return acc;
  }, {} as Record<string, ReferenceItem[]>);

  return (
    <div className="relative">
      <button
        onClick={() => setOpen(!open)}
        className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg transition-colors ${
          open ? "bg-blue-100 text-blue-700" : "text-gray-600 hover:bg-gray-100"
        }`}
      >
        <BookOpen className="w-3.5 h-3.5" />
        References & SOPs
      </button>

      {open && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />
          <div className="absolute left-0 top-full mt-1 w-80 bg-white rounded-lg shadow-lg border border-gray-200 z-50 overflow-hidden">
            <div className="p-3 pb-2">
              <h3 className="font-semibold text-sm text-gray-900">References, SOPs & Learning</h3>
              <p className="text-xs text-gray-500">{filtered.length} of {roleFiltered.length} resources for your role</p>
            </div>

            <div className="px-3 pb-2">
              <div className="relative">
                <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-gray-400" />
                <input
                  placeholder="Search references, guidelines, cases..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="w-full h-8 pl-8 pr-3 text-sm border border-gray-200 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
            </div>

            <div className="border-t border-gray-100" />

            <div className="max-h-[400px] overflow-y-auto">
              {categoryOrder.map((category) => {
                const items = grouped[category];
                if (!items || items.length === 0) return null;
                const config = categoryConfig[category];
                return (
                  <div key={category} className="p-2">
                    <p className="text-[10px] font-medium text-gray-400 uppercase tracking-wider px-2 mb-1">{config.label}</p>
                    {items.map((ref) => {
                      const Icon = ref.icon;
                      return (
                        <button
                          key={ref.label}
                          onClick={() => { setOpen(false); router.push("/clinical-tools?tab=documents"); }}
                          className="w-full flex items-center gap-3 px-2 py-2 rounded-md hover:bg-gray-50 transition-colors text-left"
                        >
                          <div className="h-7 w-7 rounded-md bg-blue-50 flex items-center justify-center shrink-0">
                            <Icon className="h-3.5 w-3.5 text-blue-600" />
                          </div>
                          <div className="min-w-0 flex-1">
                            <p className="text-xs font-medium text-gray-900 leading-tight">{ref.label}</p>
                            <p className="text-[11px] text-gray-500 leading-snug">{ref.description}</p>
                          </div>
                          <span className={`px-1.5 py-0 rounded text-[9px] font-medium shrink-0 ${config.color}`}>{config.label}</span>
                        </button>
                      );
                    })}
                  </div>
                );
              })}
              {filtered.length === 0 && (
                <div className="p-6 text-center"><p className="text-sm text-gray-400">No references found</p></div>
              )}
            </div>

            <div className="border-t border-gray-100 p-2">
              <button
                onClick={() => { setOpen(false); router.push("/clinical-tools?tab=documents"); }}
                className="w-full flex items-center justify-center gap-1.5 px-3 py-2 text-xs font-medium text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50"
              >
                Browse All Resources <ExternalLink className="h-3 w-3" />
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
