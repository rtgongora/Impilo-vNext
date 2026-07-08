"use client";

import { useEffect, useState } from "react";
import { asText, type ModalKey, type Row } from "@/components/learning/learningUtils";
import { apiClient } from "@/lib/api-client";

// Hook to fetch languages from backend
function useLanguageOptions() {
  const [languages, setLanguages] = useState<Array<{ value: string; label: string; detail: string }>>([
    { value: "en", label: "English", detail: "" }, // Fallback while loading
  ]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchLanguages = async () => {
      try {
        const response = await apiClient.get<{ data?: { items?: Array<{ code: string; label: string; nativeLabel?: string }> } }>("/internal/v1/learning/fundo/metadata/languages");
        const items = response.data?.items || [];
        const formatted = items.map((lang) => ({
          value: lang.code,
          label: lang.label,
          detail: lang.nativeLabel || "",
        }));
        if (formatted.length > 0) {
          setLanguages(formatted);
        }
      } catch (err) {
        console.error("Failed to load languages:", err);
        // Fallback to hardcoded languages if API fails
        setLanguages([
          { value: "en", label: "English", detail: "" },
          { value: "sn", label: "Shona", detail: "ChiShona" },
          { value: "nd", label: "Ndebele", detail: "isiNdebele" },
          { value: "fr", label: "French", detail: "" },
          { value: "pt", label: "Portuguese", detail: "Português" },
        ]);
      } finally {
        setLoading(false);
      }
    };
    fetchLanguages();
  }, []);

  return languages;
}

export function ModalFields({ kind, defaults = {} }: { kind: ModalKey; defaults?: Row }) {
  const courseId = asText(defaults.courseId ?? defaults.course_id ?? defaults.id ?? defaults.code, "");
  const courseTitle = asText(defaults.title ?? defaults.name ?? defaults.courseTitle, "");
  const languages = useLanguageOptions();

  if (kind === "course")
    return (
      <>
        <Field name="code" label="Code" defaultValue={asText(defaults.code, "")} required />
        <Field name="title" label="Title" defaultValue={courseTitle} required />
        <Field name="category" label="Category" defaultValue={asText(defaults.category, "")} />
        <Field name="level" label="Level" defaultValue={asText(defaults.level, "")} />
        <FieldSelect
          name="language"
          label="Course Language"
          defaultValue={asText(defaults.language, "en")}
          options={languages}
          wide
        />
        <Field name="estimatedDurationMinutes" label="Duration (minutes)" type="number" defaultValue={asText(defaults.estimatedDurationMinutes, "30")} />
        <FieldSelect
          name="dueDateType"
          label="Due Date Type"
          defaultValue={asText(defaults.dueDateType, "FIXED")}
          options={[
            { value: "FIXED", label: "Fixed Date", detail: "Exact due date for all users" },
            { value: "RELATIVE", label: "Relative", detail: "Days from enrollment date" },
          ]}
        />
        {asText(defaults.dueDateType, "FIXED") === "FIXED" && <Field name="dueDate" label="Due Date" type="datetime-local" placeholder="2026-12-31T23:59" />}
        {asText(defaults.dueDateType, "FIXED") === "RELATIVE" && <Field name="dueDateDaysFromEnrollment" label="Days from Enrollment" type="number" placeholder="14" />}
        <Field name="description" label="Description" defaultValue={asText(defaults.description, "")} area wide />
      </>
    );

  if (kind === "enrolment")
    return (
      <>
        <Field name="courseId" label="Course ID" defaultValue={courseId} required hidden />
        <Field name="pathwayId" label="Pathway ID (optional)" defaultValue={asText(defaults.pathwayId, "")} />
        <FieldSelect
          name="enrolmentType"
          label="Enrollment Type"
          defaultValue="SELF"
          options={[
            { value: "SELF", label: "Self-Enroll", detail: "You choose to enroll in this course" },
            { value: "ASSIGNED", label: "Assigned", detail: "Admin assigns the course to you" },
            { value: "COHORT", label: "Cohort", detail: "Enrolled via your learning cohort group" },
            { value: "SYSTEM", label: "System", detail: "Automatically enrolled by the system" },
          ]}
        />
        <Field name="dueAt" label="Due date (optional)" type="datetime-local" placeholder="2026-07-31T12:00" />
      </>
    );

  if (kind === "ai")
    return (
      <>
        <Field name="prompt" label="Prompt" area wide required />
        <Field name="targetCourseId" label="Target course ID" defaultValue={courseId} />
        <Field name="generationType" label="Generation type" defaultValue="course-outline" />
        <Field name="language" label="Language" defaultValue="en" />
      </>
    );

  if (kind === "library")
    return (
      <>
        <Field name="title" label="Title" defaultValue={courseTitle} required />
        <Field name="resourceType" label="Resource type" defaultValue="DOCUMENT" />
        <Field name="storageRef" label="Storage reference or URL" wide />
        <Field name="description" label="Description" defaultValue={asText(defaults.description, "")} area wide />
      </>
    );

  if (kind === "media")
    return (
      <>
        <Field name="title" label="Title" defaultValue={courseTitle} required />
        <Field name="mediaType" label="Media type" defaultValue="VIDEO" />
        <Field name="storageRef" label="Storage reference or URL" wide />
        <Field name="transcript" label="Transcript" area wide />
      </>
    );

  if (kind === "notification")
    return (
      <>
        <Field name="title" label="Title" defaultValue={courseTitle} required />
        <Field name="channelPreference" label="Channel" defaultValue="IN_APP" />
        <Field name="message" label="Message" area wide required />
      </>
    );

  if (kind === "activity")
    return (
      <>
        <Field name="title" label="Title" defaultValue={courseTitle} required />
        <Field name="activityType" label="Activity type" defaultValue="QUIZ" />
        <Field name="courseId" label="Course ID" defaultValue={courseId} />
        <Field name="lessonId" label="Lesson ID" />
        <Field name="instructions" label="Instructions" area wide />
      </>
    );

  if (kind === "cohort")
    return (
      <>
        <Field name="courseId" label="Course ID" defaultValue={courseId} required />
        <Field name="code" label="Code" />
        <Field name="title" label="Title" required />
        <Field name="status" label="Status" defaultValue="ACTIVE" />
        <Field name="startsAt" label="Starts at" placeholder="2026-07-31T12:00:00Z" />
        <Field name="endsAt" label="Ends at" placeholder="2026-08-31T12:00:00Z" />
      </>
    );

  return (
    <>
      <Field name="courseId" label="Course ID" defaultValue={courseId} required />
      <Field name="title" label="Title" required />
      <Field name="sessionType" label="Session type" defaultValue="VIRTUAL" />
      <Field name="startsAt" label="Starts at" placeholder="2026-07-31T12:00:00Z" />
      <Field name="endsAt" label="Ends at" placeholder="2026-07-31T13:00:00Z" />
      <Field name="facilitator" label="Facilitator ID" />
      <Field name="description" label="Description" area wide />
    </>
  );
}

