"use client";

import { FormEvent, ReactNode } from "react";
import { CheckCircle2, Loader2, X } from "lucide-react";
import { type ModalKey, type Row, type SectionKey } from "@/components/learning/learningUtils";
import { LearningWorkspaceHeader } from "./LearningWorkspaceHeader";
import { Studio } from "./StudioPanel";
import { Learner } from "./LearnerPanel";
import { Overview } from "./OverviewPanel";
import { Reports } from "./ReportsPanel";
import { ModalFields, modalTitle } from "./FormComponents";

export const learningSections: Array<{ key: SectionKey; label: string; icon: any; detail: string }> = [
  { key: "overview", label: "Overview", icon: null, detail: "Your learning dashboard" },
  { key: "learner", label: "My Learning", icon: null, detail: "Your progress & browse" },
  { key: "studio", label: "Studio", icon: null, detail: "Author content" },
  { key: "reports", label: "Reports", icon: null, detail: "Analytics & insights" },
];

export function LearningWorkspaceMain({
  section,
  data,
  openSection,
  setModal,
  canLearn,
  canUseStudio,
  canViewReports,
}: {
  section: SectionKey;
  data: Record<string, unknown>;
  openSection: (section: SectionKey) => void;
  setModal: (modal: ModalKey, defaults?: Row) => void;
  canLearn: boolean;
  canUseStudio: boolean;
  canViewReports: boolean;
}) {
  if (section === "learner" && canLearn) return <Learner data={data} openSection={openSection} setModal={setModal} />;
  if (section === "studio" && canUseStudio) return <Studio data={data} setModal={setModal} />;
  if (section === "reports" && canViewReports) return <Reports data={data} />;
  return (
    <Overview
      data={data}
      openSection={openSection}
      setModal={setModal}
      canLearn={canLearn}
      canUseStudio={canUseStudio}
    />
  );
}

export function Banner({ tone, children }: { tone: "error" | "success"; children: ReactNode }) {
  const classes =
    tone === "error"
      ? "border-red-200 bg-red-50 text-red-800"
      : "border-emerald-200 bg-emerald-50 text-emerald-800";

  return <div className={["rounded-md border px-3 py-2 text-sm font-medium", classes].join(" ")}>{children}</div>;
}

export function LearningModal({ title, busy, onClose, onSubmit, children }: { title: string; busy: boolean; onClose: () => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void; children: ReactNode }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 p-4">
      <form onSubmit={onSubmit} className="w-full max-w-2xl overflow-hidden rounded-lg bg-white shadow-xl">
        <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
          <h2 className="text-base font-semibold text-slate-950">{title}</h2>
          <button type="button" onClick={onClose} className="rounded-md p-1 text-slate-500 hover:bg-slate-100" aria-label="Close">
            <X className="h-5 w-5" />
          </button>
        </div>
        <div className="max-h-[70vh] overflow-y-auto p-4">
          <div className="grid gap-3 sm:grid-cols-2">{children}</div>
        </div>
        <div className="flex justify-end gap-2 border-t border-slate-200 px-4 py-3">
          <button type="button" onClick={onClose} className="rounded-md border border-slate-200 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50">
            Cancel
          </button>
          <button type="submit" disabled={busy} className="inline-flex items-center gap-2 rounded-md bg-teal-700 px-4 py-2 text-sm font-medium text-white hover:bg-teal-800 disabled:opacity-60">
            {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <CheckCircle2 className="h-4 w-4" />} Save
          </button>
        </div>
      </form>
    </div>
  );
}

export { ModalFields, modalTitle };
export { LearningWorkspaceHeader };
