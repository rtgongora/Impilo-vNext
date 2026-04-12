"use client";

/**
 * Unified Search — Health OS Experience Doctrine
 *
 * A global search overlay that searches across all planes:
 * - Patients (VITO)
 * - Providers (VARAPI)
 * - Facilities (TUSO)
 * - Clinical records (BUTANO/PCT)
 * - Medications (MSIKA)
 * - Knowledge (Clinical Knowledge Platform)
 * - Documents
 * - Orders/Results
 * - Coverage/Claims
 *
 * "The experience layer is searchable: users can actively look for
 *  health, wellness, lifestyle, diet, sleep, service, and marketplace
 *  information."
 *
 * Activated by Cmd+K / Ctrl+K or clicking the search icon.
 */

import { useState, useEffect, useCallback, useRef } from "react";
import { useRouter } from "next/navigation";
import {
  Search, X, User, Building2, Pill, FileText, Stethoscope,
  FlaskConical, ShieldCheck, CreditCard, BookOpen, Loader2,
  ArrowRight, Hash,
} from "lucide-react";
import { apiClient } from "@/lib/api-client";

// ── Types ────────────────────────────────────────────────────────────

interface SearchResult {
  id: string;
  type: "PATIENT" | "PROVIDER" | "FACILITY" | "MEDICATION" | "ORDER" | "DOCUMENT" | "KNOWLEDGE" | "CLAIM" | "ENCOUNTER";
  title: string;
  subtitle: string;
  href: string;
  icon: string;
  score: number;
}

interface SearchCategory {
  type: SearchResult["type"];
  label: string;
  icon: typeof User;
  color: string;
}

const CATEGORIES: SearchCategory[] = [
  { type: "PATIENT", label: "Patients", icon: User, color: "text-blue-600 bg-blue-50" },
  { type: "PROVIDER", label: "Providers", icon: Stethoscope, color: "text-green-600 bg-green-50" },
  { type: "FACILITY", label: "Facilities", icon: Building2, color: "text-amber-600 bg-amber-50" },
  { type: "MEDICATION", label: "Medications", icon: Pill, color: "text-purple-600 bg-purple-50" },
  { type: "ORDER", label: "Orders", icon: FlaskConical, color: "text-indigo-600 bg-indigo-50" },
  { type: "DOCUMENT", label: "Documents", icon: FileText, color: "text-gray-600 bg-gray-50" },
  { type: "KNOWLEDGE", label: "Knowledge", icon: BookOpen, color: "text-emerald-600 bg-emerald-50" },
  { type: "CLAIM", label: "Claims", icon: CreditCard, color: "text-rose-600 bg-rose-50" },
  { type: "ENCOUNTER", label: "Encounters", icon: ShieldCheck, color: "text-teal-600 bg-teal-50" },
];

const TYPE_ICON_MAP: Record<string, typeof User> = Object.fromEntries(
  CATEGORIES.map(c => [c.type, c.icon])
);

const TYPE_COLOR_MAP: Record<string, string> = Object.fromEntries(
  CATEGORIES.map(c => [c.type, c.color])
);

// ── Component ────────────────────────────────────────────────────────

interface UnifiedSearchProps {
  onClose?: () => void;
}

