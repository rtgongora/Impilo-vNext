"use client";

import { useState } from "react";
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
} from "lucide-react";
import { asArray, asRecord, asText, type ModalKey, type Row } from "@/components/learning/learningUtils";
import { Panel, CreationCard, CreationTemplate, StatusPill, countOf } from "@/components/learning/SharedComponents";
import { SectionFormComponent } from "./SectionFormComponent";
import { DeleteConfirmationDialog } from "./DeleteConfirmationDialog";
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

export function Studio({ data, setModal }: { data: Record<string, unknown>; setModal: (m: ModalKey, defaults?: Row) => void }) {
  const [activeTab, setActiveTab] = useState<ContentType>("courses");
  const [courseView, setCourseView] = useState<"sections" | null>(null);
  const [selectedCourseForSections, setSelectedCourseForSections] = useState<CourseStructure | null>(null);
  const [modules, setModules] = useState<Array<any>>([]);
  const [showAddSection, setShowAddSection] = useState(false);
  const [deletingCourse, setDeletingCourse] = useState<Row | null>(null);
  const [loadingStructure, setLoadingStructure] = useState(false);
  const [structureError, setStructureError] = useState<string | null>(null);

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
      const structure = asRecord(response.data);

      setSelectedCourseForSections(structure as CourseStructure);
      setModules(asArray(structure.modules));
      setCourseView("sections");
      setShowAddSection(false);
    } catch (err) {
      const errorMsg = err instanceof Error ? err.message : "Failed to load course structure";
      setStructureError(errorMsg);
      console.error("Failed to fetch course structure:", err);
    } finally {
      setLoadingStructure(false);
    }
  };

  // Show module management view (formerly section view)
  if (courseView === "sections" && selectedCourseForSections) {
    return (
      <>
        <ModuleManagementView
          courseView={courseView}
          setCourseView={setCourseView}
          selectedCourseForSections={selectedCourseForSections}
          setSelectedCourseForSections={setSelectedCourseForSections}
          modules={modules}
          setModules={setModules}
          structureError={structureError}
          setStructureError={setStructureError}
        />
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
          <Panel title={`Courses (${courseCount})`}>
            {visibleCourses.length > 0 ? (
              <div className="space-y-2">
                {visibleCourses.map((row, index) => (
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
        <Panel title={`Library Resources (${library.length})`}>
          {library.length > 0 ? (
            <div className="space-y-2">
              {library.map((row, index) => (
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
        <Panel title={`Media Assets (${media.length})`}>
          {media.length > 0 ? (
            <div className="space-y-2">
              {media.map((row, index) => (
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
        <Panel title={`Interactive Activities (${activities.length})`}>
          {activities.length > 0 ? (
            <div className="space-y-2">
              {activities.map((row, index) => (
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
        <Panel title={`Cohorts (${cohorts.length})`}>
          {cohorts.length > 0 ? (
            <div className="space-y-2">
              {cohorts.map((row, index) => (
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
        <Panel title={`Sessions (${sessions.length})`}>
          {sessions.length > 0 ? (
            <div className="space-y-2">
              {sessions.map((row, index) => (
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
      const createdModule = asRecord(moduleData.module || moduleData);

      const newModule = {
        id: asText(createdModule.id),
        title: asText(createdModule.title),
        description: asText(createdModule.description),
        sequence: createdModule.sequence || modules.length + 1,
        status: asText(createdModule.status),
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
      const errorMsg = err instanceof Error ? err.message : "Failed to create module";
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
      const createdLesson = asRecord(lessonData.lesson || lessonData);

      // Update local state
      const newLesson = {
        id: asText(createdLesson.id),
        title: asText(createdLesson.title),
        contentType: asText(createdLesson.contentType),
        sequence: createdLesson.sequence || allSections.length + 1,
        status: asText(createdLesson.status),
      };

      setAllSections([...allSections, newLesson]);
      setShowAddSection(false);
      setStructureError(null);
    } catch (err) {
      const errorMsg = err instanceof Error ? err.message : "Failed to save section to backend";
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

