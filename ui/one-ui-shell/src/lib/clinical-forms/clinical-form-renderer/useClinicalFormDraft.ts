"use client";

import { useCallback, useEffect, useState, type Dispatch, type SetStateAction } from "react";
import type { FormValues } from "../types";

export function draftStorageKey(formId: string, patientId: string, encounterId: string): string {
  return `impilo:dak-draft:${formId}:${patientId}:${encounterId}`;
}

export function useClinicalFormDraft(
  formId: string,
  patientId: string,
  encounterId: string,
  initial: FormValues,
): {
  values: FormValues;
  setValues: Dispatch<SetStateAction<FormValues>>;
  setField: (_id: string, _v: FormValues[string]) => void;
  clearDraft: () => void;
  dirty: boolean;
} {
  const key = draftStorageKey(formId, patientId, encounterId);
  const [values, setValues] = useState<FormValues>(() => {
    if (typeof window === "undefined") return { ...initial };
    try {
      const raw = window.localStorage.getItem(key);
      if (!raw) return { ...initial };
      const parsed = JSON.parse(raw) as FormValues;
      return { ...initial, ...parsed };
    } catch {
      return { ...initial };
    }
  });
  const [dirty, setDirty] = useState(false);

  useEffect(() => {
    if (typeof window === "undefined" || !dirty) return;
    try {
      window.localStorage.setItem(key, JSON.stringify(values));
    } catch {
      /* ignore quota */
    }
  }, [values, key, dirty]);

  const setField = useCallback((id: string, v: FormValues[string]) => {
    setDirty(true);
    setValues((prev) => ({ ...prev, [id]: v }));
  }, []);

  const clearDraft = useCallback(() => {
    try {
      window.localStorage.removeItem(key);
    } catch {
      /* ignore */
    }
    setValues({ ...initial });
    setDirty(false);
  }, [key, initial]);

  return { values, setValues, setField, clearDraft, dirty };
}
