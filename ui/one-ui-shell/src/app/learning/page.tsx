"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
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
import { apiClient } from "@/lib/api-client";

type ApiEnvelope<T = unknown> = { data?: T };

const FUNDO = "/internal/v1/learning/fundo";
const DEFAULT_SUBJECT = { subjectType: "USER", subjectId: "admin@mohcc.gov.zw" };

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
  const initialSection = (searchParams.get("section") as SectionKey | null) ?? "overview";
  const [section, setSection] = useState<SectionKey>(learningSections.some((s) => s.key === initialSection) ? initialSection : "overview");
  const [history, setHistory] = useState<SectionKey[]>([]);
  const [modal, setModal] = useState<ModalKey | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const subject = DEFAULT_SUBJECT;
  const [data, setData] = useState<Record<string, unknown>>({});

  const load = useCallback(async () => {
    setError(null);
    setBusy(true);
    try {
      const subjectQuery = qs({ subjectType: subject.subjectType, subjectId: subject.subjectId });
      const [
        studio,
        catalog,
        pathways,
        myLearning,
        enrolments,
        certificates,
        record,
        library,
        media,
        notifications,
        activities,
        cohorts,
        sessions,
        reportOverview,
        overdue,
        courseCompletions,
        cohortCompletions,
        assessmentPerformance,
      ] = await Promise.all([
        getData(`${FUNDO}/studio/dashboard`),
        getData(`${FUNDO}/catalog?limit=50`),
        getData(`${FUNDO}/pathways?status=PUBLISHED&limit=50`),
        getData(`${FUNDO}/my-learning${subjectQuery}`),
        getData(`${FUNDO}/enrolments${qs({ ...subject, limit: 100 })}`),
        getData(`${FUNDO}/certificates${subjectQuery}`),
        getData(`${FUNDO}/subjects/${encodeURIComponent(subject.subjectType)}/${encodeURIComponent(subject.subjectId)}/record`),
        getData(`${FUNDO}/library/resources?limit=50`),
        getData(`${FUNDO}/media/assets?limit=50`),
        getData(`${FUNDO}/notifications${qs({ ...subject, limit: 50 })}`),
        getData(`${FUNDO}/interactive/activities?limit=50`),
        getData(`${FUNDO}/cohorts?limit=50`),
        getData(`${FUNDO}/sessions?limit=50`),
        getData(`${FUNDO}/reports/overview`),
        getData(`${FUNDO}/reports/overdue-learning?limit=50`),
        getData(`${FUNDO}/reports/course-completions?limit=50`),
        getData(`${FUNDO}/reports/cohort-completions?limit=50`),
        getData(`${FUNDO}/reports/assessment-performance?limit=50`),
      ]);
      setData({
        studio,
        catalog,
        pathways,
        myLearning,
        enrolments,
        certificates,
        record,
        library,
        media,
        notifications,
        activities,
        cohorts,
        sessions,
        reportOverview,
        overdue,
        courseCompletions,
        cohortCompletions,
        assessmentPerformance,
      });
    } catch (err) {
      const message = asRecord(asRecord(err).error).message;
      setError(typeof message === "string" ? message : "Learning service is unavailable or returned an unexpected response.");
    } finally {
      setBusy(false);
    }
  }, [subject]);

  useEffect(() => {
    void load();
  }, [load]);

  const openSection = (next: SectionKey) => {
    if (next === section) return;
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

  const submit = async (kind: ModalKey, form: HTMLFormElement) => {
    setError(null);
    setNotice(null);
    setBusy(true);
    const values = Object.fromEntries(new FormData(form).entries());
    const body = Object.fromEntries(Object.entries(values).filter(([, value]) => String(value).trim() !== ""));
    try {
      if (kind === "course") {
        await postData(`${FUNDO}/catalog`, { status: "DRAFT", ...body, estimatedDurationMinutes: Number(body.estimatedDurationMinutes ?? 30) });
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
      setModal(null);
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
      <div className="-mt-1 space-y-2">
        <LearningWorkspaceHeader
          section={section}
          busy={busy}
          historyLength={history.length}
          onSectionChange={openSection}
          onBack={goBack}
          onRefresh={() => void load()}
        />

        {error ? <Banner tone="error">{error}</Banner> : null}
        {notice ? <Banner tone="success">{notice}</Banner> : null}

        <main className="min-w-0">
          <LearningWorkspaceMain section={section} data={data} openSection={openSection} setModal={setModal} />
        </main>
      </div>

      {modal ? (
        <LearningModal title={modalTitle(modal)} onClose={() => setModal(null)} busy={busy} onSubmit={handleModalSubmit}>
          <ModalFields kind={modal} />
        </LearningModal>
      ) : null}
    </AppLayout>
  );
}
