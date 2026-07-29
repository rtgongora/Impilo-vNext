"use client";

import { useState, FormEvent } from "react";
import * as React from "react";
import {
  Activity,
  ArrowLeft,
  BookOpenCheck,
  CalendarClock,
  FileText,
  GripVertical,
  Library,
  Plus,
  Radio,
  Trash2,
  Users,
  Loader2,
  X,
} from "lucide-react";
import { asArray, asRecord, asText, type ModalKey, type Row } from "@/components/learning/learningUtils";
import { Panel, CreationCard, CreationTemplate, StatusPill, countOf } from "@/components/learning/SharedComponents";
import { SectionFormComponent } from "./SectionFormComponent";
import { DeleteConfirmationDialog } from "./DeleteConfirmationDialog";
import { ModuleManagementPanel } from "./studio/ModuleManagementPanel";
import { ModuleDetailPage } from "./ModuleDetailPage";
import { SearchFilterPagination } from "./SearchFilterPagination";
import { LearningModal } from "./LearningWorkspace";
import { apiClient } from "@/lib/api-client";

const FUNDO = "/internal/v1/learning/fundo";

type ContentType = "courses" | "resources" | "media" | "activities" | "cohorts" | "sessions";

export interface CourseStructure extends Row {
  modules?: Array<{
    id: string;
    title: string;
    description?: string;
    sequence: number;
    lessons?: Array<{
      id: string;
      title: string;
      contentType: string;
      sequence: number;
      status: string;
    }>;
  }>;
}

function unwrapNamedData(value: unknown, key: string) {
  const envelope = asRecord(value);
  return asRecord(envelope[key] ?? envelope);
}

function apiErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error) return error.message;
  const record = asRecord(error);
  const apiError = asRecord(record.error);
  const message = asText(apiError.message ?? record.message, fallback);
  try {
    const parsed = JSON.parse(message);
    const parsedError = asRecord(asRecord(parsed).error);
    return asText(parsedError.message ?? parsedError.code, message);
  } catch {
    return message;
  }
}

