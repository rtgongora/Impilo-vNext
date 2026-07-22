"use client";

import { Edit, Trash2, Eye } from "lucide-react";

interface ModuleCardProps {
  module: {
    id: string;
    title: string;
    description?: string;
    lessons?: Array<{ id: string; title: string }>;
  };
  index: number;
  onView: () => void;
  onEdit: () => void;
  onDelete: () => void;
}

export function ModuleCard({
  module,
  index,
  onView,
  onEdit,
  onDelete,
}: ModuleCardProps) {
  const sectionCount = (module.lessons || []).length;

  return (
    <div className="bg-white rounded-lg border border-slate-200 hover:border-teal-300 hover:shadow-md transition overflow-hidden">
      <div className="p-4 space-y-3">
        {/* Header */}
        <div className="flex items-start gap-3">
          <span className="inline-flex h-7 w-7 items-center justify-center rounded-full bg-teal-700 text-white text-sm font-bold shrink-0">
            {index + 1}
          </span>
          <div className="flex-1 min-w-0">
            <h3 className="font-semibold text-slate-950 truncate">{module.title}</h3>
            {module.description && (
              <p className="text-xs text-slate-500 truncate mt-1">{module.description}</p>
            )}
            <p className="text-xs text-slate-500 mt-1.5 font-medium">
              {sectionCount} section{sectionCount !== 1 ? "s" : ""}
            </p>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex gap-2">
          <button
            onClick={onView}
            className="flex-1 h-8 inline-flex items-center justify-center gap-1.5 rounded-md bg-teal-700 text-white text-xs font-semibold hover:bg-teal-800 transition"
            title="View/Edit module and sections"
          >
            <Eye className="h-3.5 w-3.5" />
            <span>View/Edit</span>
          </button>
          <button
            onClick={onEdit}
            className="h-8 inline-flex items-center justify-center rounded-md border border-slate-200 bg-white text-slate-700 text-xs font-semibold hover:bg-slate-50 transition px-2.5"
            title="Edit module metadata"
          >
            <Edit className="h-3.5 w-3.5" />
          </button>
          <button
            onClick={onDelete}
            className="h-8 inline-flex items-center justify-center rounded-md border border-red-200 bg-red-50 text-red-700 text-xs font-semibold hover:bg-red-100 transition px-2.5"
            title="Delete module"
          >
            <Trash2 className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
    </div>
  );
}
