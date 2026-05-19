"use client";

import { useMemo } from "react";

export interface LearningSubject {
  subjectType: string;
  subjectId: string;
}

const DEFAULT_SUBJECT: LearningSubject = {
  subjectType: "PROVIDER",
  subjectId: "demo-provider",
};

export function useLearningSubject(): LearningSubject {
  return useMemo(() => {
    if (typeof window === "undefined") return DEFAULT_SUBJECT;
    const type = window.localStorage.getItem("learning.subjectType");
    const id = window.localStorage.getItem("learning.subjectId");
    if (!type || !id) return DEFAULT_SUBJECT;
    return { subjectType: type, subjectId: id };
  }, []);
}
