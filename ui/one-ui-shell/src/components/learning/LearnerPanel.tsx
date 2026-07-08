"use client";

import { useState } from "react";
import { ArrowLeft, CheckCircle2, Plus, Search } from "lucide-react";
import { asArray, asRecord, asText, type ModalKey, type Row } from "@/components/learning/learningUtils";
import { Panel, CourseCard, QuickActionCard, PathwayCard, CertificateCard, EmptyState, ActionText, rowTitle, rowDetail, learningProgressPercent, isLearningRowEnrolled } from "@/components/learning/SharedComponents";

export function Learner({ data, openSection, setModal }: { data: Record<string, unknown>; openSection: (s: SectionKey) => void; setModal: (m: ModalKey, defaults?: Row) => void }) {
  const [query, setQuery] = useState("");
  const [selectedCourse, setSelectedCourse] = useState<Row | null>(null);
  const [showBrowse, setShowBrowse] = useState(false);

  const myLearning = asRecord(data.myLearning);
  const inProgress = asArray(myLearning.inProgress);
  const recommended = asArray(myLearning.recommended);
  const enrolments = asArray(asRecord(data.enrolments).items);
  const cpdCompletions = asArray(asRecord(data.myLearning).cpdEligibleCompletions);
  const catalogItems = asArray(asRecord(data.catalog).items);
  const pathways = asArray(asRecord(data.pathways).items);
  const certificates = asArray(asRecord(data.certificates).items);

  const filteredCourses = catalogItems.filter((row) => {
    const needle = query.toLowerCase();
    const haystack = [row.title, row.name, row.courseTitle, row.code, row.description]
      .map((v) => asText(v, ""))
      .join(" ")
      .toLowerCase();
    return haystack.includes(needle);
  });

  // Show course details modal
  if (selectedCourse) {
    return (
      <div className="space-y-3 pb-20 md:pb-3">
        <button onClick={() => setSelectedCourse(null)} className="inline-flex items-center gap-1.5 text-teal-700 hover:text-teal-900 text-sm font-semibold mb-2">
          <ArrowLeft className="h-4 w-4" />
          Back
        </button>
        <Panel title="Course Details">
          <div className="space-y-4">
            <div>
              <h2 className="text-xl font-bold text-slate-950">{rowTitle(selectedCourse, "Course")}</h2>
              <p className="text-slate-600 mt-2">{rowDetail(selectedCourse)}</p>
            </div>
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
              <div className="rounded-lg bg-slate-50 p-3">
                <p className="text-xs text-slate-500">Status</p>
                <p className="font-semibold text-slate-900 mt-1">{asText(selectedCourse.status ?? selectedCourse.courseStatus, "Open")}</p>
              </div>
              <div className="rounded-lg bg-slate-50 p-3">
                <p className="text-xs text-slate-500">Level</p>
                <p className="font-semibold text-slate-900 mt-1">{asText(selectedCourse.level, "-")}</p>
              </div>
              <div className="rounded-lg bg-slate-50 p-3">
                <p className="text-xs text-slate-500">Duration</p>
                <p className="font-semibold text-slate-900 mt-1">{asText(selectedCourse.estimatedDurationMinutes, "-")} min</p>
              </div>
              <div className="rounded-lg bg-slate-50 p-3">
                <p className="text-xs text-slate-500">Category</p>
                <p className="font-semibold text-slate-900 mt-1">{asText(selectedCourse.category ?? selectedCourse.type, "-")}</p>
              </div>
            </div>
            <div className="border-t pt-4">
              <h3 className="font-semibold text-slate-900 mb-3">Description</h3>
              <p className="text-slate-600">{asText(selectedCourse.description, "No description provided")}</p>
            </div>
            <div className="flex gap-2">
              <button onClick={() => setModal("enrolment", selectedCourse)} className="flex-1 h-10 rounded-lg bg-teal-700 hover:bg-teal-800 text-white font-semibold transition">
                {isLearningRowEnrolled(selectedCourse) ? "Continue" : "Enroll"}
              </button>
              <button onClick={() => setSelectedCourse(null)} className="flex-1 h-10 rounded-lg border border-slate-200 text-slate-600 font-semibold hover:bg-slate-50 transition">
                Close
              </button>
            </div>
          </div>
        </Panel>
      </div>
    );
  }

  // Show browse/search courses view
  if (showBrowse) {
    return (
      <div className="space-y-3 pb-20 md:pb-3">
        <button onClick={() => setShowBrowse(false)} className="inline-flex items-center gap-1.5 text-teal-700 hover:text-teal-900 text-sm font-semibold mb-2">
          <ArrowLeft className="h-4 w-4" />
          Back
        </button>

        {/* Search Bar */}
        <div className="sticky top-14 md:top-16 z-30 bg-white pt-2 pb-2">
          <label className="flex h-10 items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 focus-within:border-teal-500 focus-within:ring-2 focus-within:ring-teal-100">
            <Search className="h-4 w-4 shrink-0 text-slate-400" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search courses"
              className="min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-slate-400"
              aria-label="Search courses"
            />
          </label>
        </div>

        {/* Browse Courses - NOW SHOWS ACTUAL COURSES */}
        <Panel title={`Browse Courses (${filteredCourses.length})`}>
          {filteredCourses.length > 0 ? (
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {filteredCourses.map((row, index) => (
                <CourseCard
                  key={String(index)}
                  row={row}
                  onEnroll={() => setModal("enrolment", row)}
                  onDetails={() => setSelectedCourse(row)}
                />
              ))}
            </div>
          ) : (
            <EmptyState title="No courses found" detail="Try adjusting your search" />
          )}
        </Panel>

        {/* Pathways Section */}
        {pathways.length > 0 && (
          <Panel title="Learning Pathways">
            <div className="grid gap-2 sm:grid-cols-2">
              {pathways.map((row, index) => (
                <PathwayCard key={String(row.id ?? index)} row={row} />
              ))}
            </div>
          </Panel>
        )}

        {/* Certificates Section */}
        {certificates.length > 0 && (
          <Panel title="Certificates">
            <div className="grid gap-2 sm:grid-cols-2">
              {certificates.map((row, index) => (
                <CertificateCard key={String(row.id ?? index)} row={row} />
              ))}
            </div>
          </Panel>
        )}
      </div>
    );
  }

  // Main My Learning view
  return (
    <div className="space-y-3 pb-20 md:pb-3">
      {/* In Progress Section */}
      {inProgress.length > 0 && (
        <Panel title="Currently Learning" action={<ActionText onClick={() => {}}>{inProgress.length} active</ActionText>}>
          <div className="space-y-2">
            {inProgress.map((row, index) => (
              <LearningProgressCard
                key={String(row.id ?? index)}
                row={row}
                onResume={() => setModal("enrolment", row)}
              />
            ))}
          </div>
        </Panel>
      )}

      {/* Quick Actions - Browse and Enroll */}
      <Panel title="What's next?">
        <div className="grid gap-2 sm:grid-cols-2">
          <QuickActionCard
            icon={<Search />}
            title="Browse courses"
            detail="Discover new learning"
            onClick={() => setShowBrowse(true)}
          />
          <QuickActionCard
            icon={<Plus />}
            title="Add enrollment"
            detail="Start a new course"
            onClick={() => setModal("enrolment")}
            teal
          />
        </div>
      </Panel>

      {/* Recommended Courses */}
      {recommended.length > 0 && (
        <Panel title="Recommended" action={<ActionText onClick={() => setShowBrowse(true)}>See all</ActionText>}>
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {recommended.slice(0, 3).map((row, index) => (
              <CourseCard
                key={String(row.id ?? index)}
                row={row}
                onEnroll={() => setModal("enrolment", row)}
                onDetails={() => setSelectedCourse(row)}
              />
            ))}
          </div>
        </Panel>
      )}

      {/* CPD Completions */}
      {cpdCompletions.length > 0 && (
        <Panel title="CPD Progress">
          <div className="space-y-2">
            {cpdCompletions.slice(0, 5).map((row, index) => (
              <div key={String(row.id ?? index)} className="flex items-center justify-between rounded-lg border border-emerald-200 bg-emerald-50 p-2.5">
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold text-slate-900 truncate">{asText(row.title ?? row.courseTitle, "CPD Activity")}</p>
                  <p className="text-xs text-emerald-700 mt-0.5">{asText(row.completionStatus, "Eligible")}</p>
                </div>
                <CheckCircle2 className="h-5 w-5 shrink-0 text-emerald-700" />
              </div>
            ))}
          </div>
        </Panel>
      )}

      {/* Enrolments Summary */}
      {enrolments.length > 0 && (
        <Panel title="Enrollments">
          <div className="text-sm space-y-1.5">
            {enrolments.slice(0, 4).map((row, index) => (
              <div key={String(row.id ?? index)} className="flex items-center justify-between p-2 rounded-md bg-slate-50 hover:bg-slate-100">
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium text-slate-900 truncate">{asText(row.courseTitle ?? row.title, "Course")}</p>
                  <p className="text-xs text-slate-500">{asText(row.enrolmentType, "Self")}</p>
                </div>
                <span className="text-xs font-semibold text-teal-700 whitespace-nowrap ml-2">{asText(row.status, "Active")}</span>
              </div>
            ))}
          </div>
        </Panel>
      )}
    </div>
  );
}

function LearningProgressCard({ row, onResume }: { row: Row; onResume: () => void }) {
  const progress = learningProgressPercent(row);

  return (
    <div className="flex flex-col gap-2 rounded-lg border border-teal-200 bg-gradient-to-br from-teal-50 to-teal-50/50 p-3">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0 flex-1">
          <p className="font-semibold text-slate-900 text-sm truncate">{rowTitle(row, "Course")}</p>
          <p className="text-xs text-slate-500 mt-1">{rowDetail(row)}</p>
        </div>
        <span className="inline-flex shrink-0 rounded-full bg-teal-700 text-white text-xs font-bold px-2 py-1">
          {progress}%
        </span>
      </div>
      <div className="space-y-1.5">
        <div className="h-2 bg-teal-200 rounded-full overflow-hidden">
          <div className="h-full bg-teal-600 rounded-full" style={{ width: `${progress}%` }} />
        </div>
        <button
          onClick={onResume}
          className="w-full h-8 rounded-md bg-teal-700 hover:bg-teal-800 text-white text-xs font-semibold transition"
        >
          Continue Learning
        </button>
      </div>
    </div>
  );
}

type SectionKey = string;
