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

type ContentType = "courses" | "resources" | "media" | "activities" | "cohorts" | "sessions";

export function Studio({ data, setModal }: { data: Record<string, unknown>; setModal: (m: ModalKey, defaults?: Row) => void }) {
  const [activeTab, setActiveTab] = useState<ContentType>("courses");
  const [courseView, setCourseView] = useState<"list" | "create" | "sections" | null>(null);
  const [selectedCourseForSections, setSelectedCourseForSections] = useState<Row | null>(null);
  const [sections, setSections] = useState<Row[]>([]);
  const [showAddSection, setShowAddSection] = useState(false);

  const studio = asRecord(data.studio);
  const library = asArray(asRecord(data.library).items);
  const media = asArray(asRecord(data.media).items);
  const activities = asArray(asRecord(data.activities).items);
  const cohorts = asArray(asRecord(data.cohorts).items);
  const sessions = asArray(asRecord(data.sessions).items);
  const draftCourses = asArray(studio.draftCourses);

  const totalAssets = library.length + media.length + activities.length;
  const draftCount = countOf(studio.draftCourses, 0);

  // Show section management view
  if (courseView === "sections" && selectedCourseForSections) {
    return <SectionManagementView courseView={courseView} setCourseView={setCourseView} selectedCourseForSections={selectedCourseForSections} setSelectedCourseForSections={setSelectedCourseForSections} sections={sections} setSections={setSections} showAddSection={showAddSection} setShowAddSection={setShowAddSection} />;
  }

  // Show course list/management view if requested
  if (courseView === "list") {
    return <CourseListView courseView={courseView} setCourseView={setCourseView} draftCourses={draftCourses} draftCount={draftCount} setModal={setModal} setSelectedCourseForSections={setSelectedCourseForSections} setSections={setSections} setShowAddSection={setShowAddSection} setCourseView={setCourseView} />;
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
        <Panel title={`Courses (${draftCount})`}>
          {draftCourses.length > 0 ? (
            <div className="space-y-2">
              {draftCourses.map((row, index) => (
                <ContentListItem key={String(row.id ?? row.code ?? index)} row={row} type="course" onEdit={() => setModal("course", row)} onManage={() => { setSelectedCourseForSections(row); setSections([]); setShowAddSection(false); setCourseView("sections"); }} />
              ))}
            </div>
          ) : (
            <div className="text-center py-8 px-4 rounded-lg border border-dashed border-slate-200 bg-slate-50">
              <p className="text-sm font-semibold text-slate-900">No courses yet</p>
              <p className="text-xs text-slate-500 mt-1">Click "New course" to create your first course</p>
            </div>
          )}
        </Panel>
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
  setCourseView: (view: "list" | "create" | "sections" | null) => void;
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
          setCourseView("list");
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
}: {
  row: Row;
  type: "course" | "resource" | "media" | "activity" | "cohort" | "session";
  onEdit: () => void;
  onManage?: () => void;
}) {
  const icons: Record<typeof type, React.ReactNode> = {
    course: "📚",
    resource: "📄",
    media: "🎬",
    activity: "❓",
    cohort: "👥",
    session: "📅",
  };

  return (
    <div className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white p-3 hover:shadow-md transition">
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-lg">{icons[type]}</span>
          <div className="min-w-0 flex-1">
            <p className="font-semibold text-slate-950 text-sm truncate">{asText(row.title ?? row.name, "Item")}</p>
            <p className="text-xs text-slate-500 mt-0.5 truncate">{asText(row.description ?? row.code ?? row.resourceType ?? row.status, "No detail")}</p>
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
      </div>
    </div>
  );
}

