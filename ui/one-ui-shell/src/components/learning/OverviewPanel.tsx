"use client";

import { BookOpenCheck, CheckCircle2, Clock3, FileText, Library, PlayCircle, Radio, Route, Search, Activity } from "lucide-react";
import { asArray, asRecord, type ModalKey, type Row, type SectionKey } from "@/components/learning/learningUtils";
import { Panel, CreationCard, CourseCard, QuickActionCard, StatCard, ActionText, countOf, rowTitle, learningProgressPercent } from "@/components/learning/SharedComponents";

export function Overview({
  data,
  openSection,
  setModal,
  canLearn = true,
  canUseStudio = false,
}: {
  data: Record<string, unknown>;
  openSection: (s: SectionKey) => void;
  setModal: (m: ModalKey, defaults?: Row) => void;
  canLearn?: boolean;
  canUseStudio?: boolean;
}) {
  const catalog = asRecord(data.catalog);
  const studio = asRecord(data.studio);
  const myLearning = asRecord(data.myLearning);
  const recommended = asArray(myLearning.recommended);
  const inProgress = asArray(myLearning.inProgress);
  const courses = asArray(catalog.items);
  const courseCount = countOf(catalog.totalElements, asArray(catalog.items).length);
  const pathwayCount = asArray(asRecord(data.pathways).items).length;
  const enrolmentCount = asArray(asRecord(data.enrolments).items).length;
  const certificateCount = asArray(asRecord(data.certificates).items).length;
  const libraryCount = countOf(studio.libraryResources, asArray(asRecord(data.library).items).length);
  const mediaCount = asArray(asRecord(data.media).items).length;
  const activityCount = asArray(asRecord(data.activities).items).length;
  const sessionCount = asArray(asRecord(data.sessions).items).length;
  const overdueCount = asArray(asRecord(data.overdue).items).length;
  const draftCount = countOf(studio.draftCourses, 0);
  const nextCourse = inProgress[0];
  const relevantRows = [...recommended, ...courses].slice(0, 3);

  return (
    <div className="space-y-3 pb-20 md:pb-3">
      {/* Primary Action - Continue Learning */}
      {canLearn && nextCourse && (
        <div className="rounded-lg bg-gradient-to-r from-teal-50 to-teal-100/50 border border-teal-200 p-4 sm:p-5">
          <div className="flex items-start gap-3 justify-between">
            <div className="min-w-0 flex-1">
              <p className="text-xs font-semibold text-teal-700 uppercase tracking-wide">Continue Learning</p>
              <h2 className="mt-1 text-lg sm:text-xl font-bold text-slate-900 truncate">{rowTitle(nextCourse, "Course")}</h2>
              <p className="mt-2 text-sm text-slate-600">{learningProgressPercent(nextCourse)}% complete</p>
              <div className="mt-3 h-2 bg-teal-200 rounded-full overflow-hidden">
                <div className="h-full bg-teal-600 rounded-full" style={{ width: `${learningProgressPercent(nextCourse)}%` }} />
              </div>
            </div>
            <button
              onClick={() => openSection("learner")}
              className="flex-shrink-0 inline-flex h-10 items-center gap-1.5 rounded-lg bg-teal-700 hover:bg-teal-800 text-white px-4 text-sm font-semibold transition"
            >
              <PlayCircle className="h-4 w-4" />
              <span className="hidden sm:inline">Resume</span>
            </button>
          </div>
        </div>
      )}

      {/* Recommended Courses Section */}
      {canLearn && relevantRows.length > 0 && (
        <Panel title="Recommended for you" action={<ActionText onClick={() => openSection("learner")}>See all</ActionText>}>
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {relevantRows.map((row, index) => (
              <CourseCard
                key={String(row.id ?? row.code ?? index)}
                row={row}
                onEnroll={() => setModal("enrolment", row)}
                onDetails={() => openSection("learner")}
              />
            ))}
          </div>
        </Panel>
      )}

      {/* Learning Path */}
      {canLearn ? (
        <Panel title="My learning path" action={<ActionText onClick={() => openSection("learner")}>View all</ActionText>}>
          <div className="grid gap-2 sm:grid-cols-2">
            <QuickActionCard
              icon={<Route />}
              title="Continue learning"
              detail={`${enrolmentCount} active enrollments`}
              onClick={() => openSection("learner")}
              teal
            />
            <QuickActionCard
              icon={<Search />}
              title="Browse courses"
              detail={`${courseCount} courses available`}
              onClick={() => openSection("learner")}
            />
          </div>
        </Panel>
      ) : null}

      {/* Creation Tools - Navigation to dedicated list pages */}
      {canUseStudio && draftCount >= 0 && (
        <Panel title="Create & coordinate" action={<ActionText onClick={() => openSection("studio")}>Full studio</ActionText>}>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            <CreationCard icon={<BookOpenCheck />} label="Courses" onClick={() => openSection("studio")} />
            <CreationCard icon={<FileText />} label="Resources" onClick={() => openSection("studio")} />
            <CreationCard icon={<Radio />} label="Media" onClick={() => openSection("studio")} />
            <CreationCard icon={<Activity />} label="Activities" onClick={() => openSection("studio")} />
          </div>
        </Panel>
      )}
    </div>
  );
}
