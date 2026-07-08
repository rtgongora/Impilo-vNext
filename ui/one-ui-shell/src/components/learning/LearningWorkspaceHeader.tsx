"use client";

import { useState } from "react";
import { Activity, ArrowLeft, GraduationCap, Loader2, Menu } from "lucide-react";
import { learningSections } from "./LearningWorkspace";
import { type SectionKey } from "@/components/learning/learningUtils";

export function LearningWorkspaceHeader({
  section,
  busy,
  historyLength,
  onSectionChange,
  onBack,
  onRefresh,
}: {
  section: SectionKey;
  busy: boolean;
  historyLength: number;
  onSectionChange: (section: SectionKey) => void;
  onBack: () => void;
  onRefresh: () => void;
}) {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const currentSection = learningSections.find((s) => s.key === section);

  return (
    <>
      {/* Header - Mobile/Tablet */}
      <header className="sticky top-0 z-40 border-b border-slate-200 bg-white shadow-sm">
        <div className="flex items-center justify-between gap-2 px-3 py-3 sm:px-4">
          {/* Brand */}
          <div className="flex items-center gap-2 min-w-0">
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-teal-50 text-teal-700 ring-1 ring-teal-100">
              <GraduationCap className="h-4 w-4" />
            </span>
            <div className="min-w-0 hidden sm:block">
              <h1 className="truncate text-sm font-bold leading-5 text-slate-950">Impilo Fundo</h1>
              <p className="truncate text-[10px] leading-3 text-slate-500">Learning</p>
            </div>
          </div>

          {/* Desktop Navigation - Hidden on Mobile */}
          <nav className="hidden md:flex gap-1" aria-label="Learning sections">
            {learningSections.map((item) => {
              const active = section === item.key;
              return (
                <button
                  key={item.key}
                  type="button"
                  onClick={() => onSectionChange(item.key)}
                  className={[
                    "inline-flex h-9 items-center justify-center gap-1.5 rounded-md px-3 text-xs font-semibold transition whitespace-nowrap",
                    active
                      ? "border border-teal-300 bg-teal-50 text-teal-900 shadow-sm"
                      : "border border-transparent text-slate-600 hover:bg-slate-50 hover:text-slate-900",
                  ].join(" ")}
                >
                  <span>{item.label}</span>
                </button>
              );
            })}
          </nav>

          {/* Right Actions */}
          <div className="flex items-center gap-1 flex-shrink-0">
            {historyLength > 0 && (
              <button
                onClick={onBack}
                className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 text-slate-600 hover:bg-slate-50 transition"
                title="Back"
                aria-label="Back"
              >
                <ArrowLeft className="h-4 w-4" />
              </button>
            )}
            <button
              onClick={onRefresh}
              disabled={busy}
              className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 text-slate-600 hover:bg-slate-50 transition disabled:opacity-50"
              title="Refresh"
              aria-label="Refresh"
            >
              {busy ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Activity className="h-4 w-4" />
              )}
            </button>
            {/* Mobile Menu Toggle */}
            <button
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="md:hidden inline-flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 text-slate-600 hover:bg-slate-50 transition"
              title="Menu"
              aria-label="Menu"
            >
              <Menu className="h-4 w-4" />
            </button>
          </div>
        </div>

        {/* Mobile Navigation Menu */}
        {mobileMenuOpen && (
          <nav className="md:hidden border-t border-slate-200 bg-slate-50 px-3 py-2 grid grid-cols-2 gap-1 sm:grid-cols-5">
            {learningSections.map((item) => {
              const active = section === item.key;
              return (
                <button
                  key={item.key}
                  type="button"
                  onClick={() => {
                    onSectionChange(item.key);
                    setMobileMenuOpen(false);
                  }}
                  className={[
                    "flex flex-col items-center justify-center gap-1 rounded-md p-2 text-xs font-medium transition",
                    active
                      ? "bg-teal-50 text-teal-700"
                      : "text-slate-600 hover:bg-white hover:text-slate-900",
                  ].join(" ")}
                >
                  <span className="text-[10px]">{item.label}</span>
                </button>
              );
            })}
          </nav>
        )}
      </header>

      {/* Mobile Bottom Tab Bar - Visible on small screens */}
      <nav className="md:hidden fixed bottom-0 left-0 right-0 z-40 border-t border-slate-200 bg-white flex gap-1 px-1 py-1 safe-area-inset-bottom">
        {learningSections.map((item) => {
          const active = section === item.key;
          return (
            <button
              key={item.key}
              type="button"
              onClick={() => onSectionChange(item.key)}
              className={[
                "flex-1 flex flex-col items-center justify-center gap-0.5 rounded-md py-2 px-1 text-xs font-medium transition",
                active
                  ? "bg-teal-50 text-teal-700"
                  : "text-slate-500 hover:bg-slate-50 hover:text-slate-700",
              ].join(" ")}
              aria-current={active ? "page" : undefined}
            >
              <span className="text-[10px] leading-3">{item.label}</span>
            </button>
          );
        })}
      </nav>
    </>
  );
}
