"use client";

/**
 * InvestigationSearch — search-as-you-type investigation picker with running cart,
 * common sets, and CDS suggestions.
 *
 * UX model:
 * 1. User types in search box → dropdown shows matching investigations as they type
 * 2. User clicks an investigation → it's added to the running cart (right side)
 * 3. User can keep searching and adding more investigations
 * 4. Common/frequent investigations shown as quick-pick chips
 * 5. CDS suggestions shown based on patient context (complaints, history, encounter)
 * 6. Order sets available as one-click bundles
 * 7. Cart is always visible — shows what's been selected, with remove buttons
 */

import { useState, useMemo, useCallback, useRef, useEffect } from "react";
import {
  Search, Plus, X, Zap, FlaskConical, Image, Heart,
  ChevronDown, Package, Sparkles,
} from "lucide-react";
import {
  searchInvestigations,
  getCommonInvestigations,
  getClinicalDomains,
  TYPE_LABELS,
  DOMAIN_LABELS,
  type Investigation,
  type InvestigationType,
  type ClinicalDomain,
} from "@/data/investigationCatalog";

// ── Types ───────────────────────────────────────────────────────────

export interface SelectedInvestigation {
  investigation: Investigation;
  priority: "STAT" | "URGENT" | "ROUTINE";
  clinicalNotes?: string;
}

interface CDSSuggestion {
  investigation: Investigation;
  reason: string;
  confidence: "HIGH" | "MEDIUM" | "LOW";
}

interface InvestigationSearchProps {
  /** Currently selected investigations. */
  selected: SelectedInvestigation[];
  /** Called when an investigation is added. */
  onAdd: (item: SelectedInvestigation) => void;
  /** Called when an investigation is removed. */
  onRemove: (code: string) => void;
  /** CDS suggestions based on patient context. */
  cdsSuggestions?: CDSSuggestion[];
  /** Default priority for new items. */
  defaultPriority?: "STAT" | "URGENT" | "ROUTINE";
}

// ── Component ───────────────────────────────────────────────────────

const TYPE_ICONS: Record<InvestigationType, typeof FlaskConical> = {
  LABORATORY: FlaskConical,
  IMAGING: Image,
  POINT_OF_CARE: Zap,
  CARDIOLOGY_DX: Heart,
  PULMONARY_DX: FlaskConical,
  ENDOSCOPY: FlaskConical,
  PATHOLOGY: FlaskConical,
  PROCEDURE: FlaskConical,
};

const TYPE_COLORS: Record<InvestigationType, string> = {
  LABORATORY: "bg-primary-soft text-primary",
  IMAGING: "bg-purple-100 text-warning-foreground",
  POINT_OF_CARE: "bg-amber-100 text-warning-foreground",
  CARDIOLOGY_DX: "bg-red-100 text-danger",
  PULMONARY_DX: "bg-teal-100 text-teal-700",
  ENDOSCOPY: "bg-indigo-100 text-primary-hover",
  PATHOLOGY: "bg-pink-100 text-pink-700",
  PROCEDURE: "bg-neutral-100 text-foreground",
};

const PRIORITY_STYLES = {
  STAT: "bg-red-600 text-white",
  URGENT: "bg-amber-500 text-white",
  ROUTINE: "bg-neutral-100 text-foreground",
};

