"use client";

import { useState, useMemo } from "react";
import { Search } from "lucide-react";

interface Module {
  id: string;
  title: string;
  lessons?: Array<{
    id: string;
    title: string;
  }>;
}

export interface SearchPanelProps {
  modules: Module[];
  onSelectModule: (moduleId: string) => void;
  onSelectSection: (moduleId: string, sectionId: string) => void;
}

export function SearchPanel({ modules, onSelectModule, onSelectSection }: SearchPanelProps) {
  const [searchQuery, setSearchQuery] = useState("");

  const searchResults = useMemo(() => {
    if (!searchQuery.trim()) return [];

    const query = searchQuery.toLowerCase();
    const results: Array<{ type: "module" | "section"; moduleId: string; moduleTitle: string; sectionId?: string; sectionTitle?: string }> = [];

    modules.forEach((module) => {
      if (module.title.toLowerCase().includes(query)) {
        results.push({
          type: "module",
          moduleId: module.id,
          moduleTitle: module.title,
        });
      }

      module.lessons?.forEach((section) => {
        if (section.title.toLowerCase().includes(query)) {
          results.push({
            type: "section",
            moduleId: module.id,
            moduleTitle: module.title,
            sectionId: section.id,
            sectionTitle: section.title,
          });
        }
      });
    });

    return results;
  }, [modules, searchQuery]);

  const handleClear = () => {
    setSearchQuery("");
  };

  return (
    <div className="mb-5 pb-5 border-b border-slate-200">
      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
        <input
          type="text"
          placeholder="Search modules & sections..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="w-full pl-10 pr-3 py-2 rounded-md border border-slate-300 bg-white text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100 transition"
        />
        {searchQuery && (
          <button
            onClick={handleClear}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
            aria-label="Clear search"
          >
            ×
          </button>
        )}
      </div>

      {searchQuery && searchResults.length > 0 && (
        <div className="mt-3 space-y-2 max-h-80 overflow-y-auto">
          {searchResults.map((result, idx) => (
            <button
              key={`${result.moduleId}-${result.sectionId || "module"}-${idx}`}
              onClick={() => {
                if (result.type === "module") {
                  onSelectModule(result.moduleId);
                } else if (result.sectionId) {
                  onSelectSection(result.moduleId, result.sectionId);
                }
                setSearchQuery("");
              }}
              className="w-full text-left p-3 rounded-md bg-teal-50 border border-teal-200 hover:bg-teal-100 transition"
            >
              <div className="font-semibold text-sm text-teal-700">📚 {result.moduleTitle}</div>
              {result.type === "section" && result.sectionTitle && (
                <div className="text-xs text-slate-600 mt-1">└─ {result.sectionTitle}</div>
              )}
            </button>
          ))}
        </div>
      )}

      {searchQuery && searchResults.length === 0 && (
        <div className="mt-3 p-3 text-center text-sm text-slate-500">No results found</div>
      )}
    </div>
  );
}
