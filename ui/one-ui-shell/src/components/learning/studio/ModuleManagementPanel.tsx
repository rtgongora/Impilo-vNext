"use client";

import { useState } from "react";
import { Plus, ArrowLeft } from "lucide-react";
import { asArray, asText, type Row } from "@/components/learning/learningUtils";
import { SearchPanel } from "./SearchPanel";
import { ModuleCard } from "./ModuleCard";

interface Module {
  id: string;
  title: string;
  description?: string;
  lessons?: Array<{
    id: string;
    title: string;
    contentType: string;
    status: string;
  }>;
}

export interface ModuleManagementPanelProps {
  courseTitle: string;
  modules: Module[];
  onBack: () => void;
  onAddModule: () => void;
  onViewModule: (moduleId: string) => void;
  onEditModule: (moduleId: string) => void;
  onDeleteModule: (moduleId: string) => void;
  onAddSection?: (moduleId: string) => void;
  onEditSection?: (moduleId: string, sectionId: string) => void;
  onDeleteSection?: (moduleId: string, sectionId: string) => void;
  error?: string;
}

export function ModuleManagementPanel({
  courseTitle,
  modules,
  onBack,
  onAddModule,
  onViewModule,
  onEditModule,
  onDeleteModule,
  error,
}: ModuleManagementPanelProps) {

  return (
    <div className="space-y-4">
      {/* Header with Back Button */}
      <div className="flex items-center justify-between gap-3">
        <button
          onClick={onBack}
          className="inline-flex items-center gap-1.5 text-teal-700 hover:text-teal-900 font-semibold transition"
        >
          <ArrowLeft className="h-5 w-5" />
          Back to Courses
        </button>
        <h2 className="text-xl font-bold text-slate-950 truncate">{courseTitle}</h2>
      </div>

      {/* Error Banner */}
      {error && (
        <div className="p-4 rounded-lg border border-red-200 bg-red-50">
          <p className="text-sm font-medium text-red-700">{error}</p>
        </div>
      )}

      {/* Main Modules Panel */}
      <div className="bg-white rounded-lg border border-slate-200 shadow-sm">
        {/* Panel Header */}
        <div className="flex items-center justify-between gap-3 p-5 border-b border-slate-200">
          <h3 className="text-2xl font-bold text-slate-950">Modules</h3>
          <button
            onClick={onAddModule}
            className="inline-flex h-9 items-center gap-1.5 rounded-md bg-teal-700 text-white px-4 text-sm font-semibold hover:bg-teal-800 transition"
          >
            <Plus className="h-4 w-4" />
            Add Module
          </button>
        </div>

        {/* Modules Grid */}
        {modules.length > 0 ? (
          <div className="p-5 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {modules.map((module, idx) => (
              <ModuleCard
                key={module.id}
                module={module}
                index={idx}
                onView={() => onViewModule(module.id)}
                onEdit={() => onEditModule(module.id)}
                onDelete={() => onDeleteModule(module.id)}
              />
            ))}
          </div>
        ) : (
          <div className="p-12 text-center">
            <p className="text-lg font-semibold text-slate-700">No modules yet</p>
            <p className="text-sm text-slate-500 mt-2">Click "Add Module" to create your first module</p>
          </div>
        )}
      </div>
    </div>
  );
}