function CourseListView({
  courseView,
  setCourseView,
  draftCourses,
  draftCount,
  setModal,
  setSelectedCourseForSections,
  setSections,
  setShowAddSection,
}: {
  courseView: string | null;
  setCourseView: (view: "list" | "create" | "sections" | null) => void;
  draftCourses: Row[];
  draftCount: number;
  setModal: (m: ModalKey, defaults?: Row) => void;
  setSelectedCourseForSections: (row: Row | null) => void;
  setSections: (sections: Row[]) => void;
  setShowAddSection: (show: boolean) => void;
}) {
  return (
    <div className="space-y-3 pb-20 md:pb-3">
      <button
        onClick={() => setCourseView(null)}
        className="inline-flex items-center gap-1.5 text-teal-700 hover:text-teal-900 text-sm font-semibold mb-2"
      >
        <ArrowLeft className="h-4 w-4" />
        Back
      </button>

      <Panel
        title={`Draft Courses (${draftCourses.length})`}
        action={
          <button
            onClick={() => {
              setCourseView("create");
            }}
            className="inline-flex h-8 items-center gap-1.5 rounded-md bg-teal-700 text-white px-3 text-xs font-semibold hover:bg-teal-800 transition"
          >
            <Plus className="h-3.5 w-3.5" />
            <span className="hidden sm:inline">New Course</span>
          </button>
        }
      >
        {draftCourses.length > 0 ? (
          <div className="space-y-2">
            {draftCourses.map((row, index) => (
              <div key={String(row.id ?? row.code ?? index)} className="flex items-start gap-3 rounded-lg border border-slate-200 bg-white p-3 hover:shadow-md transition">
                <div className="flex-1 min-w-0">
                  <p className="font-semibold text-slate-950 text-sm truncate">{asText(row.title ?? row.name, "Course")}</p>
                  <p className="text-xs text-slate-500 mt-1">{asText(row.code, "No code")}</p>
                  <p className="text-xs text-slate-600 mt-1 line-clamp-1">{asText(row.description, "No description")}</p>
                  <div className="flex flex-wrap gap-2 mt-2">
                    <StatusPill>{asText(row.status ?? row.courseStatus, "DRAFT")}</StatusPill>
                    {row.level ? <StatusPill>{asText(row.level as string)}</StatusPill> : null}
                    {row.estimatedDurationMinutes ? (
                      <span className="text-xs text-slate-500">
                        {asText(row.estimatedDurationMinutes as string)} min
                      </span>
                    ) : null}
                  </div>
                </div>
                <div className="flex flex-col gap-1.5 shrink-0">
                  <button
                    onClick={() => {
                      setModal("course", row);
                      setCourseView(null);
                    }}
                    className="inline-flex h-8 items-center gap-1 rounded-md border border-slate-200 bg-white px-2.5 text-xs font-medium text-slate-700 hover:bg-slate-50 transition"
                    title="Edit course"
                  >
                    <span>Edit</span>
                  </button>
                  <button
                    onClick={() => {
                      setSelectedCourseForSections(row);
                      setSections([]);
                      setShowAddSection(false);
                      setCourseView("sections");
                    }}
                    className="inline-flex h-8 items-center gap-1 rounded-md border border-slate-200 bg-white px-2.5 text-xs font-medium text-slate-700 hover:bg-slate-50 transition"
                    title="Manage sections"
                  >
                    <span>Sections</span>
                  </button>
                  <button
                    onClick={() => {
                      // Future: Delete course
                      // confirm and delete
                    }}
                    className="inline-flex h-8 items-center gap-1 rounded-md border border-red-200 bg-red-50 px-2.5 text-xs font-medium text-red-700 hover:bg-red-100 transition"
                    title="Delete course"
                  >
                    <span>Delete</span>
                  </button>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="text-center py-8 px-4 rounded-lg border border-dashed border-slate-200 bg-slate-50">
            <p className="text-sm font-semibold text-slate-900">No draft courses yet</p>
            <p className="text-xs text-slate-500 mt-1">Click "New Course" to create your first course</p>
          </div>
        )}
      </Panel>
    </div>
  );
}
