"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { AppLayout } from "@/components/AppLayout";
import {
  Banner,
  LearningModal,
  LearningWorkspaceHeader,
  LearningWorkspaceMain,
  ModalFields,
  learningSections,
  modalTitle,
} from "@/components/learning/LearningWorkspace";
import { asRecord, type ModalKey, type Row, type SectionKey } from "@/components/learning/learningUtils";
import { useAuthStore } from "@/hooks/useAuthStore";
import { apiClient } from "@/lib/api-client";

type ApiEnvelope<T = unknown> = { data?: T };

const FUNDO = "/internal/v1/learning/fundo";
const LEARNER_FALLBACK_SUBJECT = { subjectType: "USER", subjectId: "admin@mohcc.gov.zw" };

function qs(params: Record<string, string | number | boolean | undefined>) {
  const out = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== "") out.set(key, String(value));
  });
  const s = out.toString();
  return s ? `?${s}` : "";
}

async function getData<T = Row>(path: string): Promise<T> {
  const res = await apiClient.get<ApiEnvelope<T>>(path);
  return (res.data ?? ({} as T)) as T;
}

async function postData<T = Row>(path: string, body: Row): Promise<T> {
  const res = await apiClient.post<ApiEnvelope<T>>(path, body);
  return (res.data ?? ({} as T)) as T;
}

export default function LearningPage() {
  const searchParams = useSearchParams();
  const user = useAuthStore((state) => state.user);
  const hasRole = useAuthStore((state) => state.hasRole);
  const isSystemAdmin = hasRole("SYSTEM_ADMIN");
  const canLearn = !isSystemAdmin;
  const canUseStudio = isSystemAdmin;
  const canViewReports = isSystemAdmin || hasRole("FACILITY_ADMIN");
  const sections = useMemo(() => learningSections.filter((item) => {
    if (item.key === "studio") return canUseStudio;
    if (item.key === "learner") return canLearn;
    if (item.key === "reports") return canViewReports;
    return true;
  }), [canLearn, canUseStudio, canViewReports]);
  const initialSection = (searchParams.get("section") as SectionKey | null) ?? "overview";
  const [section, setSection] = useState<SectionKey>(sections.some((s) => s.key === initialSection) ? initialSection : "overview");
  const [history, setHistory] = useState<SectionKey[]>([]);
  const [modal, setModal] = useState<ModalKey | null>(null);
  const [modalDefaults, setModalDefaults] = useState<Row>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const subject = useMemo(
    () => ({ subjectType: "USER", subjectId: user?.email ?? user?.id ?? LEARNER_FALLBACK_SUBJECT.subjectId }),
    [user?.email, user?.id],
  );
  const [data, setData] = useState<Record<string, unknown>>({});

  const load = useCallback(async () => {
    setError(null);
    setBusy(true);
    try {
      const subjectQuery = qs({ subjectType: subject.subjectType, subjectId: subject.subjectId });
      const baseRequests: Array<[string, Promise<unknown>]> = [
        ["catalog", getData(`${FUNDO}/catalog?limit=50`)],
        ["pathways", getData(`${FUNDO}/pathways?status=PUBLISHED&limit=50`)],
      ];
      const learnerRequests: Array<[string, Promise<unknown>]> = canLearn
        ? [
            ["myLearning", getData(`${FUNDO}/my-learning${subjectQuery}`)],
            ["enrolments", getData(`${FUNDO}/enrolments${qs({ ...subject, limit: 100 })}`)],
            ["certificates", getData(`${FUNDO}/certificates${subjectQuery}`)],
            ["record", getData(`${FUNDO}/subjects/${encodeURIComponent(subject.subjectType)}/${encodeURIComponent(subject.subjectId)}/record`)],
          ]
        : [];
      const studioRequests: Array<[string, Promise<unknown>]> = canUseStudio
        ? [
            ["studio", getData(`${FUNDO}/studio/dashboard`)],
            ["catalogAll", getData(`${FUNDO}/catalog?status=ALL&limit=100`)],
            ["library", getData(`${FUNDO}/library/resources?limit=50`)],
            ["media", getData(`${FUNDO}/media/assets?limit=50`)],
            ["notifications", getData(`${FUNDO}/notifications${qs({ ...subject, limit: 50 })}`)],
            ["activities", getData(`${FUNDO}/interactive/activities?limit=50`)],
            ["cohorts", getData(`${FUNDO}/cohorts?limit=50`)],
            ["sessions", getData(`${FUNDO}/sessions?limit=50`)],
          ]
        : [];
      const reportRequests: Array<[string, Promise<unknown>]> = canViewReports
        ? [
            ["reportOverview", getData(`${FUNDO}/reports/overview`)],
            ["overdue", getData(`${FUNDO}/reports/overdue-learning?limit=50`)],
            ["courseCompletions", getData(`${FUNDO}/reports/course-completions?limit=50`)],
            ["cohortCompletions", getData(`${FUNDO}/reports/cohort-completions?limit=50`)],
            ["assessmentPerformance", getData(`${FUNDO}/reports/assessment-performance?limit=50`)],
          ]
        : [];

      const entries = await Promise.all([...baseRequests, ...learnerRequests, ...studioRequests, ...reportRequests].map(
        async ([key, request]) => [key, await request] as const,
      ));
      const nextData = Object.fromEntries(entries);
      setData({
        studio: {},
        catalog: {},
        catalogAll: {},
        pathways: {},
        myLearning: {},
        enrolments: {},
        certificates: {},
        record: {},
        library: {},
        media: {},
        notifications: {},
        activities: {},
        cohorts: {},
        sessions: {},
        reportOverview: {},
        overdue: {},
        courseCompletions: {},
        cohortCompletions: {},
        assessmentPerformance: {},
        ...nextData,
      });
    } catch (err) {
      const message = asRecord(asRecord(err).error).message;
      setError(typeof message === "string" ? message : "Learning service is unavailable or returned an unexpected response.");
    } finally {
      setBusy(false);
    }
  }, [canLearn, canUseStudio, canViewReports, subject]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!sections.some((item) => item.key === section)) {
      setSection("overview");
      setHistory([]);
    }
  }, [section, sections]);

  const openSection = (next: SectionKey) => {
    if (next === section) return;
    if (!sections.some((item) => item.key === next)) return;
    setHistory((prev) => [...prev, section]);
    setSection(next);
    setNotice(null);
  };

  const goBack = () => {
    setHistory((prev) => {
      const copy = [...prev];
      const previous = copy.pop();
      if (previous) setSection(previous);
      return copy;
    });
  };

  const openModal = (next: ModalKey, defaults: Row = {}) => {
    setModalDefaults(defaults);
    setModal(next);
  };

  const closeModal = () => {
    setModal(null);
    setModalDefaults({});
  };

  const submit = async (kind: ModalKey, form: HTMLFormElement) => {
    setError(null);
    setNotice(null);
    setBusy(true);
    const formData = new FormData(form);
    const values = Object.fromEntries(formData.entries());
    const body = Object.fromEntries(Object.entries(values).filter(([, value]) => String(value).trim() !== ""));
    try {
      if (kind === "course") {
        const courseBody = normalizeCourseBody(formData);
        const courseId = asRecord(modalDefaults).id;
        if (typeof courseId === "string" && courseId) {
          await apiClient.put(`${FUNDO}/catalog/${courseId}`, courseBody);
        } else {
          await postData(`${FUNDO}/catalog`, { status: "DRAFT", ...courseBody });
        }
      } else if (kind === "enrolment") {
        await postData(`${FUNDO}/enrolments`, { ...subject, ...body });
      } else if (kind === "ai") {
        await postData(`${FUNDO}/ai/generate`, body);
      } else if (kind === "library") {
        await postData(`${FUNDO}/library/resources`, body);
      } else if (kind === "media") {
        await postData(`${FUNDO}/media/assets`, body);
      } else if (kind === "notification") {
        await postData(`${FUNDO}/notifications`, { ...subject, ...body });
      } else if (kind === "activity") {
        await postData(`${FUNDO}/interactive/activities`, body);
      } else if (kind === "cohort") {
        await postData(`${FUNDO}/cohorts`, body);
      } else if (kind === "session") {
        await postData(`${FUNDO}/sessions`, body);
      }
      closeModal();
      setNotice("Saved successfully.");
      await load();
    } catch (err) {
      const message = asRecord(asRecord(err).error).message;
      setError(typeof message === "string" ? message : "The request could not be completed.");
    } finally {
      setBusy(false);
    }
  };

  const handleModalSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (modal) void submit(modal, event.currentTarget);
  };

  return (
    <AppLayout inlineContext>
      <LearningWorkspaceHeader
        section={section}
        busy={busy}
        historyLength={history.length}
        sections={sections}
        onSectionChange={openSection}
        onBack={goBack}
        onRefresh={() => void load()}
      />

      <div className="px-3 py-3 sm:px-4 space-y-2">
        {error ? <Banner tone="error">{error}</Banner> : null}
        {notice ? <Banner tone="success">{notice}</Banner> : null}

        <main className="min-w-0">
          <LearningWorkspaceMain
            section={section}
            data={data}
            openSection={openSection}
            setModal={openModal}
            canLearn={canLearn}
            canUseStudio={canUseStudio}
            canViewReports={canViewReports}
          />
        </main>
      </div>

      {modal ? (
        <LearningModal title={modalTitle(modal)} onClose={closeModal} busy={busy} onSubmit={handleModalSubmit}>
          <ModalFields kind={modal} defaults={modalDefaults} />
        </LearningModal>
      ) : null}
    </AppLayout>
  );
}

