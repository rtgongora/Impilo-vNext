"use client";

/**
 * Specialty Workspace — Dynamic clinical workspace that adapts
 * based on the specialty/encounter type.
 *
 * Route: /ehr/[patientId]/workspace/[specialty]
 *
 * Lovable reference: 18 specialty workspaces (Cardiology, Surgery,
 * Labour & Delivery, etc.) Each renders specialty-specific tools
 * and documentation panels.
 *
 * Runtime approach: Generic template that shows specialty-relevant
 * clinical tools based on a config map.
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft, Activity, Stethoscope, Heart, Syringe, Brain,
  Baby, Bone, Eye, Pill, Scissors, Zap, Thermometer,
  FileText, ClipboardList, AlertTriangle,
} from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";

interface SpecialtyConfig {
  label: string;
  description: string;
  icon: React.ComponentType<{ className?: string }>;
  color: string;
  tools: Array<{
    label: string;
    description: string;
    icon: React.ComponentType<{ className?: string }>;
    href?: string;
  }>;
  assessments: string[];
  orderSets: string[];
}

const SPECIALTY_CONFIGS: Record<string, SpecialtyConfig> = {
  cardiology: {
    label: "Cardiology", description: "Cardiovascular assessment and management",
    icon: Heart, color: "bg-red-100 text-red-600",
    tools: [
      { label: "ECG Interpretation", description: "12-lead ECG analysis", icon: Activity },
      { label: "Cardiac Risk Score", description: "HEART / TIMI / GRACE", icon: AlertTriangle },
      { label: "Echo Results", description: "Echocardiogram findings", icon: Heart },
    ],
    assessments: ["Chest pain assessment", "Heart failure classification (NYHA)", "Arrhythmia evaluation"],
    orderSets: ["ACS workup", "Heart failure panel", "Anticoagulation protocol"],
  },
  surgery: {
    label: "Surgery", description: "Surgical assessment and planning",
    icon: Scissors, color: "bg-blue-100 text-blue-600",
    tools: [
      { label: "Pre-op Checklist", description: "Surgical safety checklist", icon: ClipboardList },
      { label: "Anaesthesia Assessment", description: "ASA classification", icon: Thermometer },
      { label: "Consent Form", description: "Informed consent documentation", icon: FileText },
    ],
    assessments: ["Surgical risk assessment", "Wound classification", "DVT risk (Caprini)"],
    orderSets: ["Pre-op investigations", "Post-op orders", "Antibiotic prophylaxis"],
  },
  obstetrics: {
    label: "Obstetrics", description: "Pregnancy and delivery management",
    icon: Baby, color: "bg-pink-100 text-pink-600",
    tools: [
      { label: "Partograph", description: "Labour progress monitoring", icon: Activity },
      { label: "Fetal Monitoring", description: "CTG interpretation", icon: Heart },
      { label: "Antenatal Record", description: "Pregnancy summary", icon: FileText },
    ],
    assessments: ["Bishop score", "Apgar score", "Blood loss estimation"],
    orderSets: ["Labour admission orders", "Emergency C-section prep", "Post-partum care"],
  },
  paediatrics: {
    label: "Paediatrics", description: "Child health assessment and care",
    icon: Baby, color: "bg-green-100 text-green-600",
    tools: [
      { label: "Growth Chart", description: "WHO growth standards", icon: Activity },
      { label: "IMCI Protocol", description: "Integrated management of childhood illness", icon: ClipboardList },
      { label: "Immunization Schedule", description: "EPI calendar", icon: Syringe },
    ],
    assessments: ["Paediatric early warning score (PEWS)", "Developmental milestones", "Nutritional assessment"],
    orderSets: ["Paediatric fluid management", "Neonatal sepsis workup", "Asthma protocol"],
  },
  emergency: {
    label: "Emergency", description: "Emergency and trauma management",
    icon: Zap, color: "bg-red-100 text-red-600",
    tools: [
      { label: "Trauma Assessment", description: "Primary & secondary survey", icon: AlertTriangle },
      { label: "GCS Calculator", description: "Glasgow Coma Scale", icon: Brain },
      { label: "Resuscitation", description: "ABCDE approach", icon: Activity },
    ],
    assessments: ["Triage acuity", "Injury severity score", "NEWS2"],
    orderSets: ["Trauma panel", "Sepsis bundle", "Anaphylaxis protocol"],
  },
  orthopaedics: {
    label: "Orthopaedics", description: "Musculoskeletal assessment and management",
    icon: Bone, color: "bg-amber-100 text-amber-600",
    tools: [
      { label: "Fracture Classification", description: "AO/OTA classification", icon: Bone },
      { label: "ROM Assessment", description: "Range of motion", icon: Activity },
      { label: "X-ray Viewer", description: "Imaging review", icon: Eye },
    ],
    assessments: ["Fracture assessment", "Neurovascular status", "Compartment syndrome screening"],
    orderSets: ["Fracture workup", "Post-reduction orders", "Surgical planning"],
  },
};

const DEFAULT_CONFIG: SpecialtyConfig = {
  label: "General", description: "General clinical workspace",
  icon: Stethoscope, color: "bg-gray-100 text-gray-600",
  tools: [
    { label: "Clinical Notes", description: "SOAP documentation", icon: FileText },
    { label: "Orders", description: "Lab and imaging orders", icon: ClipboardList },
    { label: "Medications", description: "Prescribing", icon: Pill },
  ],
  assessments: ["General assessment", "Systems review"],
  orderSets: ["Admission orders", "Discharge medications"],
};

export default function SpecialtyWorkspacePage() {
  const params = useParams<{ patientId: string; specialty: string }>();
  const { patientId, specialty } = params;
  const config = SPECIALTY_CONFIGS[specialty] ?? DEFAULT_CONFIG;
  const SpecIcon = config.icon;

  return (
    <EHRLayout>
      <div className="space-y-6">
        <div className="flex items-center gap-3">
          <Link href={`/ehr/${patientId}`} className="text-gray-400 hover:text-gray-600">
            <ArrowLeft className="w-5 h-5" />
          </Link>
          <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${config.color}`}>
            <SpecIcon className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-lg font-semibold text-gray-900">{config.label} Workspace</h2>
            <p className="text-sm text-gray-500">{config.description}</p>
          </div>
        </div>

        {/* Specialty Tools */}
        <div className="bg-white rounded-lg border border-gray-200 p-5">
          <h3 className="text-sm font-semibold text-gray-900 mb-3">Specialty Tools</h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            {config.tools.map((tool) => {
              const ToolIcon = tool.icon;
              return (
                <div key={tool.label} className="bg-gray-50 rounded-lg border border-gray-200 p-4 hover:border-blue-300 transition-colors cursor-pointer">
                  <ToolIcon className="w-5 h-5 text-gray-400 mb-2" />
                  <p className="text-sm font-medium text-gray-900">{tool.label}</p>
                  <p className="text-xs text-gray-500">{tool.description}</p>
                </div>
              );
            })}
          </div>
        </div>

        {/* Assessments */}
        <div className="bg-white rounded-lg border border-gray-200 p-5">
          <h3 className="text-sm font-semibold text-gray-900 mb-3">Assessments</h3>
          <div className="space-y-2">
            {config.assessments.map((assessment) => (
              <div key={assessment} className="flex items-center gap-2 px-3 py-2 bg-gray-50 rounded-lg">
                <ClipboardList className="w-4 h-4 text-gray-400" />
                <span className="text-sm text-gray-700">{assessment}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Order Sets */}
        <div className="bg-white rounded-lg border border-gray-200 p-5">
          <h3 className="text-sm font-semibold text-gray-900 mb-3">Quick Order Sets</h3>
          <div className="flex flex-wrap gap-2">
            {config.orderSets.map((orderSet) => (
              <button key={orderSet}
                className="px-3 py-1.5 text-xs font-medium bg-blue-50 text-blue-700 rounded-lg hover:bg-blue-100 transition-colors">
                {orderSet}
              </button>
            ))}
          </div>
        </div>
      </div>
    </EHRLayout>
  );
}