export function Field({
  name,
  label,
  type = "text",
  area = false,
  wide = false,
  required = false,
  hidden = false,
  defaultValue,
  placeholder,
}: {
  name: string;
  label: string;
  type?: string;
  area?: boolean;
  wide?: boolean;
  required?: boolean;
  hidden?: boolean;
  defaultValue?: string;
  placeholder?: string;
}) {
  if (hidden) {
    return <input type="hidden" name={name} value={defaultValue} />;
  }
  const className = "mt-1 w-full rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100";
  return (
    <label className={wide ? "sm:col-span-2" : ""}>
      <span className="text-xs font-medium text-slate-600">{label}</span>
      {area ? (
        <textarea name={name} required={required} defaultValue={defaultValue} placeholder={placeholder} rows={4} className={className} />
      ) : (
        <input name={name} type={type} required={required} defaultValue={defaultValue} placeholder={placeholder} className={className} />
      )}
    </label>
  );
}

export function FieldSelect({
  name,
  label,
  defaultValue,
  options,
  wide = false,
}: {
  name: string;
  label: string;
  defaultValue: string;
  options: Array<{ value: string; label: string; detail: string }>;
  wide?: boolean;
}) {
  const className = "mt-1 w-full rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100";
  return (
    <label className={wide ? "sm:col-span-2" : ""}>
      <span className="text-xs font-medium text-slate-600">{label}</span>
      <select name={name} defaultValue={defaultValue} className={className}>
        {options.map((opt) => (
          <option key={opt.value} value={opt.value} title={opt.detail}>
            {opt.label}
          </option>
        ))}
      </select>
    </label>
  );
}

export function modalTitle(kind: ModalKey) {
  return {
    course: "Draft course",
    enrolment: "Create enrolment",
    ai: "Generate AI learning draft",
    library: "Add library resource",
    media: "Add media asset",
    notification: "Schedule notification",
    activity: "Create interactive activity",
    cohort: "Create cohort",
    session: "Schedule session",
  }[kind];
}