export function InvestigationSearch({
  selected,
  onAdd,
  onRemove,
  cdsSuggestions = [],
  defaultPriority = "ROUTINE",
}: InvestigationSearchProps) {
  const [query, setQuery] = useState("");
  const [showDropdown, setShowDropdown] = useState(false);
  const [domainFilter, setDomainFilter] = useState<ClinicalDomain | "ALL">("ALL");
  const [showCommon, setShowCommon] = useState(true);
  const inputRef = useRef<HTMLInputElement>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const selectedCodes = useMemo(() => new Set(selected.map((s) => s.investigation.code)), [selected]);

  // Search results
  const searchResults = useMemo(() => {
    if (query.length < 2) return [];
    let results = searchInvestigations(query);
    if (domainFilter !== "ALL") {
      results = results.filter((r) => r.domain === domainFilter);
    }
    return results.slice(0, 15);
  }, [query, domainFilter]);

  // Common investigations
  const commonInvestigations = useMemo(() => getCommonInvestigations(), []);

  // Handle adding
  const handleAdd = useCallback(
    (investigation: Investigation) => {
      if (selectedCodes.has(investigation.code)) return;
      onAdd({ investigation, priority: defaultPriority });
      setQuery("");
      setShowDropdown(false);
      inputRef.current?.focus();
    },
    [onAdd, selectedCodes, defaultPriority],
  );

  // Close dropdown on outside click
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setShowDropdown(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
      {/* ── Left: Search + Browse + CDS ──────────────────────────── */}
      <div className="lg:col-span-2 space-y-4">
        {/* Search box */}
        <div className="relative" ref={dropdownRef}>
          <div className="relative">
            <Search className="absolute left-3 top-3 h-5 w-5 text-muted-foreground" />
            <input
              ref={inputRef}
              value={query}
              onChange={(e) => { setQuery(e.target.value); setShowDropdown(true); }}
              onFocus={() => query.length >= 2 && setShowDropdown(true)}
              placeholder="Search investigations — type name, code, or keyword..."
              className="w-full rounded-xl border border-border pl-11 pr-4 py-3 text-sm focus:outline-none focus:border-impilo-400 focus:ring-2 focus:ring-impilo-100"
              autoComplete="off"
            />
            {query && (
              <button onClick={() => { setQuery(""); setShowDropdown(false); }} className="absolute right-3 top-3 text-muted-foreground hover:text-muted-foreground">
                <X className="h-5 w-5" />
              </button>
            )}
          </div>

          {/* Search dropdown */}
          {showDropdown && searchResults.length > 0 && (
            <div className="absolute z-50 mt-1 w-full rounded-xl border border-border bg-card shadow-xl max-h-80 overflow-y-auto">
              {/* Domain filter chips */}
              <div className="sticky top-0 bg-card border-b border-border px-3 py-2 flex gap-1 flex-wrap">
                <button
                  onClick={() => setDomainFilter("ALL")}
                  className={`rounded-full px-2 py-0.5 text-[10px] font-medium ${domainFilter === "ALL" ? "bg-primary text-white" : "bg-neutral-100 text-muted-foreground"}`}
                >All</button>
                {getClinicalDomains().slice(0, 8).map((d) => (
                  <button
                    key={d}
                    onClick={() => setDomainFilter(d)}
                    className={`rounded-full px-2 py-0.5 text-[10px] font-medium ${domainFilter === d ? "bg-primary text-white" : "bg-neutral-100 text-muted-foreground"}`}
                  >{DOMAIN_LABELS[d]}</button>
                ))}
              </div>
              {searchResults.map((inv) => {
                const isSelected = selectedCodes.has(inv.code);
                const Icon = TYPE_ICONS[inv.type];
                return (
                  <button
                    key={inv.code}
                    onClick={() => !isSelected && handleAdd(inv)}
                    disabled={isSelected}
                    className={`w-full text-left px-4 py-2.5 flex items-center gap-3 border-b border-gray-50 last:border-0 transition-colors ${
                      isSelected ? "bg-primary-soft opacity-50 cursor-not-allowed" : "hover:bg-primary-soft cursor-pointer"
                    }`}
                  >
                    <div className={`h-8 w-8 rounded-lg flex items-center justify-center ${TYPE_COLORS[inv.type]}`}>
                      <Icon className="h-4 w-4" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-foreground truncate">{inv.name}</p>
                      <p className="text-[11px] text-muted-foreground">
                        {inv.shortName} · {DOMAIN_LABELS[inv.domain]} · {TYPE_LABELS[inv.type]}
                        {inv.isPanel && " · Panel"}
                        {inv.specimen && ` · ${inv.specimen}`}
                      </p>
                    </div>
                    {isSelected ? (
                      <span className="text-[10px] text-primary font-medium">Added</span>
                    ) : (
                      <Plus className="h-4 w-4 text-muted-foreground" />
                    )}
                  </button>
                );
              })}
            </div>
          )}

          {showDropdown && query.length >= 2 && searchResults.length === 0 && (
            <div className="absolute z-50 mt-1 w-full rounded-xl border border-border bg-card shadow-xl p-4 text-center text-sm text-muted-foreground">
              No investigations match &quot;{query}&quot;
            </div>
          )}
        </div>

        {/* CDS Suggestions */}
        {cdsSuggestions.length > 0 && (
          <div className="rounded-xl border border-success/25 bg-success-soft p-4">
            <div className="flex items-center gap-2 mb-2">
              <Sparkles className="h-4 w-4 text-primary" />
              <h3 className="text-sm font-semibold text-primary-hover">Clinical Decision Support</h3>
            </div>
            <p className="text-xs text-primary-hover mb-3">Based on presenting complaints, history, and encounter findings:</p>
            <div className="space-y-2">
              {cdsSuggestions.map((s) => {
                const isSelected = selectedCodes.has(s.investigation.code);
                return (
                  <div key={s.investigation.code} className="flex items-center gap-3 bg-card rounded-lg p-2.5 border border-emerald-100">
                    <div className={`h-7 w-7 rounded flex items-center justify-center text-xs font-bold ${
                      s.confidence === "HIGH" ? "bg-emerald-600 text-white" : s.confidence === "MEDIUM" ? "bg-amber-500 text-white" : "bg-gray-300 text-foreground"
                    }`}>{s.confidence[0]}</div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-foreground">{s.investigation.name}</p>
                      <p className="text-xs text-primary-hover">{s.reason}</p>
                    </div>
                    <button
                      onClick={() => !isSelected && handleAdd(s.investigation)}
                      disabled={isSelected}
                      className={`rounded-lg px-3 py-1 text-xs font-medium transition-colors ${
                        isSelected ? "bg-neutral-100 text-muted-foreground" : "bg-emerald-600 text-white hover:bg-emerald-700"
                      }`}
                    >{isSelected ? "Added" : "Accept"}</button>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* Common investigations */}
        <div>
          <button onClick={() => setShowCommon(!showCommon)} className="flex items-center gap-1 text-sm font-medium text-foreground mb-2">
            <ChevronDown className={`h-4 w-4 transition-transform ${showCommon ? "" : "-rotate-90"}`} />
            Common Investigations
          </button>
          {showCommon && (
            <div className="flex flex-wrap gap-2">
              {commonInvestigations.map((inv) => {
                const isSelected = selectedCodes.has(inv.code);
                return (
                  <button
                    key={inv.code}
                    onClick={() => !isSelected && handleAdd(inv)}
                    disabled={isSelected}
                    className={`rounded-full px-3 py-1.5 text-xs font-medium border transition-all ${
                      isSelected
                        ? "bg-primary-soft border-primary/25 text-impilo-300 cursor-not-allowed"
                        : "bg-card border-border text-foreground hover:border-impilo-400 hover:bg-primary-soft"
                    }`}
                  >
                    {isSelected && "✓ "}{inv.shortName}
                  </button>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {/* ── Right: Running Cart ──────────────────────────────────── */}
      <div className="rounded-xl border border-border bg-card shadow-sm">
        <div className="flex items-center justify-between px-4 py-3 border-b border-border bg-background rounded-t-xl">
          <div className="flex items-center gap-2">
            <Package className="h-4 w-4 text-primary" />
            <h3 className="text-sm font-semibold text-foreground">Order Cart</h3>
          </div>
          <span className="rounded-full bg-primary text-white text-xs font-bold px-2 py-0.5">
            {selected.length}
          </span>
        </div>
        <div className="max-h-[400px] overflow-y-auto divide-y divide-gray-50">
          {selected.length === 0 ? (
            <div className="p-6 text-center text-sm text-muted-foreground">
              <Search className="h-6 w-6 mx-auto mb-2 text-muted-foreground" />
              Search and select investigations above
            </div>
          ) : (
            selected.map((item) => {
              const Icon = TYPE_ICONS[item.investigation.type];
              return (
                <div key={item.investigation.code} className="px-4 py-2.5 flex items-center gap-2">
                  <div className={`h-6 w-6 rounded flex items-center justify-center ${TYPE_COLORS[item.investigation.type]}`}>
                    <Icon className="h-3 w-3" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-xs font-medium text-foreground truncate">{item.investigation.shortName}</p>
                    <p className="text-[10px] text-muted-foreground">{DOMAIN_LABELS[item.investigation.domain]}</p>
                  </div>
                  <span className={`rounded-full px-1.5 py-0.5 text-[9px] font-bold ${PRIORITY_STYLES[item.priority]}`}>
                    {item.priority}
                  </span>
                  <button onClick={() => onRemove(item.investigation.code)} className="text-muted-foreground hover:text-red-500">
                    <X className="h-3.5 w-3.5" />
                  </button>
                </div>
              );
            })
          )}
        </div>
        {selected.length > 0 && (
          <div className="px-4 py-3 border-t border-border bg-background rounded-b-xl">
            <p className="text-[10px] text-muted-foreground mb-1">
              {selected.filter((s) => s.investigation.type === "LABORATORY").length} lab ·{" "}
              {selected.filter((s) => s.investigation.type === "IMAGING").length} imaging ·{" "}
              {selected.filter((s) => !["LABORATORY", "IMAGING"].includes(s.investigation.type)).length} other
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
