"use client";

import { useState } from "react";
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
} from "lucide-react";
import { asArray, asRecord, asText, type ModalKey, type Row } from "@/components/learning/learningUtils";
import { Panel, CreationCard, CreationTemplate, StatusPill, countOf } from "@/components/learning/SharedComponents";
import { SectionFormComponent } from "./SectionFormComponent";
import { DeleteConfirmationDialog } from "./DeleteConfirmationDialog";

type ContentType = "courses" | "resources" | "media" | "activities" | "cohorts" | "sessions";

export function Studio({ data, setModal }: { data: Record<string, unknown>; setModal: (m: ModalKey, defaults?: Row) => void }) {
  const [activeTab, setActiveTab] = useState<ContentType>("courses");
  const [courseView, setCourseView] = useState<"sections" | null>(null);
  const [selectedCourseForSections, setSelectedCourseForSections] = useState<Row | null>(null);
  const [sections, setSections] = useState<Row[]>([]);
  const [showAddSection, setShowAddSection] = useState(false);
  const [deletingCourse, setDeletingCourse] = useState<Row | null>(null);

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

  // Show section management view
  if (courseView === "sections" && selectedCourseForSections) {
    return (
      <>
        <SectionManagementView courseView={courseView} setCourseView={setCourseView} selectedCourseForSections={selectedCourseForSections} setSelectedCourseForSections={setSelectedCourseForSections} sections={sections} setSections={setSections} showAddSection={showAddSection} setShowAddSection={setShowAddSection} />
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
                  <ContentListItem key={String(row.id ?? row.code ?? index)} row={row} type="course" onEdit={() => setModal("course", row)} onManage={() => { setSelectedCourseForSections(row); setSections([]); setShowAddSection(false); setCourseView("sections"); }} onDelete={() => setDeletingCourse(row)} />
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

function SectionManagementView({
  courseView,
  setCourseView,
  selectedCourseForSections,
  setSelectedCourseForSections,
  sections,
  setSections,
  showAddSection,
  setShowAddSection,
}: {
  courseView: string | null;
  setCourseView: (view: "sections" | null) => void;
  selectedCourseForSections: Row | null;
  setSelectedCourseForSections: (row: Row | null) => void;
  sections: Row[];
  setSections: (sections: Row[]) => void;
  showAddSection: boolean;
  setShowAddSection: (show: boolean) => void;
}) {
  return (
    <div className="space-y-3 pb-20 md:pb-3">
      <button
        onClick={() => {
          setCourseView(null);
          setSelectedCourseForSections(null);
          setSections([]);
        }}
        className="inline-flex items-center gap-1.5 text-teal-700 hover:text-teal-900 text-sm font-semibold mb-2"
      >
        <ArrowLeft className="h-4 w-4" />
        Back to Courses
      </button>

      <Panel
        title={`Sections for: ${asText(selectedCourseForSections?.title ?? "Course", "Course")}`}
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
            onSubmit={(newSection) => {
              setSections([...sections, newSection]);
              setShowAddSection(false);
            }}
            courseId={asText(selectedCourseForSections?.id as string)}
            sequenceNo={sections.length + 1}
          />
        )}

        {sections.length > 0 ? (
          <div className="space-y-2">
            {sections.map((section, index) => (
              <div key={String(section.id ?? index)} className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white p-3 hover:shadow-md transition">
                <GripVertical className="h-4 w-4 text-slate-400 cursor-grab shrink-0" />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="inline-flex h-6 w-6 items-center justify-center rounded-full bg-teal-100 text-teal-700 text-xs font-bold">
                      {index + 1}
                    </span>
                    <p className="font-semibold text-slate-950 text-sm truncate">{asText(section.title ?? section.name, "Section")}</p>
                  </div>
                  <p className="text-xs text-slate-500 mt-1">{asText(section.contentType ?? section.type, "TEXT")}</p>
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
                      setSections(sections.filter((_, i) => i !== index));
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
            <p className="text-xs text-slate-500 mt-1">Click "Add Section" to start building your course</p>
          </div>
        )}
      </Panel>
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
            title="Manage sections"
          >
            Sections
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

