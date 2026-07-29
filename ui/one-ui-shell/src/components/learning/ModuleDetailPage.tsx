"use client";

import { useState } from "react";
import { ArrowLeft, Plus, Loader2, Trash2, Edit2, X, CheckCircle2 } from "lucide-react";
import { asArray, asText, type Row } from "@/components/learning/learningUtils";
import { SectionFormComponent } from "./SectionFormComponent";
import { DeleteConfirmationDialog } from "./DeleteConfirmationDialog";
import { apiClient } from "@/lib/api-client";

const FUNDO = "/internal/v1/learning/fundo";

export interface ModuleDetailPageProps {
  courseId: string;
  courseName: string;
  module: Row & { lessons?: Row[] };
  onBack: () => void;
  onModuleUpdate: (updatedModule: Row) => void;
}

function apiErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error) return error.message;
  const record =
    error && typeof error === "object" ? (error as Record<string, unknown>) : {};
  const apiError =
    record.error && typeof record.error === "object"
      ? (record.error as Record<string, unknown>)
      : {};
  const message = asText(apiError.message ?? record.message, fallback);
  try {
    const parsed = JSON.parse(message) as Record<string, unknown>;
    const parsedError =
      parsed.error && typeof parsed.error === "object"
        ? (parsed.error as Record<string, unknown>)
        : {};
    return asText(parsedError.message ?? parsedError.code, message);
  } catch {
    return message;
  }
}

function unwrapLesson(value: unknown) {
  const envelope =
    value && typeof value === "object" ? (value as Record<string, unknown>) : {};
  const data =
    envelope.data && typeof envelope.data === "object"
      ? (envelope.data as Row)
      : envelope;
  return data.lesson && typeof data.lesson === "object" ? (data.lesson as Row) : data;
}

function sectionTypeLabel(value: unknown) {
  return asText(value, "TEXT").replace(/_/g, " ");
}