export function Studio({ data, setModal }: { data: Record<string, unknown>; setModal: (m: ModalKey, defaults?: Row) => void }) {
  const [activeTab, setActiveTab] = useState<ContentType>("courses");
  const [courseView, setCourseView] = useState<"sections" | "moduleDetail" | null>(null);
  const [selectedCourseForSections, setSelectedCourseForSections] = useState<CourseStructure | null>(null);
  const [modules, setModules] = useState<Array<any>>([]);
  const [selectedModuleForDetail, setSelectedModuleForDetail] = useState<string | null>(null);
  const [showAddSection, setShowAddSection] = useState(false);
  const [selectedModuleForSection, setSelectedModuleForSection] = useState<string | null>(null);
  const [editingSection, setEditingSection] = useState<{ moduleId: string; sectionId: string } | null>(null);
  const [deletingCourse, setDeletingCourse] = useState<Row | null>(null);
  const [deletingModule, setDeletingModule] = useState<string | null>(null);
  const [deletingSection, setDeletingSection] = useState<{ moduleId: string; sectionId: string } | null>(null);
  const [loadingStructure, setLoadingStructure] = useState(false);
  const [structureError, setStructureError] = useState<string | null>(null);
  const [showModuleModal, setShowModuleModal] = useState(false);
  const [editingModule, setEditingModule] = useState<{ id: string; title: string; description?: string } | null>(null);
  const [moduleModalTitle, setModuleModalTitle] = useState("");
  const [moduleModalDescription, setModuleModalDescription] = useState("");
  const [isSubmittingModule, setIsSubmittingModule] = useState(false);

  // Search, filter, pagination state
  const [searchQuery, setSearchQuery] = useState<Record<ContentType, string>>({
    courses: "",
    resources: "",
    media: "",
    activities: "",
    cohorts: "",
    sessions: "",
  });
  const [filterValue, setFilterValue] = useState<Record<ContentType, string>>({
    courses: "",
    resources: "",
    media: "",
    activities: "",
    cohorts: "",
    sessions: "",
  });
  const [currentPage, setCurrentPage] = useState<Record<ContentType, number>>({
    courses: 1,
    resources: 1,
    media: 1,
    activities: 1,
    cohorts: 1,
    sessions: 1,
  });
  const pageSize = 10;

  const studio = asRecord(data.studio);
  const library = asArray(asRecord(data.library).items);
  const media = asArray(asRecord(data.media).items);
  const activities = asArray(asRecord(data.activities).items);
  const cohorts = asArray(asRecord(data.cohorts).items);
  const sessions = asArray(asRecord(data.sessions).items);
  const courses = asArray(asRecord(data.catalogAll).items);
  const visibleCourses = courses.length > 0 ? courses : asArray(asRecord(data.catalog).items);

  const totalAssets = library.length + media.length + activities.length;
  const draftCount = countOf(studio.draftCourses, 0);
  const publishedCount = countOf(studio.publishedCourses, 0);
  const courseCount = Math.max(visibleCourses.length, draftCount + publishedCount);

  // Helper function to filter and paginate items
  const filterAndPaginateItems = (items: Array<Row>, tabType: ContentType): Array<Row> => {
    const query = searchQuery[tabType].toLowerCase();
    const filtered = items.filter((item) => {
      const title = asText(item.title ?? item.name, "").toLowerCase();
      const description = asText(item.description ?? item.code ?? "", "").toLowerCase();
      return title.includes(query) || description.includes(query);
    });

    const start = (currentPage[tabType] - 1) * pageSize;
    const end = start + pageSize;
    return filtered.slice(start, end);
  };

  const getTotalFilteredCount = (items: Array<Row>, tabType: ContentType): number => {
    const query = searchQuery[tabType].toLowerCase();
    return items.filter((item) => {
      const title = asText(item.title ?? item.name, "").toLowerCase();
      const description = asText(item.description ?? item.code ?? "", "").toLowerCase();
      return title.includes(query) || description.includes(query);
    }).length;
  };

  // Handler to fetch course structure on demand
  const handleManageSections = async (course: Row) => {
    setLoadingStructure(true);
    setStructureError(null);
    try {
      const courseId = asText(course.id);
      if (!courseId) {
        setStructureError("Invalid course ID");
        return;
      }

      const response = await apiClient.get<{ data?: any }>(`${FUNDO}/courses/${courseId}/structure`);
      const structure = unwrapNamedData(response.data, "structure");

      setSelectedCourseForSections(structure as CourseStructure);
      setModules(asArray(structure.modules));
      setCourseView("sections");
      setShowAddSection(false);
    } catch (err) {
      const errorMsg = apiErrorMessage(err, "Failed to load course structure");
      setStructureError(errorMsg);
      console.error("Failed to fetch course structure:", err);
    } finally {
      setLoadingStructure(false);
    }
  };

  // Module handlers
  const handleAddModule = () => {
    setEditingModule(null);
    setModuleModalTitle("");
    setModuleModalDescription("");
    setShowModuleModal(true);
  };

  const handleEditModule = (moduleId: string) => {
    const module = modules.find((m) => m.id === moduleId);
    if (module) {
      setEditingModule({
        id: module.id,
        title: module.title,
        description: module.description,
      });
      setModuleModalTitle(module.title);
      setModuleModalDescription(module.description || "");
      setShowModuleModal(true);
    }
  };

  const handleDeleteModule = (moduleId: string) => {
    setDeletingModule(moduleId);
  };

  const handleSaveModule = async () => {
    if (!moduleModalTitle.trim()) {
      setStructureError("Module title is required");
      return;
    }

    setIsSubmittingModule(true);
    try {
      const courseId = asText(selectedCourseForSections?.id);
      if (!courseId) {
        setStructureError("Invalid course ID");
        return;
      }

      if (editingModule) {
        // Update existing module
        await apiClient.put(`${FUNDO}/modules/${editingModule.id}`, {
          title: moduleModalTitle,
          description: moduleModalDescription || null,
        });

        setModules(
          modules.map((m) =>
            m.id === editingModule.id
              ? { ...m, title: moduleModalTitle, description: moduleModalDescription }
              : m
          )
        );
        setStructureError(null);
      } else {
        // Create new module
        const moduleResponse = await apiClient.post(`${FUNDO}/courses/${courseId}/modules`, {
          title: moduleModalTitle,
          description: moduleModalDescription || null,
          sequence: modules.length + 1,
          status: "PUBLISHED",
        });

        const moduleData = asRecord(asRecord(moduleResponse).data);
        const createdModule = unwrapNamedData(moduleData, "module");
        const createdModuleId = asText(createdModule.id, "");
        if (!createdModuleId) {
          throw new Error("Backend did not return a module ID");
        }

        const newModule = {
          id: createdModuleId,
          title: asText(createdModule.title, moduleModalTitle),
          description: asText(createdModule.description, moduleModalDescription),
          sequence: createdModule.sequence || modules.length + 1,
          status: asText(createdModule.status, "PUBLISHED"),
          lessons: [],
        };

        setModules([...modules, newModule]);
        setStructureError(null);
      }

      setShowModuleModal(false);
      setEditingModule(null);
      setModuleModalTitle("");
      setModuleModalDescription("");
    } catch (err) {
      const errorMsg = apiErrorMessage(err, "Failed to save module");
      setStructureError(`Failed to save module: ${errorMsg}`);
      console.error("Module save error:", err);
    } finally {
      setIsSubmittingModule(false);
    }
  };

  const confirmDeleteModule = async () => {
    if (!deletingModule) return;

    try {
      await apiClient.delete(`${FUNDO}/modules/${deletingModule}`);
      setModules(modules.filter((m) => m.id !== deletingModule));
      setStructureError(null);
    } catch (err) {
      const errorMsg = apiErrorMessage(err, "Failed to delete module");
      setStructureError(`Failed to delete module: ${errorMsg}`);
      console.error("Module delete error:", err);
    } finally {
      setDeletingModule(null);
    }
  };

  // Section handlers
  const handleAddSection = (moduleId: string) => {
    setSelectedModuleForSection(moduleId);
    setEditingSection(null);
    setShowAddSection(true);
  };

  const handleEditSection = (moduleId: string, sectionId: string) => {
    const module = modules.find((m) => m.id === moduleId);
    if (module) {
      const section = asArray(module.lessons).find((s) => s.id === sectionId);
      if (section) {
        setSelectedModuleForSection(moduleId);
        setEditingSection({ moduleId, sectionId });
        setShowAddSection(true);
      }
    }
  };

  const handleDeleteSection = (moduleId: string, sectionId: string) => {
    setDeletingSection({ moduleId, sectionId });
  };

  const confirmDeleteSection = async () => {
    if (!deletingSection) return;

    try {
      await apiClient.delete(`${FUNDO}/lessons/${deletingSection.sectionId}`);

      // Update local state
      setModules(
        modules.map((module) =>
          module.id === deletingSection.moduleId
            ? {
                ...module,
                lessons: asArray(module.lessons).filter((s) => s.id !== deletingSection.sectionId),
              }
            : module
        )
      );
      setStructureError(null);
    } catch (err) {
      const errorMsg = apiErrorMessage(err, "Failed to delete section");
      setStructureError(`Failed to delete section: ${errorMsg}`);
      console.error("Section delete error:", err);
    } finally {
      setDeletingSection(null);
    }
  };

  const handleSectionSubmit = async (newSection: Row) => {
    if (!selectedModuleForSection) {
      setStructureError("No module selected. Please try again.");
      return;
    }

    try {
      const courseId = asText(selectedCourseForSections?.id);
      if (!courseId) {
        setStructureError("Invalid course ID");
        return;
      }

      if (editingSection) {
        // Update existing section
        await apiClient.put(`${FUNDO}/lessons/${editingSection.sectionId}`, {
          title: newSection.title,
          contentType: newSection.contentType || newSection.type,
          contentBody: newSection.contentBody,
          contentRef: newSection.contentRef,
          contentFormat: newSection.contentFormat,
          contentBlocksJson: newSection.contentBlocksJson,
          status: newSection.status || "DRAFT",
        });

        setModules(
          modules.map((module) =>
            module.id === editingSection.moduleId
              ? {
                  ...module,
                  lessons: asArray(module.lessons).map((s) =>
                    s.id === editingSection.sectionId
                      ? {
                          ...s,
                          title: newSection.title,
                          contentType: newSection.contentType || newSection.type,
                          status: newSection.status || "DRAFT",
                        }
                      : s
                  ),
                }
              : module
          )
        );
      } else {
        // Create new section
        const lessonResponse = await apiClient.post(`${FUNDO}/modules/${selectedModuleForSection}/lessons`, {
          title: newSection.title,
          contentType: newSection.contentType || newSection.type,
          contentBody: newSection.contentBody,
          contentRef: newSection.contentRef,
          contentFormat: newSection.contentFormat,
          contentBlocksJson: newSection.contentBlocksJson,
          sequence: 999, // Backend will handle sequence
          required: newSection.required ?? true,
          status: newSection.status || "DRAFT",
        });

        const lessonData = asRecord(asRecord(lessonResponse).data);
        const createdLesson = unwrapNamedData(lessonData, "lesson");
        const createdLessonId = asText(createdLesson.id, "");
        if (!createdLessonId) {
          throw new Error("Backend did not return a lesson ID");
        }

        const newLesson = {
          id: createdLessonId,
          title: asText(createdLesson.title, asText(newSection.title, "Section")),
          contentType: asText(createdLesson.contentType, asText(newSection.contentType ?? newSection.type, "TEXT")),
          sequence: createdLesson.sequence || 999,
          status: asText(createdLesson.status, "DRAFT"),
        };

        setModules(
          modules.map((module) =>
            module.id === selectedModuleForSection
              ? {
                  ...module,
                  lessons: [...asArray(module.lessons), newLesson],
                }
              : module
          )
        );
      }

      setShowAddSection(false);
      setSelectedModuleForSection(null);
      setEditingSection(null);
      setStructureError(null);
    } catch (err) {
      const errorMsg = apiErrorMessage(err, "Failed to save section");
      setStructureError(`Failed to save section: ${errorMsg}`);
      console.error("Section save error:", err);
    }
  };

  // Show module detail page
  if (courseView === "moduleDetail" && selectedCourseForSections && selectedModuleForDetail) {
    const module = modules.find((m) => m.id === selectedModuleForDetail);
    if (module) {
      return (
        <ModuleDetailPage
          courseId={asText(selectedCourseForSections.id)}
          courseName={asText(selectedCourseForSections.title ?? selectedCourseForSections.name, "Course")}
          module={module}
          onBack={() => {
            setCourseView("sections");
            setSelectedModuleForDetail(null);
          }}
          onModuleUpdate={(updatedModule) => {
            setModules(modules.map((m) => (m.id === updatedModule.id ? updatedModule : m)));
          }}
        />
      );
    }
  }

  // Show module management view (formerly section view)
  if (courseView === "sections" && selectedCourseForSections) {
    return (
      <>
        <ModuleManagementPanel
          courseTitle={asText(selectedCourseForSections.title ?? selectedCourseForSections.name, "Course")}
          modules={modules}
          onBack={() => {
            setCourseView(null);
            setSelectedCourseForSections(null);
            setStructureError(null);
          }}
          onAddModule={handleAddModule}
          onViewModule={(moduleId) => {
            setSelectedModuleForDetail(moduleId);
            setCourseView("moduleDetail");
          }}
          onEditModule={handleEditModule}
          onDeleteModule={handleDeleteModule}
          error={structureError || undefined}
        />

        {/* Module Modal */}
        {showModuleModal && (
          <LearningModal
            title={editingModule ? "Edit Module" : "Add Module"}
            busy={isSubmittingModule}
            onClose={() => {
              setShowModuleModal(false);
              setEditingModule(null);
              setModuleModalTitle("");
              setModuleModalDescription("");
            }}
            onSubmit={(e: FormEvent<HTMLFormElement>) => {
              e.preventDefault();
              handleSaveModule();
            }}
          >
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1">Module Title *</label>
              <input
                type="text"
                value={moduleModalTitle}
                onChange={(e) => setModuleModalTitle(e.target.value)}
                placeholder="e.g., Introduction to Anatomy"
                className="w-full rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                required
              />
            </div>
            <div className="sm:col-span-2">
              <label className="block text-xs font-medium text-slate-600 mb-1">Description (optional)</label>
              <textarea
                value={moduleModalDescription}
                onChange={(e) => setModuleModalDescription(e.target.value)}
                placeholder="Brief description of the module"
                rows={4}
                className="w-full rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
              />
            </div>
          </LearningModal>
        )}

        {/* Delete Module Confirmation */}
        {deletingModule && (
          <DeleteConfirmationDialog
            title="Delete Module"
            message="Are you sure you want to delete this module and all its sections? This action cannot be undone."
            onConfirm={confirmDeleteModule}
            onCancel={() => setDeletingModule(null)}
          />
        )}

        {/* Delete Section Confirmation */}
        {deletingSection && (
          <DeleteConfirmationDialog
            title="Delete Section"
            message="Are you sure you want to delete this section? This action cannot be undone."
            onConfirm={confirmDeleteSection}
            onCancel={() => setDeletingSection(null)}
          />
        )}

        {/* Section Form Modal */}
        {showAddSection && selectedModuleForSection && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 p-4 overflow-y-auto">
            <div className="w-full max-w-2xl bg-white rounded-lg shadow-xl my-8 flex flex-col">
              {/* Modal Header */}
              <div className="sticky top-0 flex items-center justify-between border-b border-slate-200 px-4 py-3 bg-white rounded-t-lg">
                <h2 className="text-base font-semibold text-slate-950">{editingSection ? "Edit Section" : "Add Section"}</h2>
                <button
                  type="button"
                  onClick={() => {
                    setShowAddSection(false);
                    setSelectedModuleForSection(null);
                    setEditingSection(null);
                  }}
                  className="rounded-md p-1 text-slate-500 hover:bg-slate-100"
                  aria-label="Close"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>
              {/* Modal Content - Remove background styling conflict */}
              <div className="flex-1 max-h-[70vh] overflow-y-auto">
                <div className="[&>div]:mb-0 [&>div]:p-0 [&>div]:border-0 [&>div]:bg-white">
                  <SectionFormComponent
                    onCancel={() => {
                      setShowAddSection(false);
                      setSelectedModuleForSection(null);
                      setEditingSection(null);
                    }}
                    onSubmit={handleSectionSubmit}
                    courseId={asText(selectedCourseForSections?.id as string)}
                    sequenceNo={0}
                    initialData={
                      editingSection
                        ? (() => {
                            const module = modules.find((m) => m.id === editingSection.moduleId);
                            if (module) {
                              const section = asArray(module.lessons).find((s) => s.id === editingSection.sectionId);
                              return section;
                            }
                            return undefined;
                          })()
                        : undefined
                    }
                  />
                </div>
              </div>
            </div>
          </div>
        )}

        {deletingCourse && (
          <DeleteConfirmationDialog
            title="Delete Course"
            message={`Are you sure you want to delete "${asText(deletingCourse.title ?? deletingCourse.name, "this course")}"? This action cannot be undone.`}
            onConfirm={() => {
              // TODO: Call API to delete course
              setDeletingCourse(null);
            }}
            onCancel={() => setDeletingCourse(null)}
          />
        )}
      </>
    );
  }

  return (
    <div className="space-y-3 pb-20 md:pb-3">
      {/* Content Type Tabs */}
      <div className="flex gap-1 border-b border-slate-200 overflow-x-auto">
        {[
          { key: "courses" as ContentType, label: "Courses", icon: "📚" },
          { key: "resources" as ContentType, label: "Resources", icon: "📄" },
          { key: "media" as ContentType, label: "Media", icon: "🎬" },
          { key: "activities" as ContentType, label: "Activities", icon: "❓" },
          { key: "cohorts" as ContentType, label: "Cohorts", icon: "👥" },
          { key: "sessions" as ContentType, label: "Sessions", icon: "📅" },
        ].map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={[
              "px-4 py-2 text-sm font-medium whitespace-nowrap transition border-b-2",
              activeTab === tab.key
                ? "border-teal-600 text-teal-700 bg-teal-50"
                : "border-transparent text-slate-600 hover:text-slate-900 hover:bg-slate-50",
            ].join(" ")}
          >
            <span className="mr-1">{tab.icon}</span>
            {tab.label}
          </button>
        ))}
      </div>

      {/* Create New Button - Context Sensitive */}
      <div className="flex justify-end">
        <button
          onClick={() => {
            if (activeTab === "courses") setModal("course");
            else if (activeTab === "resources") setModal("library");
            else if (activeTab === "media") setModal("media");
            else if (activeTab === "activities") setModal("activity");
            else if (activeTab === "cohorts") setModal("cohort");
            else if (activeTab === "sessions") setModal("session");
          }}
          className="inline-flex h-8 items-center gap-1.5 rounded-md bg-teal-700 text-white px-3 text-xs font-semibold hover:bg-teal-800 transition"
        >
          <Plus className="h-3.5 w-3.5" />
          <span>New {activeTab.slice(0, -1)}</span>
        </button>
      </div>

      {/* Tab Content - Courses */}
      {activeTab === "courses" && (
        <>
          <Panel title={`Courses (${getTotalFilteredCount(visibleCourses, "courses")})`}>
            <SearchFilterPagination
              totalItems={getTotalFilteredCount(visibleCourses, "courses")}
              pageSize={pageSize}
              currentPage={currentPage.courses}
              onPageChange={(page) => setCurrentPage({ ...currentPage, courses: page })}
              onSearch={(query) => {
                setSearchQuery({ ...searchQuery, courses: query });
                setCurrentPage({ ...currentPage, courses: 1 });
              }}
              searchPlaceholder="Search courses..."
            />
            {visibleCourses.length > 0 ? (
              <div className="space-y-2 mt-4">
                {filterAndPaginateItems(visibleCourses, "courses").map((row, index) => (
                  <ContentListItem key={String(row.id ?? row.code ?? index)} row={row} type="course" onEdit={() => setModal("course", row)} onManage={() => handleManageSections(row)} onDelete={() => setDeletingCourse(row)} />
                ))}
              </div>
            ) : (
              <div className="text-center py-8 px-4 rounded-lg border border-dashed border-slate-200 bg-slate-50">
                <p className="text-sm font-semibold text-slate-900">No courses yet</p>
                <p className="text-xs text-slate-500 mt-1">Click "New course" to create your first course</p>
              </div>
            )}
          </Panel>
          {deletingCourse && (
            <DeleteConfirmationDialog
              title="Delete Course"
              message={`Are you sure you want to delete "${asText(deletingCourse.title ?? deletingCourse.name, "this course")}"? This action cannot be undone.`}
              onConfirm={() => {
                // TODO: Call API to delete course
                setDeletingCourse(null);
              }}
              onCancel={() => setDeletingCourse(null)}
            />
          )}
        </>
      )}

      {/* Tab Content - Resources */}
      {activeTab === "resources" && (
        <Panel title={`Library Resources (${getTotalFilteredCount(library, "resources")})`}>
          <SearchFilterPagination
            totalItems={getTotalFilteredCount(library, "resources")}
            pageSize={pageSize}
            currentPage={currentPage.resources}
            onPageChange={(page) => setCurrentPage({ ...currentPage, resources: page })}
            onSearch={(query) => {
              setSearchQuery({ ...searchQuery, resources: query });
              setCurrentPage({ ...currentPage, resources: 1 });
            }}
            searchPlaceholder="Search resources..."
          />
          {library.length > 0 ? (
            <div className="space-y-2 mt-4">
              {filterAndPaginateItems(library, "resources").map((row, index) => (
                <ContentListItem key={String(row.id ?? index)} row={row} type="resource" onEdit={() => setModal("library", row)} />
              ))}
            </div>
          ) : (
            <div className="text-center py-8 px-4 rounded-lg border border-dashed border-slate-200 bg-slate-50">
              <p className="text-sm font-semibold text-slate-900">No resources yet</p>
              <p className="text-xs text-slate-500 mt-1">Click "New resource" to add learning materials</p>
            </div>
          )}
        </Panel>
      )}

      {/* Tab Content - Media */}
      {activeTab === "media" && (
        <Panel title={`Media Assets (${getTotalFilteredCount(media, "media")})`}>
          <SearchFilterPagination
            totalItems={getTotalFilteredCount(media, "media")}
            pageSize={pageSize}
            currentPage={currentPage.media}
            onPageChange={(page) => setCurrentPage({ ...currentPage, media: page })}
            onSearch={(query) => {
              setSearchQuery({ ...searchQuery, media: query });
              setCurrentPage({ ...currentPage, media: 1 });
            }}
            searchPlaceholder="Search media..."
          />
          {media.length > 0 ? (
            <div className="space-y-2 mt-4">
              {filterAndPaginateItems(media, "media").map((row, index) => (
                <ContentListItem key={String(row.id ?? index)} row={row} type="media" onEdit={() => setModal("media", row)} />
              ))}
            </div>
          ) : (
            <div className="text-center py-8 px-4 rounded-lg border border-dashed border-slate-200 bg-slate-50">
              <p className="text-sm font-semibold text-slate-900">No media yet</p>
              <p className="text-xs text-slate-500 mt-1">Click "New media" to upload videos, audio, and images</p>
            </div>
          )}
        </Panel>
      )}

      {/* Tab Content - Activities */}
      {activeTab === "activities" && (
        <Panel title={`Interactive Activities (${getTotalFilteredCount(activities, "activities")})`}>
          <SearchFilterPagination
            totalItems={getTotalFilteredCount(activities, "activities")}
            pageSize={pageSize}
            currentPage={currentPage.activities}
            onPageChange={(page) => setCurrentPage({ ...currentPage, activities: page })}
            onSearch={(query) => {
              setSearchQuery({ ...searchQuery, activities: query });
              setCurrentPage({ ...currentPage, activities: 1 });
            }}
            searchPlaceholder="Search activities..."
          />
          {activities.length > 0 ? (
            <div className="space-y-2 mt-4">
              {filterAndPaginateItems(activities, "activities").map((row, index) => (
                <ContentListItem key={String(row.id ?? index)} row={row} type="activity" onEdit={() => setModal("activity", row)} />
              ))}
            </div>
          ) : (
            <div className="text-center py-8 px-4 rounded-lg border border-dashed border-slate-200 bg-slate-50">
              <p className="text-sm font-semibold text-slate-900">No activities yet</p>
              <p className="text-xs text-slate-500 mt-1">Click "New activity" to create quizzes and assessments</p>
            </div>
          )}
        </Panel>
      )}

      {/* Tab Content - Cohorts */}
      {activeTab === "cohorts" && (
        <Panel title={`Cohorts (${getTotalFilteredCount(cohorts, "cohorts")})`}>
          <SearchFilterPagination
            totalItems={getTotalFilteredCount(cohorts, "cohorts")}
            pageSize={pageSize}
            currentPage={currentPage.cohorts}
            onPageChange={(page) => setCurrentPage({ ...currentPage, cohorts: page })}
            onSearch={(query) => {
              setSearchQuery({ ...searchQuery, cohorts: query });
              setCurrentPage({ ...currentPage, cohorts: 1 });
            }}
            searchPlaceholder="Search cohorts..."
          />
          {cohorts.length > 0 ? (
            <div className="space-y-2 mt-4">
              {filterAndPaginateItems(cohorts, "cohorts").map((row, index) => (
                <ContentListItem key={String(row.id ?? index)} row={row} type="cohort" onEdit={() => setModal("cohort", row)} />
              ))}
            </div>
          ) : (
            <div className="text-center py-8 px-4 rounded-lg border border-dashed border-slate-200 bg-slate-50">
              <p className="text-sm font-semibold text-slate-900">No cohorts yet</p>
              <p className="text-xs text-slate-500 mt-1">Click "New cohort" to group learners</p>
            </div>
          )}
        </Panel>
      )}

      {/* Tab Content - Sessions */}
      {activeTab === "sessions" && (
        <Panel title={`Sessions (${getTotalFilteredCount(sessions, "sessions")})`}>
          <SearchFilterPagination
            totalItems={getTotalFilteredCount(sessions, "sessions")}
            pageSize={pageSize}
            currentPage={currentPage.sessions}
            onPageChange={(page) => setCurrentPage({ ...currentPage, sessions: page })}
            onSearch={(query) => {
              setSearchQuery({ ...searchQuery, sessions: query });
              setCurrentPage({ ...currentPage, sessions: 1 });
            }}
            searchPlaceholder="Search sessions..."
          />
          {sessions.length > 0 ? (
            <div className="space-y-2 mt-4">
              {filterAndPaginateItems(sessions, "sessions").map((row, index) => (
                <ContentListItem key={String(row.id ?? index)} row={row} type="session" onEdit={() => setModal("session", row)} />
              ))}
            </div>
          ) : (
            <div className="text-center py-8 px-4 rounded-lg border border-dashed border-slate-200 bg-slate-50">
              <p className="text-sm font-semibold text-slate-900">No sessions yet</p>
              <p className="text-xs text-slate-500 mt-1">Click "New session" to schedule live training</p>
            </div>
          )}
        </Panel>
      )}
    </div>
  );
}