function normalizeCourseBody(formData: FormData) {
  const body = Object.fromEntries(formData.entries());
  const audienceType = String(body.audienceType ?? "ALL_LEARNERS");
  const dueDateType = String(body.dueDateType ?? "");
  const audienceRoles = formData.getAll("audienceRoles")
    .map((value) => String(value))
    .filter(Boolean)
    .join(",");
  const out: Row = {
    ...Object.fromEntries(Object.entries(body).filter(([, value]) => String(value).trim() !== "")),
    estimatedDurationMinutes: Number(body.estimatedDurationMinutes ?? 30),
    mandatory: body.mandatory === "true" || body.mandatory === "on",
    audienceType,
  };
  delete out.audienceRoles;
  delete out.dueDate;
  delete out.dueDateDaysFromEnrollment;
  if (audienceType === "SPECIFIC_ROLES" && audienceRoles) {
    out.audienceRoles = audienceRoles;
  }
  if (dueDateType === "FIXED" && body.dueDate) {
    out.dueDateType = "FIXED";
    out.dueDate = toOffsetDateTime(String(body.dueDate));
  } else if (dueDateType === "RELATIVE" && body.dueDateDaysFromEnrollment) {
    out.dueDateType = "RELATIVE";
    out.dueDateDaysFromEnrollment = Number(body.dueDateDaysFromEnrollment);
  } else {
    delete out.dueDateType;
  }
  return out;
}

function toOffsetDateTime(value: string) {
  if (!value) return value;
  if (/[zZ]|[+-]\d{2}:\d{2}$/.test(value)) return value;
  return `${value}:00Z`;
}
