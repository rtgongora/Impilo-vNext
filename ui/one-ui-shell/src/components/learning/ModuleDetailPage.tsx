"use client";

import { useState } from "react";
import { ArrowLeft, Plus, Loader2, Trash2, Edit2 } from "lucide-react";
import { asArray, asText, type Row } from "@/components/learning/learningUtils";
import { SectionFormComponent } from "./SectionFormComponent";
import { DeleteConfirmationDialog } from "./DeleteConfirmationDialog";
import { apiClient } from "@/lib/api-client";

const FUNDO = "/internal/v1/learning/fundo";

export interface ModuleDetailPageProps {
  courseId: string;
  courseName: string;
  module: Row & { lessons?: Array<any> };
  onBack: () => void;
  onModuleUpdate: (updatedModule: Row) => void;
}

function apiErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error) return error.message;
  const record = error as Record<string, any>;
  const apiError = record.error as Record<string, any>;
  const message = (apiError?.message ?? record.message ?? fallback) as string;
  try {
    const parsed = JSON.parse(message);
    const parsedError = (parsed.error ?? {}) as Record<string, any>;
    return (parsedError.message ?? parsedError.code ?? message) as string;
  } catch {
    return message;
  }
}

export function ModuleDetailPage({
  courseId,
  courseName,
  module,
  onBack,
  onModuleUpdate,
}: ModuleDetailPageProps) {
  const [moduleTitle, setModuleTitle] = useState(asText(module.title || module.name, ""));
  const [moduleDescription, setModuleDescription] = useState(asText(module.description, ""));
  const [isEditingModule, setIsEditingModule] = useState(false);
  const [isSavingModule, setIsSavingModule] = useState(false);
  const [sections, setSections] = useState<Array<any>>(asArray(module.lessons || []));
  const [showAddSection, setShowAddSection] = useState(false);
  const [editingSection, setEditingSection] = useState<string | null>(null);
  const [deletingSection, setDeletingSection] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleSaveModule = async () => {
    if (!moduleTitle.trim()) {
      setError("Module title is required");
      return;
    }

    setIsSavingModule(true);
    setError(null);

    try {
      await apiClient.put(`${FUNDO}/modules/${module.id}`, {
        title: moduleTitle,
        description: moduleDescription || null,
      });

      const updatedModule = {
        ...module,
        title: moduleTitle,
        description: moduleDescription,
      };

      onModuleUpdate(updatedModule);
      setIsEditingModule(false);
    } catch (err) {
      const errorMsg = apiErrorMessage(err, "Failed to save module");
      setError(errorMsg);
      console.error("Module save error:", err);
    } finally {
      setIsSavingModule(false);
    }
  };

  const handleSectionSubmit = async (newSection: Row) => {
    try {
      if (editingSection) {
        // Update existing section
        await apiClient.put(`${FUNDO}/lessons/${editingSection}`, {
          title: newSection.title,
          contentType: newSection.contentType || newSection.type,
          contentBody: newSection.contentBody,
          contentRef: newSection.contentRef,
          contentFormat: newSection.contentFormat,
          contentBlocksJson: newSection.contentBlocksJson,
          status: newSection.status || "DRAFT",
        });

        setSections(
          sections.map((s) =>
            s.id === editingSection
              ? {
                  ...s,
                  title: newSection.title,
                  contentType: newSection.contentType || newSection.type,
                  status: newSection.status || "DRAFT",
                }
              : s
          )
        );
      } else {
        // Create new section
        const lessonResponse = await apiClient.post(
          `${FUNDO}/modules/${module.id}/lessons`,
          {
            title: newSection.title,
            contentType: newSection.contentType || newSection.type,
            contentBody: newSection.contentBody,
            contentRef: newSection.contentRef,
            contentFormat: newSection.contentFormat,
            contentBlocksJson: newSection.contentBlocksJson,
            sequence: sections.length + 1,
            required: newSection.required ?? true,
            status: newSection.status || "DRAFT",
          }
        );

        const lessonData = (lessonResponse as Record<string, any>).data as Record<string, any>;
        const createdLesson = (lessonData.lesson ?? lessonData) as Record<string, any>;
        const createdLessonId = createdLesson.id as string;

        if (!createdLessonId) {
          throw new Error("Backend did not return a lesson ID");
        }

        const newLesson = {
          id: createdLessonId,
          title: asText(createdLesson.title, asText(newSection.title, "Section")),
          contentType: asText(
            createdLesson.contentType,
            asText(newSection.contentType ?? newSection.type, "TEXT")
          ),
          sequence: (createdLesson.sequence as number) || sections.length + 1,
          status: asText(createdLesson.status, "DRAFT"),
        };

        setSections([...sections, newLesson]);
      }

      setShowAddSection(false);
      setEditingSection(null);
      setError(null);
    } catch (err) {
      const errorMsg = apiErrorMessage(err, "Failed to save section");
      setError(errorMsg);
      console.error("Section save error:", err);
    }
  };

  const handleDeleteSection = async () => {
    if (!deletingSection) return;

    try {
      await apiClient.delete(`${FUNDO}/lessons/${deletingSection}`);
      setSections(sections.filter((s) => s.id !== deletingSection));
      setError(null);
    } catch (err) {
      const errorMsg = apiErrorMessage(err, "Failed to delete section");
      setError(errorMsg);
      console.error("Section delete error:", err);
    } finally {
      setDeletingSection(null);
    }
  };

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between gap-3">
        <button
          onClick={onBack}
          className="inline-flex items-center gap-1.5 text-teal-700 hover:text-teal-900 font-semibold transition"
        >
          <ArrowLeft className="h-5 w-5" />
          Back to Modules
        </button>
        <h2 className="text-xl font-bold text-slate-950 truncate">{courseName}</h2>
      </div>

      {/* Error Banner */}
      {error && (
        <div className="p-4 rounded-lg border border-red-200 bg-red-50">
          <p className="text-sm font-medium text-red-700">{error}</p>
        </div>
      )}

      {/* Module Info Card */}
      <div className="rounded-lg border border-slate-200 bg-white shadow-sm p-5">
        <div className="flex items-start justify-between gap-3 mb-4">
          <div className="flex-1 min-w-0">
            {isEditingModule ? (
              <div className="space-y-3">
                <div>
                  <label className="block text-xs font-medium text-slate-600 mb-1">
                    Module Title *
                  </label>
                  <input
                    type="text"
                    value={moduleTitle}
                    onChange={(e) => setModuleTitle(e.target.value)}
                    className="w-full rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-600 mb-1">
                    Description (optional)
                  </label>
                  <textarea
                    value={moduleDescription}
                    onChange={(e) => setModuleDescription(e.target.value)}
                    rows={2}
                    className="w-full rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                  />
                </div>
              </div>
            ) : (
              <div>
                <h3 className="text-lg font-bold text-slate-950">{moduleTitle}</h3>
                {moduleDescription && (
                  <p className="text-sm text-slate-600 mt-2">{moduleDescription}</p>
                )}
              </div>
            )}
          </div>

          {isEditingModule ? (
            <div className="flex gap-2 shrink-0">
              <button
                onClick={handleSaveModule}
                disabled={isSavingModule}
                className="inline-flex h-9 items-center gap-1.5 rounded-md bg-teal-700 text-white px-4 text-sm font-semibold hover:bg-teal-800 transition disabled:opacity-60 disabled:cursor-not-allowed"
              >
                {isSavingModule ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    <span>Saving...</span>
                  </>
                ) : (
                  "Save"
                )}
              </button>
              <button
                onClick={() => {
                  setIsEditingModule(false);
                  setModuleTitle(asText(module.title || module.name, ""));
                  setModuleDescription(asText(module.description, ""));
                }}
                disabled={isSavingModule}
                className="inline-flex h-9 items-center gap-1.5 rounded-md border border-slate-200 text-slate-600 px-4 text-sm font-semibold hover:bg-slate-50 transition disabled:opacity-60 disabled:cursor-not-allowed"
              >
                Cancel
              </button>
            </div>
          ) : (
            <button
              onClick={() => setIsEditingModule(true)}
              className="inline-flex h-9 items-center gap-1.5 rounded-md border border-slate-200 text-slate-600 px-4 text-sm font-semibold hover:bg-slate-50 transition"
            >
              <Edit2 className="h-4 w-4" />
              <span>Edit Module</span>
            </button>
          )}
        </div>
      </div>

      {/* Sections Panel */}
      <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
        {/* Panel Header */}
        <div className="flex items-center justify-between gap-3 p-5 border-b border-slate-200">
          <h3 className="text-lg font-bold text-slate-950">Sections ({sections.length})</h3>
          {!showAddSection && !editingSection && (
            <button
              onClick={() => setShowAddSection(true)}
              className="inline-flex h-9 items-center gap-1.5 rounded-md bg-teal-700 text-white px-4 text-sm font-semibold hover:bg-teal-800 transition"
            >
              <Plus className="h-4 w-4" />
              Add Section
            </button>
          )}
        </div>

        {/* Section Form */}
        <div className="p-5">
          {(showAddSection || editingSection) && (
            <SectionFormComponent
              onCancel={() => {
                setShowAddSection(false);
                setEditingSection(null);
              }}
              onSubmit={handleSectionSubmit}
              courseId={courseId}
              sequenceNo={sections.length + 1}
              initialData={
                editingSection
                  ? sections.find((s) => s.id === editingSection)
                  : undefined
              }
            />
          )}

          {/* Sections List */}
          {sections.length > 0 ? (
            <div className="space-y-2 mt-4">
              {sections.map((section, index) => (
                <div
                  key={section.id}
                  className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 bg-slate-50 p-3 hover:shadow-md transition"
                >
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="inline-flex h-6 w-6 items-center justify-center rounded-full bg-teal-100 text-teal-700 text-xs font-bold shrink-0">
                        {index + 1}
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="font-semibold text-slate-950 text-sm truncate">
                          {section.title}
                        </p>
                        <p className="text-xs text-slate-500 mt-0.5">
                          {section.contentType}
                        </p>
                      </div>
                    </div>
                  </div>

                  <div className="flex gap-1 shrink-0">
                    <button
                      onClick={() => setEditingSection(section.id)}
                      className="inline-flex h-8 items-center gap-1 rounded-md border border-slate-200 bg-white px-2.5 text-xs font-medium text-slate-700 hover:bg-slate-50 transition"
                      title="Edit section"
                    >
                      <Edit2 className="h-3.5 w-3.5" />
                      Edit
                    </button>
                    <button
                      onClick={() => setDeletingSection(section.id)}
                      className="inline-flex h-8 items-center rounded-md border border-red-200 bg-red-50 px-2 text-red-700 hover:bg-red-100 transition"
                      title="Delete section"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            !showAddSection &&
            !editingSection && (
              <div className="text-center py-8 px-4 rounded-lg border border-dashed border-slate-200 bg-slate-50">
                <p className="text-sm font-semibold text-slate-900">No sections yet</p>
                <p className="text-xs text-slate-500 mt-1">Click "Add Section" to start building this module</p>
              </div>
            )
          )}
        </div>
      </div>

      {/* Delete Section Confirmation */}
      {deletingSection && (
        <DeleteConfirmationDialog
          title="Delete Section"
          message="Are you sure you want to delete this section? This action cannot be undone."
          onConfirm={handleDeleteSection}
          onCancel={() => setDeletingSection(null)}
        />
      )}
    </div>
  );
}
