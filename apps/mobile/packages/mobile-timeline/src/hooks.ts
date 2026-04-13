/**
 * React Hooks for Timeline — Reactive access to timeline data.
 */

import { useState, useEffect, useCallback } from "react";
import type { TimelineEvent, TimelineFilters, TimelineResponse } from "./types";
import { fetchTimeline, fetchMyTimeline } from "./timelineService";

export interface UseTimelineResult {
  events: TimelineEvent[];
  isLoading: boolean;
  /**
   * Back-compat alias used by older screens.
   */
  loading?: boolean;
  error: Error | null;
  hasMore: boolean;
  loadMore: () => void;
  refresh: () => void;
  totalElements?: number;
}

/**
 * Hook for a patient's timeline.
 */
export function useTimeline(
  cpid: string,
  filters?: TimelineFilters,
  options?: { pageSize?: number }
): UseTimelineResult {
  const [events, setEvents] = useState<TimelineEvent[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [cursor, setCursor] = useState<string | undefined>();
  const [hasMore, setHasMore] = useState(true);
  const [totalElements, setTotalElements] = useState<number | undefined>();
  const pageSize = options?.pageSize ?? 20;

  const load = useCallback(async (cursorVal: string | undefined, replace: boolean) => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchTimeline(cpid, filters, {
        cursor: cursorVal,
        size: pageSize,
      });
      setEvents((prev) => replace ? result.items : [...prev, ...result.items]);
      setCursor(result.cursor);
      setHasMore(result.hasMore);
      setTotalElements(result.totalElements);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, [cpid, filters, pageSize]);

  useEffect(() => {
    load(undefined, true);
  }, [load]);

  const loadMore = useCallback(() => {
    if (!isLoading && hasMore && cursor) {
      load(cursor, false);
    }
  }, [isLoading, hasMore, cursor, load]);

  const refresh = useCallback(() => load(undefined, true), [load]);

  return { events, isLoading, loading: isLoading, error, hasMore, loadMore, refresh, totalElements };
}

/**
 * Hook for the current user's own timeline (citizen/patient view).
 */
export function useMyTimeline(
  filters?: TimelineFilters,
  options?: { pageSize?: number }
): UseTimelineResult {
  const [events, setEvents] = useState<TimelineEvent[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [cursor, setCursor] = useState<string | undefined>();
  const [hasMore, setHasMore] = useState(true);
  const [totalElements, setTotalElements] = useState<number | undefined>();
  const pageSize = options?.pageSize ?? 20;

  const load = useCallback(async (cursorVal: string | undefined, replace: boolean) => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchMyTimeline(filters, {
        cursor: cursorVal,
        size: pageSize,
      });
      setEvents((prev) => replace ? result.items : [...prev, ...result.items]);
      setCursor(result.cursor);
      setHasMore(result.hasMore);
      setTotalElements(result.totalElements);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, [filters, pageSize]);

  useEffect(() => {
    load(undefined, true);
  }, [load]);

  const loadMore = useCallback(() => {
    if (!isLoading && hasMore && cursor) {
      load(cursor, false);
    }
  }, [isLoading, hasMore, cursor, load]);

  const refresh = useCallback(() => load(undefined, true), [load]);

  return { events, isLoading, loading: isLoading, error, hasMore, loadMore, refresh, totalElements };
}