function ModuleManagementView({
  courseView,
  setCourseView,
  selectedCourseForSections,
  setSelectedCourseForSections,
  modules,
  setModules,
  structureError,
  setStructureError,
}: {
  courseView: string | null;
  setCourseView: (view: "sections" | null) => void;
  selectedCourseForSections: CourseStructure | null;
  setSelectedCourseForSections: (row: CourseStructure | null) => void;
  modules: Array<any>;
  setModules: (modules: Array<any>) => void;
  structureError: string | null;
  setStructureError: (error: string | null) => void;
}) {
  const [selectedModule, setSelectedModule] = useState<any | null>(null);
  const [showAddModule, setShowAddModule] = useState(false);
  const [newModuleTitle, setNewModuleTitle] = useState("");
  const [newModuleDescription, setNewModuleDescription] = useState("");
  const [moduleCreating, setModuleCreating] = useState(false);
  const [showAddSection, setShowAddSection] = useState(false);
  const [allSections, setAllSections] = useState<Array<any>>([]);

  // Initialize with first module if available
  React.useEffect(() => {
    if (modules.length > 0 && !selectedModule) {
      const firstModule = modules[0];
      setSelectedModule(firstModule);
      setAllSections(asArray(firstModule.lessons || []));
    }
  }, [modules]);

  const handleAddModule = async () => {
    if (!newModuleTitle.trim()) {
      setStructureError("Module title is required");
      return;
    }

    setModuleCreating(true);
    try {
      const courseId = asText(selectedCourseForSections?.id);
      if (!courseId) {
        setStructureError("Invalid course ID");
        return;
      }

      const moduleResponse = await apiClient.post(`${FUNDO}/courses/${courseId}/modules`, {
        title: newModuleTitle,
        description: newModuleDescription || null,
        sequence: modules.length + 1,
        status: "PUBLISHED",
      });

      const moduleData = asRecord(asRecord(moduleResponse).data);
      const createdModule = unwrapNamedData(moduleData, "module");
      const createdModuleId = asText(createdModule.id, "");
      if (!createdModuleId) {
        throw new Error("Backend did not return a module ID");
      }

      const newModule = {
        id: createdModuleId,
        title: asText(createdModule.title, newModuleTitle),
        description: asText(createdModule.description, newModuleDescription),
        sequence: createdModule.sequence || modules.length + 1,
        status: asText(createdModule.status, "PUBLISHED"),
        lessons: [],
      };

      setModules([...modules, newModule]);
      setSelectedModule(newModule);
      setAllSections([]);
      setNewModuleTitle("");
      setNewModuleDescription("");
      setShowAddModule(false);
      setStructureError(null);
    } catch (err) {
      const errorMsg = apiErrorMessage(err, "Failed to create module");
      setStructureError(`Failed to create module: ${errorMsg}`);
      console.error("Module creation error:", err);
    } finally {
      setModuleCreating(false);
    }
  };

  const handleSectionSubmit = async (newSection: Row) => {
    if (!selectedModule) {
      setStructureError("No module selected. Please try again.");
      return;
    }

    try {
      // Create lesson via backend API
      const lessonResponse = await apiClient.post(`${FUNDO}/modules/${selectedModule.id}/lessons`, {
        title: newSection.title,
        contentType: newSection.contentType || newSection.type,
        contentBody: newSection.contentBody,
        contentRef: newSection.contentRef,
        contentFormat: newSection.contentFormat,
        contentBlocksJson: newSection.contentBlocksJson,
        sequence: (asArray(selectedModule.lessons).length || 0) + 1,
        required: newSection.required ?? true,
        status: newSection.status || "DRAFT",
      });

      const lessonData = asRecord(asRecord(lessonResponse).data);
      const createdLesson = unwrapNamedData(lessonData, "lesson");
      const createdLessonId = asText(createdLesson.id, "");
      if (!createdLessonId) {
        throw new Error("Backend did not return a lesson ID");
      }

      // Update local state
      const newLesson = {
        id: createdLessonId,
        title: asText(createdLesson.title, asText(newSection.title, "Section")),
        contentType: asText(createdLesson.contentType, asText(newSection.contentType ?? newSection.type, "TEXT")),
        sequence: createdLesson.sequence || allSections.length + 1,
        status: asText(createdLesson.status, "DRAFT"),
      };

      setAllSections([...allSections, newLesson]);
      const updatedModule = {
        ...selectedModule,
        lessons: [...asArray(selectedModule.lessons), newLesson],
      };
      setSelectedModule(updatedModule);
      setModules(modules.map((module) => (module.id === selectedModule.id ? updatedModule : module)));
      setShowAddSection(false);
      setStructureError(null);
    } catch (err) {
      const errorMsg = apiErrorMessage(err, "Failed to save section to backend");
      setStructureError(`Failed to save section: ${errorMsg}`);
      console.error("Section save error:", err);
    }
  };

  return (
    <div className="space-y-3 pb-20 md:pb-3">
      <button
        onClick={() => {
          setCourseView(null);
          setSelectedCourseForSections(null);
          setSelectedModule(null);
          setAllSections([]);
          setStructureError(null);
        }}
        className="inline-flex items-center gap-1.5 text-teal-700 hover:text-teal-900 text-sm font-semibold mb-2"
      >
        <ArrowLeft className="h-4 w-4" />
        Back to Courses
      </button>

      {structureError && (
        <div className="rounded-md border border-red-200 bg-red-50 p-3">
          <p className="text-xs text-red-700 font-medium">{structureError}</p>
        </div>
      )}

      {/* Modules List */}
      <Panel
        title="Modules"
        action={
          <button
            onClick={() => setShowAddModule(!showAddModule)}
            disabled={moduleCreating}
            className="inline-flex h-8 items-center gap-1.5 rounded-md bg-teal-700 text-white px-3 text-xs font-semibold hover:bg-teal-800 transition disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <Plus className="h-3.5 w-3.5" />
            <span className="hidden sm:inline">Add Module</span>
          </button>
        }
      >
        {showAddModule && (
          <div className="mb-4 p-3 rounded-lg border border-teal-200 bg-teal-50">
            <div className="space-y-2">
              <input
                type="text"
                placeholder="Module title (e.g., Module 1: Basics)"
                value={newModuleTitle}
                onChange={(e) => setNewModuleTitle(e.target.value)}
                className="w-full rounded-md border border-slate-200 px-3 py-2 text-xs outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
              />
              <textarea
                placeholder="Module description (optional)"
                value={newModuleDescription}
                onChange={(e) => setNewModuleDescription(e.target.value)}
                rows={2}
                className="w-full rounded-md border border-slate-200 px-3 py-2 text-xs outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
              />
              <div className="flex gap-2">
                <button
                  onClick={handleAddModule}
                  disabled={moduleCreating || !newModuleTitle.trim()}
                  className="flex-1 h-8 rounded-md bg-teal-700 hover:bg-teal-800 text-white text-xs font-semibold transition disabled:opacity-60 disabled:cursor-not-allowed inline-flex items-center justify-center gap-2"
                >
                  {moduleCreating ? (
                    <>
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      <span>Creating...</span>
                    </>
                  ) : (
                    "Create Module"
                  )}
                </button>
                <button
                  onClick={() => {
                    setShowAddModule(false);
                    setNewModuleTitle("");
                    setNewModuleDescription("");
                  }}
                  disabled={moduleCreating}
                  className="flex-1 h-8 rounded-md border border-slate-200 text-slate-600 text-xs font-semibold hover:bg-slate-50 transition disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  Cancel
                </button>
              </div>
            </div>
          </div>
        )}

        {modules.length > 0 ? (
          <div className="space-y-2">
            {modules.map((module, idx) => (
              <button
                key={module.id}
                onClick={() => {
                  setSelectedModule(module);
                  setAllSections(asArray(module.lessons || []));
                }}
                className={`w-full text-left p-3 rounded-lg border-2 transition ${
                  selectedModule?.id === module.id
                    ? "border-teal-500 bg-teal-50"
                    : "border-slate-200 bg-white hover:border-teal-300"
                }`}
              >
                <div className="flex items-center gap-2">
                  <span className="inline-flex h-6 w-6 items-center justify-center rounded-full bg-teal-100 text-teal-700 text-xs font-bold">
                    {idx + 1}
                  </span>
                  <div className="text-left">
                    <p className="text-sm font-medium text-slate-950">{module.title}</p>
                    <p className="text-xs text-slate-500">{asArray(module.lessons || []).length} section(s)</p>
                  </div>
                </div>
              </button>
            ))}
          </div>
        ) : (
          <div className="text-center py-8 px-4 rounded-lg border border-dashed border-slate-200 bg-slate-50">
            <p className="text-sm font-semibold text-slate-900">No modules yet</p>
            <p className="text-xs text-slate-500 mt-1">Click "Add Module" to create your first module</p>
          </div>
        )}
      </Panel>

      {/* Sections for Selected Module */}
      {selectedModule && (
        <Panel
          title={`Sections in: ${selectedModule.title}`}
          action={
            <button
              onClick={() => setShowAddSection(!showAddSection)}
              className="inline-flex h-8 items-center gap-1.5 rounded-md bg-teal-700 text-white px-3 text-xs font-semibold hover:bg-teal-800 transition"
            >
              <Plus className="h-3.5 w-3.5" />
              <span className="hidden sm:inline">Add Section</span>
            </button>
          }
        >
          {showAddSection && (
            <SectionFormComponent
              onCancel={() => setShowAddSection(false)}
              onSubmit={handleSectionSubmit}
              courseId={asText(selectedCourseForSections?.id as string)}
              sequenceNo={allSections.length + 1}
            />
          )}

          {allSections.length > 0 ? (
            <div className="space-y-2">
              {allSections.map((section, index) => (
                <div key={section.id} className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white p-3 hover:shadow-md transition">
                  <GripVertical className="h-4 w-4 text-slate-400 cursor-grab shrink-0" />
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="inline-flex h-6 w-6 items-center justify-center rounded-full bg-teal-100 text-teal-700 text-xs font-bold">
                        {index + 1}
                      </span>
                      <p className="font-semibold text-slate-950 text-sm truncate">{section.title}</p>
                    </div>
                    <p className="text-xs text-slate-500 mt-1">{section.contentType}</p>
                  </div>
                  <div className="flex gap-1 shrink-0">
                    <button
                      onClick={() => {
                        // Future: Edit section
                      }}
                      className="inline-flex h-8 items-center gap-1 rounded-md border border-slate-200 bg-white px-2.5 text-xs font-medium text-slate-700 hover:bg-slate-50 transition"
                      title="Edit section"
                    >
                      Edit
                    </button>
                    <button
                      onClick={() => {
                        setAllSections(allSections.filter((_, i) => i !== index));
                      }}
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
            <div className="text-center py-8 px-4 rounded-lg border border-dashed border-slate-200 bg-slate-50">
              <p className="text-sm font-semibold text-slate-900">No sections yet</p>
              <p className="text-xs text-slate-500 mt-1">Click "Add Section" to start building this module</p>
            </div>
          )}
        </Panel>
      )}
    </div>
  );
}

// Reusable content list item component
function ContentListItem({
  row,
  type,
  onEdit,
  onManage,
  onDelete,
}: {
  row: Row;
  type: "course" | "resource" | "media" | "activity" | "cohort" | "session";
  onEdit: () => void;
  onManage?: () => void;
  onDelete?: () => void;
}) {
  const icons: Record<typeof type, React.ReactNode> = {
    course: "📚",
    resource: "📄",
    media: "🎬",
    activity: "❓",
    cohort: "👥",
    session: "📅",
  };
  const required = row.mandatory === true || row.mandatory === "true";
  const audience = asText(row.audienceType, "");

  return (
    <div className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white p-3 hover:shadow-md transition">
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-lg">{icons[type]}</span>
          <div className="min-w-0 flex-1">
            <p className="font-semibold text-slate-950 text-sm truncate">{asText(row.title ?? row.name, "Item")}</p>
            <p className="text-xs text-slate-500 mt-0.5 truncate">{asText(row.description ?? row.code ?? row.resourceType ?? row.status, "No detail")}</p>
            {type === "course" ? (
              <div className="mt-1 flex flex-wrap gap-1">
                {required ? <StatusPill>Required</StatusPill> : null}
                {audience ? <StatusPill>{audience.replace(/_/g, " ")}</StatusPill> : null}
              </div>
            ) : null}
          </div>
        </div>
      </div>
      <div className="flex gap-1 shrink-0">
        {onManage && type === "course" && (
          <button
            onClick={onManage}
            className="inline-flex h-8 items-center gap-1 rounded-md border border-slate-200 bg-white px-2.5 text-xs font-medium text-slate-700 hover:bg-slate-50 transition"
            title="Manage modules"
          >
            Modules
          </button>
        )}
        <button
          onClick={onEdit}
          className="inline-flex h-8 items-center gap-1 rounded-md border border-slate-200 bg-white px-2.5 text-xs font-medium text-slate-700 hover:bg-slate-50 transition"
          title="Edit"
        >
          Edit
        </button>
        {onDelete && type === "course" && (
          <button
            onClick={onDelete}
            className="inline-flex h-8 items-center gap-1 rounded-md border border-red-200 bg-red-50 px-2.5 text-xs font-medium text-red-700 hover:bg-red-100 transition"
            title="Delete"
          >
            <Trash2 className="h-3.5 w-3.5" />
          </button>
        )}
      </div>
    </div>
  );
}
