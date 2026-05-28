"use client";

import { useMemo } from "react";
import { useAuthStore } from "@/hooks/useAuthStore";

export interface LearningSubject {
  subjectType: string;
  subjectId: string;
}

const DEFAULT_SUBJECT: LearningSubject = {
  subjectType: "USER_HEALTH_ID",
  subjectId: "",
};

export function useLearningSubject(): LearningSubject {
  const user = useAuthStore((state) => state.user);
  return useMemo(() => {
    const providerId = user?.providerId?.trim();
    if (providerId) {
      return { subjectType: "PROVIDER_PUBLIC_ID", subjectId: providerId };
    }
    const healthId = user?.healthId?.trim() || user?.id?.trim();
    if (healthId) {
      return { subjectType: "USER_HEALTH_ID", subjectId: healthId };
    }
    if (typeof window === "undefined") return DEFAULT_SUBJECT;
    const explicitProvider = window.sessionStorage.getItem("exp:provider_id");
    if (explicitProvider && explicitProvider.trim()) {
      return { subjectType: "PROVIDER_PUBLIC_ID", subjectId: explicitProvider.trim() };
    }
    const type = window.localStorage.getItem("learning.subjectType");
    const id = window.localStorage.getItem("learning.subjectId");
    if (type && id) {
      return { subjectType: type, subjectId: id };
    }
    return DEFAULT_SUBJECT;
  }, [user?.providerId, user?.healthId, user?.id]);
}
