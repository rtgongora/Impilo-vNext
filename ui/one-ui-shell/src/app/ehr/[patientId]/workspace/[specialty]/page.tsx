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
  FileText, ClipboardList, AlertTriangle, ExternalLink,
} from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { findSpecialty } from "@/features/medicine/specialties/specialty-config";

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
      { label: "Cardiovascular Risk (WHO HEARTS)", description: "Total CV risk, BP thresholds and statin indication", icon: Activity, href: "/ehr/[patientId]/medicine/cds" },
      { label: "Echo Results", description: "Echocardiogram findings", icon: Heart },
    ],
    assessments: ["Chest pain assessment", "Heart failure classification (NYHA)", "Arrhythmia evaluation"],
    orderSets: ["ACS workup", "Heart failure panel", "Anticoagulation protocol"],
  },
  surgery: {
    label: "Surgery", description: "Surgical assessment and planning",
    icon: Scissors, color: "bg-primary-soft text-primary",
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
      { label: "Partograph", description: "Labour progress monitoring", icon: Activity, href: "/ehr/[patientId]/maternity" },
      { label: "Fetal Monitoring", description: "CTG interpretation", icon: Heart, href: "/ehr/[patientId]/maternity" },
      { label: "PPH Protocol", description: "Postpartum haemorrhage first-response bundle", icon: AlertTriangle, href: "/ehr/[patientId]/maternity#emergency-bundles" },
      { label: "Eclampsia Protocol", description: "Eclampsia / severe pre-eclampsia bundle", icon: AlertTriangle, href: "/ehr/[patientId]/maternity#emergency-bundles" },
      { label: "Antenatal Record", description: "Pregnancy summary", icon: FileText },
    ],
    assessments: ["Bishop score", "Apgar score", "Blood loss estimation"],
    orderSets: ["Labour admission orders", "Emergency C-section prep", "Post-partum care"],
  },
  paediatrics: {
    label: "Paediatrics", description: "Child health assessment and care",
    icon: Baby, color: "bg-green-100 text-green-600",
    tools: [
      { label: "Paediatric Workspace", description: "Age-aware assessment and what is due today", icon: Baby, href: "/ehr/[patientId]/paediatrics" },
      { label: "Growth Chart", description: "WHO growth standards, plotted", icon: Activity, href: "/ehr/[patientId]/growth-chart" },
      { label: "Immunizations", description: "Doses given (schedule forecast not available yet)", icon: Syringe, href: "/ehr/[patientId]/immunizations" },
      { label: "IMCI Protocol", description: "Integrated management of childhood illness", icon: ClipboardList },
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
  icon: Stethoscope, color: "bg-neutral-100 text-muted-foreground",
  tools: [
    { label: "Medicine Workspace", description: "Programmes, problem list and allergies in one view", icon: Stethoscope, href: "/ehr/[patientId]/medicine" },
    { label: "Decision Support", description: "Governed guidance across eight topics", icon: AlertTriangle, href: "/ehr/[patientId]/medicine/cds" },
    { label: "Clinical Notes", description: "SOAP documentation", icon: FileText, href: "/ehr/[patientId]/notes" },
    { label: "Orders", description: "Lab and imaging orders", icon: ClipboardList, href: "/ehr/[patientId]/orders" },
    { label: "Medications", description: "Prescribing", icon: Pill, href: "/ehr/[patientId]/medications" },
  ],
  assessments: ["General assessment", "Systems review"],
  orderSets: ["Admission orders", "Discharge medications"],
};

export default function SpecialtyWorkspacePage() {
  const params = useParams<{ patientId: string; specialty: string }>();
  const { patientId, specialty } = params;
  const config = SPECIALTY_CONFIGS[specialty] ?? DEFAULT_CONFIG;
  const SpecIcon = config.icon;
  const medicineSpecialty = findSpecialty(specialty);
  const medicineSpecialtyHref = medicineSpecialty
    ? `/ehr/${patientId}/medicine/specialty/${medicineSpecialty.key}`
    : null;

  return (
    <EHRLayout>
      <div className="space-y-6">
        <div
          className="rounded-lg border border-warning/40 bg-amber-50 p-4 text-sm text-warning-foreground"
          data-testid="legacy-specialty-banner"
        >
          <p className="font-medium">
            This legacy specialty workspace is a design template. Use Medicine specialty routes.
          </p>
          <p className="mt-1 text-warning-foreground/90">
            Hard-coded tiles below are not wired to production APIs. Disabled tools stay disabled by
            design — do not treat them as live capabilities.
          </p>
          {medicineSpecialtyHref && (
            <Link
              href={medicineSpecialtyHref}
              data-testid="legacy-specialty-medicine-cta"
              className="mt-3 inline-flex items-center gap-1.5 rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-white hover:bg-primary-hover"
            >
              Open {medicineSpecialty.label} in Medicine workspace
              <ExternalLink className="h-3.5 w-3.5" />
            </Link>
          )}
        </div>

        <div className="flex items-center gap-3">
          <Link href={`/ehr/${patientId}`} className="text-muted-foreground hover:text-muted-foreground">
            <ArrowLeft className="w-5 h-5" />
          </Link>
          <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${config.color}`}>
            <SpecIcon className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-lg font-semibold text-foreground">{config.label} Workspace</h2>
            <p className="text-sm text-muted-foreground">{config.description}</p>
          </div>
        </div>

        {/* Specialty Tools */}
        <div className="bg-card rounded-lg border border-border p-5">
          <h3 className="text-sm font-semibold text-foreground mb-3">Specialty Tools</h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            {config.tools.map((tool) => {
              const ToolIcon = tool.icon;
              const body = (
                <>
                  <ToolIcon className="w-5 h-5 text-muted-foreground mb-2" />
                  <p className="text-sm font-medium text-foreground">{tool.label}</p>
                  <p className="text-xs text-muted-foreground">{tool.description}</p>
                </>
              );
              // Only a tool that goes somewhere looks clickable. A cursor-pointer on a tile
              // that does nothing tells the clinician a capability exists when it does not.
              return tool.href ? (
                <Link
                  key={tool.label}
                  href={tool.href.replace("[patientId]", patientId)}
                  data-testid={`specialty-tool-${tool.label.toLowerCase().replace(/\s+/g, "-")}`}
                  className="bg-background rounded-lg border border-border p-4 hover:border-primary/25 transition-colors block"
                >
                  {body}
                </Link>
              ) : (
                <div key={tool.label} className="bg-background rounded-lg border border-border p-4 opacity-70">
                  {body}
                  <p className="mt-1 text-[11px] text-muted-foreground">Not available yet</p>
                </div>
              );
            })}
          </div>
        </div>

        {/* Assessments */}
        <div className="bg-card rounded-lg border border-border p-5">
          <h3 className="text-sm font-semibold text-foreground mb-3">Assessments</h3>
          <div className="space-y-2">
            {config.assessments.map((assessment) => (
              <div key={assessment} className="flex items-center gap-2 px-3 py-2 bg-background rounded-lg">
                <ClipboardList className="w-4 h-4 text-muted-foreground" />
                <span className="text-sm text-foreground">{assessment}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Order Sets */}
        <div className="bg-card rounded-lg border border-border p-5">
          <h3 className="text-sm font-semibold text-foreground mb-3">Common Order Sets</h3>
          <div className="flex flex-wrap gap-2">
            {config.orderSets.map((orderSet) => (
              <span key={orderSet}
                className="px-3 py-1.5 text-xs font-medium bg-muted text-muted-foreground rounded-lg">
                {orderSet}
              </span>
            ))}
          </div>
        </div>
      </div>
    </EHRLayout>
  );
}