export function UnifiedSearch({ onClose }: UnifiedSearchProps) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [activeCategory, setActiveCategory] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const router = useRouter();

  // Focus input on mount
  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  // Debounced search
  useEffect(() => {
    if (query.length < 2) {
      setResults([]);
      return;
    }

    const timer = setTimeout(async () => {
      setLoading(true);
      try {
        const params = new URLSearchParams({ q: query });
        if (activeCategory) params.set("type", activeCategory);

        const response = await apiClient.get<{ data: SearchResult[] }>(
          `/internal/v1/search/federated?${params.toString()}`
        );
        setResults(response?.data ?? []);
        setSelectedIndex(0);
      } catch {
        setResults([]);
      } finally {
        setLoading(false);
      }
    }, 300);

    return () => clearTimeout(timer);
  }, [query, activeCategory]);

  // Keyboard navigation
  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setSelectedIndex(i => Math.min(i + 1, results.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setSelectedIndex(i => Math.max(i - 1, 0));
    } else if (e.key === "Enter" && results[selectedIndex]) {
      e.preventDefault();
      router.push(results[selectedIndex].href);
      onClose?.();
    } else if (e.key === "Escape") {
      onClose?.();
    }
  }, [results, selectedIndex, router, onClose]);

  const filteredResults = activeCategory
    ? results.filter(r => r.type === activeCategory)
    : results;

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-[15vh]">
      {/* Backdrop */}
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={onClose} />

      {/* Search modal */}
      <div className="relative w-full max-w-2xl mx-4 bg-white rounded-2xl shadow-2xl overflow-hidden">
        {/* Search input */}
        <div className="flex items-center gap-3 px-4 py-3 border-b">
          <Search className="h-5 w-5 text-gray-400 flex-shrink-0" />
          <input
            ref={inputRef}
            value={query}
            onChange={e => setQuery(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Search patients, providers, medications, knowledge..."
            className="flex-1 text-lg outline-none placeholder-gray-400"
          />
          {loading && <Loader2 className="h-5 w-5 text-violet-500 animate-spin" />}
          <kbd className="hidden sm:inline-block px-2 py-1 text-[10px] font-medium text-gray-400 bg-gray-100 rounded border">
            ESC
          </kbd>
        </div>

        {/* Category filters */}
        <div className="flex items-center gap-1 px-4 py-2 border-b overflow-x-auto">
          <button
            onClick={() => setActiveCategory(null)}
            className={`px-2.5 py-1 rounded-full text-xs font-medium transition ${
              !activeCategory ? "bg-violet-100 text-violet-700" : "text-gray-500 hover:bg-gray-100"
            }`}
          >
            All
          </button>
          {CATEGORIES.map(cat => (
            <button
              key={cat.type}
              onClick={() => setActiveCategory(activeCategory === cat.type ? null : cat.type)}
              className={`px-2.5 py-1 rounded-full text-xs font-medium transition whitespace-nowrap ${
                activeCategory === cat.type ? "bg-violet-100 text-violet-700" : "text-gray-500 hover:bg-gray-100"
              }`}
            >
              {cat.label}
            </button>
          ))}
        </div>

        {/* Results */}
        <div className="max-h-[50vh] overflow-y-auto">
          {query.length < 2 ? (
            <div className="p-8 text-center">
              <Search className="h-10 w-10 text-gray-200 mx-auto mb-2" />
              <p className="text-sm text-gray-400">Type at least 2 characters to search across the Health OS</p>
            </div>
          ) : filteredResults.length === 0 && !loading ? (
            <div className="p-8 text-center">
              <Hash className="h-10 w-10 text-gray-200 mx-auto mb-2" />
              <p className="text-sm text-gray-400">No results found for &ldquo;{query}&rdquo;</p>
            </div>
          ) : (
            <div className="py-2">
              {filteredResults.map((result, index) => {
                const Icon = TYPE_ICON_MAP[result.type] ?? FileText;
                const color = TYPE_COLOR_MAP[result.type] ?? "text-gray-600 bg-gray-50";
                const [textColor, bgColor] = color.split(" ");

                return (
                  <button
                    key={result.id}
                    onClick={() => {
                      router.push(result.href);
                      onClose?.();
                    }}
                    className={`w-full flex items-center gap-3 px-4 py-2.5 text-left transition ${
                      index === selectedIndex ? "bg-violet-50" : "hover:bg-gray-50"
                    }`}
                  >
                    <div className={`flex-shrink-0 p-2 rounded-lg ${bgColor}`}>
                      <Icon className={`h-4 w-4 ${textColor}`} />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-gray-900 truncate">{result.title}</p>
                      <p className="text-xs text-gray-500 truncate">{result.subtitle}</p>
                    </div>
                    <div className="flex items-center gap-2 flex-shrink-0">
                      <span className="text-[10px] text-gray-400 uppercase">{result.type}</span>
                      <ArrowRight className="h-3.5 w-3.5 text-gray-300" />
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="px-4 py-2 border-t bg-gray-50 flex items-center justify-between text-[10px] text-gray-400">
          <span>↑↓ Navigate · Enter Select · Esc Close</span>
          <span>Powered by Impilo Health OS</span>
        </div>
      </div>
    </div>
  );
}

// ── Global keyboard shortcut hook ────────────────────────────────────

export function useSearchShortcut(onOpen: () => void) {
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if ((e.metaKey || e.ctrlKey) && e.key === "k") {
        e.preventDefault();
        onOpen();
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onOpen]);
}