function sectionSummary(section: Row) {
  const contentBody = asText(section.contentBody, "");
  if (contentBody) return contentBody;

  const contentRef = asText(section.contentRef, "");
  if (contentRef) return contentRef;

  const blocks = asText(section.contentBlocksJson, "");
  if (!blocks) return "No content preview yet";

  try {
    const parsed = JSON.parse(blocks) as Record<string, unknown>;
    return asText(parsed.description ?? parsed.transcript, "Structured content");
  } catch {
    return "Structured content";
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
  const [sections, setSections] = useState<Row[]>(asArray(module.lessons || []));
  const [showAddSection, setShowAddSection] = useState(false);
  const [editingSection, setEditingSection] = useState<string | null>(null);
  const [deletingSection, setDeletingSection] = useState<string | null>(null);
  const [pendingSectionSave, setPendingSectionSave] = useState<{
    section: Row;
    editingSectionId: string | null;
  } | null>(null);
  const [isSavingSection, setIsSavingSection] = useState(false);
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

  const handleSectionSubmit = (newSection: Row) => {
    setPendingSectionSave({
      section: newSection,
      editingSectionId: editingSection,
    });
  };

  const confirmSectionSave = async () => {
    if (!pendingSectionSave) return;

    const newSection = pendingSectionSave.section;
    const editingSectionId = pendingSectionSave.editingSectionId;
    setIsSavingSection(true);

    try {
      if (editingSectionId) {
        // Update existing section
        const lessonResponse = await apiClient.put(`${FUNDO}/lessons/${editingSectionId}`, {
          title: newSection.title,
          contentType: newSection.contentType || newSection.type,
          contentBody: newSection.contentBody,
          contentRef: newSection.contentRef,
          contentFormat: newSection.contentFormat,
          contentBlocksJson: newSection.contentBlocksJson,
          status: newSection.status || "DRAFT",
        });

        const savedLesson = unwrapLesson(lessonResponse);
        const nextSections = sections.map((s) =>
            s.id === editingSectionId
              ? {
                  ...s,
                  title: asText(savedLesson.title, asText(newSection.title, "Section")),
                  contentType: asText(
                    savedLesson.contentType,
                    asText(newSection.contentType ?? newSection.type, "TEXT")
                  ),
                  contentBody: savedLesson.contentBody ?? newSection.contentBody,
                  contentRef: savedLesson.contentRef ?? newSection.contentRef,
                  contentFormat: savedLesson.contentFormat ?? newSection.contentFormat,
                  contentBlocksJson: savedLesson.contentBlocksJson ?? newSection.contentBlocksJson,
                  status: asText(savedLesson.status, asText(newSection.status, "DRAFT")),
                }
              : s
          );

        setSections(nextSections);
        onModuleUpdate({ ...module, lessons: nextSections });
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

        const createdLesson = unwrapLesson(lessonResponse);
        const createdLessonId = asText(createdLesson.id, "");

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
          contentBody: createdLesson.contentBody ?? newSection.contentBody,
          contentRef: createdLesson.contentRef ?? newSection.contentRef,
          contentFormat: createdLesson.contentFormat ?? newSection.contentFormat,
          contentBlocksJson: createdLesson.contentBlocksJson ?? newSection.contentBlocksJson,
          sequence: (createdLesson.sequence as number) || sections.length + 1,
          status: asText(createdLesson.status, "DRAFT"),
        };

        const nextSections = [...sections, newLesson];
        setSections(nextSections);
        onModuleUpdate({ ...module, lessons: nextSections });
      }

      setShowAddSection(false);
      setEditingSection(null);
      setPendingSectionSave(null);
      setError(null);
    } catch (err) {
      const errorMsg = apiErrorMessage(err, "Failed to save section");
      setError(errorMsg);
      setPendingSectionSave(null);
      console.error("Section save error:", err);
    } finally {
      setIsSavingSection(false);
    }
  };

  const handleDeleteSection = async () => {
    if (!deletingSection) return;

    try {
      await apiClient.delete(`${FUNDO}/lessons/${deletingSection}`);
      const nextSections = sections.filter((s) => s.id !== deletingSection);
      setSections(nextSections);
      onModuleUpdate({ ...module, lessons: nextSections });
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
              onClick={() => {
                setEditingSection(null);
                setShowAddSection(true);
              }}
              className="inline-flex h-9 items-center gap-1.5 rounded-md bg-teal-700 text-white px-4 text-sm font-semibold hover:bg-teal-800 transition"
            >
              <Plus className="h-4 w-4" />
              Add Section
            </button>
          )}
        </div>

        <div className="p-5">
          {/* Sections List */}
          {sections.length > 0 ? (
            <div className="space-y-2">
              {sections.map((section, index) => (
                <div
                  key={asText(section.id, String(index))}
                  className="flex flex-col gap-3 rounded-lg border border-slate-200 bg-slate-50 p-3 transition hover:border-teal-200 hover:bg-white hover:shadow-sm sm:flex-row sm:items-center sm:justify-between"
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex items-start gap-3">
                      <span className="mt-0.5 inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-teal-100 text-xs font-bold text-teal-700">
                        {index + 1}
                      </span>
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <p className="min-w-0 max-w-full truncate text-sm font-semibold text-slate-950">
                            {asText(section.title, "Untitled section")}
                          </p>
                          <span className="inline-flex rounded-full bg-white px-2 py-0.5 text-[11px] font-semibold uppercase tracking-normal text-slate-600 ring-1 ring-slate-200">
                            {sectionTypeLabel(section.contentType)}
                          </span>
                          {asText(section.status, "") && (
                            <span className="inline-flex rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] font-semibold uppercase tracking-normal text-emerald-700 ring-1 ring-emerald-100">
                              {asText(section.status)}
                            </span>
                          )}
                        </div>
                        <p className="mt-1 line-clamp-2 text-xs leading-5 text-slate-600">
                          {sectionSummary(section)}
                        </p>
                      </div>
                    </div>
                  </div>

                  <div className="flex shrink-0 justify-end gap-1 sm:self-center">
                    <button
                      onClick={() => {
                        setShowAddSection(false);
                        setEditingSection(asText(section.id, ""));
                      }}
                      className="inline-flex h-8 items-center gap-1 rounded-md border border-slate-200 bg-white px-2.5 text-xs font-medium text-slate-700 hover:bg-slate-50 transition"
                      title="Edit section"
                    >
                      <Edit2 className="h-3.5 w-3.5" />
                      Edit
                    </button>
                    <button
                      onClick={() => setDeletingSection(asText(section.id, ""))}
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
                <p className="text-xs text-slate-500 mt-1">Click Add Section to start building this module</p>
              </div>
            )
          )}
        </div>
      </div>

      {/* Section Form Modal */}
      {(showAddSection || editingSection) && (
        <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/45 p-4">
          <div className="my-6 flex max-h-[calc(100vh-3rem)] w-full max-w-4xl flex-col rounded-lg bg-white shadow-xl">
            <div className="flex shrink-0 items-center justify-between border-b border-slate-200 px-4 py-3">
              <h2 className="text-base font-semibold text-slate-950">
                {editingSection ? "Edit Section" : "Add Section"}
              </h2>
              <button
                type="button"
                onClick={() => {
                  setShowAddSection(false);
                  setEditingSection(null);
                  setPendingSectionSave(null);
                }}
                className="rounded-md p-1 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
                aria-label="Close section form"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto">
              <div className="[&>div]:mb-0 [&>div]:rounded-none [&>div]:border-0 [&>div]:bg-white [&>div]:p-4">
                {error && (
                  <div className="mx-4 mt-4 rounded-md border border-red-200 bg-red-50 p-3">
                    <p className="text-sm font-medium text-red-700">{error}</p>
                  </div>
                )}
                <SectionFormComponent
                  onCancel={() => {
                    setShowAddSection(false);
                    setEditingSection(null);
                    setPendingSectionSave(null);
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
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Save Section Confirmation */}
      {pendingSectionSave && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
          <div className="relative w-full max-w-md rounded-lg bg-white shadow-lg">
            <button
              type="button"
              onClick={() => setPendingSectionSave(null)}
              disabled={isSavingSection}
              className="absolute right-3 top-3 inline-flex h-7 w-7 items-center justify-center rounded-md text-slate-400 transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-60"
              title="Close dialog"
            >
              <X className="h-4 w-4" />
            </button>

            <div className="p-6">
              <div className="mb-4 flex items-start gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-teal-50">
                  <CheckCircle2 className="h-5 w-5 text-teal-700" />
                </div>
                <div className="flex-1">
                  <h2 className="text-lg font-semibold text-slate-950">
                    {pendingSectionSave.editingSectionId ? "Save section changes?" : "Create section?"}
                  </h2>
                </div>
              </div>

              <p className="mb-6 text-sm leading-6 text-slate-600">
                Confirm to save {asText(pendingSectionSave.section.title, "this section")} and refresh the module section list.
              </p>

              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setPendingSectionSave(null)}
                  disabled={isSavingSection}
                  className="inline-flex h-9 items-center justify-center rounded-md border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  Review
                </button>
                <button
                  type="button"
                  onClick={confirmSectionSave}
                  disabled={isSavingSection}
                  className="inline-flex h-9 items-center justify-center gap-2 rounded-md bg-teal-700 px-4 text-sm font-semibold text-white transition hover:bg-teal-800 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {isSavingSection ? (
                    <>
                      <Loader2 className="h-4 w-4 animate-spin" />
                      Saving...
                    </>
                  ) : (
                    "Confirm Save"
                  )}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

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
