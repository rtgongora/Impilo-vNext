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
  const [audienceType, setAudienceType] = useState(asText(defaults.audienceType, "ALL_LEARNERS"));
  const [dueDateType, setDueDateType] = useState(asText(defaults.dueDateType, ""));
  const selectedRoles = asText(defaults.audienceRoles, "")
    .split(",")
    .map((role) => role.trim())
    .filter(Boolean);

  if (kind === "course")
    return (
      <>
        <Field name="code" label="Code" defaultValue={asText(defaults.code, "")} required />
        <Field name="title" label="Title" defaultValue={courseTitle} required />
        <FieldSelect
          name="category"
          label="Category"
          defaultValue={asText(defaults.category, "GENERAL")}
          options={[
            { value: "GENERAL", label: "General", detail: "" },
            { value: "CLINICAL_GOVERNANCE", label: "Clinical Governance", detail: "" },
            { value: "PATIENT_SAFETY", label: "Patient Safety", detail: "" },
            { value: "DIGITAL_HEALTH", label: "Digital Health", detail: "" },
            { value: "DATA_QUALITY", label: "Data Quality", detail: "" },
            { value: "PUBLIC_HEALTH", label: "Public Health", detail: "" },
            { value: "OPERATIONS", label: "Operations", detail: "" },
            { value: "COMPLIANCE", label: "Compliance", detail: "" },
          ]}
        />
        <FieldSelect
          name="level"
          label="Level"
          defaultValue={asText(defaults.level, "FOUNDATION")}
          options={[
            { value: "FOUNDATION", label: "Foundation", detail: "" },
            { value: "INTERMEDIATE", label: "Intermediate", detail: "" },
            { value: "ADVANCED", label: "Advanced", detail: "" },
            { value: "REFRESHER", label: "Refresher", detail: "" },
          ]}
        />
        <FieldSelect
          name="language"
          label="Course Language"
          defaultValue={asText(defaults.language, "en")}
          options={languages}
          wide
        />
        <Field name="estimatedDurationMinutes" label="Duration (minutes)" type="number" defaultValue={asText(defaults.estimatedDurationMinutes, "30")} />
        <FieldSelect
          name="status"
          label="Status"
          defaultValue={asText(defaults.status, "DRAFT")}
          options={[
            { value: "DRAFT", label: "Draft", detail: "" },
            { value: "PUBLISHED", label: "Published", detail: "" },
            { value: "ARCHIVED", label: "Archived", detail: "" },
          ]}
        />
        <FieldCheckbox name="mandatory" label="Mandatory" defaultChecked={defaults.mandatory === true || defaults.mandatory === "true"} />
        <FieldSelect
          name="audienceType"
          label="Participants"
          defaultValue={audienceType}
          onChange={setAudienceType}
          wide={audienceType !== "SPECIFIC_ROLES"}
          options={[
            { value: "ALL_LEARNERS", label: "All Learners", detail: "" },
            { value: "SYSTEM_USERS", label: "System Users", detail: "" },
            { value: "CITIZENS", label: "Citizens", detail: "" },
            { value: "SPECIFIC_ROLES", label: "Specific Roles", detail: "" },
          ]}
        />
        {audienceType === "SPECIFIC_ROLES" ? (
          <FieldMultiSelect
            name="audienceRoles"
            label="Roles"
            defaultValues={selectedRoles}
            options={[
              "FACILITY_ADMIN",
              "CLINICIAN",
              "NURSE",
              "PHARMACIST",
              "FINANCE",
              "SUPPORT_AGENT",
              "PUBLIC_HEALTH_OFFICER",
              "ENV_HEALTH",
              "CHW",
              "CAREGIVER",
              "CARE_PARTNER",
            ]}
          />
        ) : null}
        <FieldSelect
          name="dueDateType"
          label="Due Date Type"
          defaultValue={dueDateType}
          onChange={setDueDateType}
          options={[
            { value: "", label: "No Due Date", detail: "" },
            { value: "FIXED", label: "Fixed Date", detail: "" },
            { value: "RELATIVE", label: "Relative", detail: "" },
          ]}
        />
        {dueDateType === "FIXED" ? (
          <Field name="dueDate" label="Due Date" type="datetime-local" defaultValue={toDateTimeLocal(defaults.dueDate)} placeholder="2026-12-31T23:59" required />
        ) : null}
        {dueDateType === "RELATIVE" ? (
          <Field
            name="dueDateDaysFromEnrollment"
            label="Days after Enrollment"
            type="number"
            defaultValue={asText(defaults.dueDateDaysFromEnrollment, "")}
            placeholder="14"
            required
          />
        ) : null}
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
  onChange,
}: {
  name: string;
  label: string;
  defaultValue: string;
  options: Array<{ value: string; label: string; detail: string }>;
  wide?: boolean;
  onChange?: (value: string) => void;
}) {
  const className = "mt-1 w-full rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100";
  return (
    <label className={wide ? "sm:col-span-2" : ""}>
      <span className="text-xs font-medium text-slate-600">{label}</span>
      <select name={name} defaultValue={defaultValue} onChange={(event) => onChange?.(event.target.value)} className={className}>
        {options.map((opt) => (
          <option key={opt.value} value={opt.value} title={opt.detail}>
            {opt.label}
          </option>
        ))}
      </select>
    </label>
  );
}

export function FieldMultiSelect({
  name,
  label,
  defaultValues,
  options,
}: {
  name: string;
  label: string;
  defaultValues: string[];
  options: string[];
}) {
  const className = "mt-1 h-32 w-full rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100";
  return (
    <label>
      <span className="text-xs font-medium text-slate-600">{label}</span>
      <select name={name} defaultValue={defaultValues} multiple required className={className}>
        {options.map((role) => (
          <option key={role} value={role}>
            {role.replace(/_/g, " ")}
          </option>
        ))}
      </select>
    </label>
  );
}

export function FieldCheckbox({
  name,
  label,
  defaultChecked,
}: {
  name: string;
  label: string;
  defaultChecked?: boolean;
}) {
  return (
    <label className="mt-5 inline-flex h-10 items-center gap-2 rounded-md border border-slate-200 px-3 text-sm font-medium text-slate-700">
      <input type="checkbox" name={name} value="true" defaultChecked={defaultChecked} className="h-4 w-4 rounded border-slate-300 text-teal-700 focus:ring-teal-500" />
      <span>{label}</span>
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

function toDateTimeLocal(value: unknown) {
  const text = asText(value, "");
  if (!text) return "";
  return text.slice(0, 16);
}
