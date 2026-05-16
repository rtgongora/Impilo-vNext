/**
 * React hooks for the Ndila mobile SDK.
 *
 * Each hook is intentionally small and side-effect-bounded so screens can
 * compose them without orchestrating queries / mutations / queues by hand.
 */

import { useCallback, useEffect, useState } from "react";
import { ndilaMobile } from "./ndilaClient";
import { captureCurrentLocation, haversineMeters, type GpsCaptureResult } from "./gpsCapture";
import { flushQueue, pendingCount, queueLocationCapture, subscribeQueue } from "./offlineQueue";
import type { NdilaCoordinate, NdilaNearbyResult, NdilaTileConfig } from "./types";

export function useNdilaTileConfig() {
  const [config, setConfig] = useState<NdilaTileConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    ndilaMobile.tileConfig()
      .then((cfg) => { if (!cancelled) setConfig(cfg); })
      .catch((e) => { if (!cancelled) setError(e); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);
  return { config, loading, error };
}

export function useNearbyServices(origin: NdilaCoordinate | null, radiusMeters = 10_000, entityTypes?: string[]) {
  const [results, setResults] = useState<NdilaNearbyResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<unknown>(null);
  useEffect(() => {
    if (!origin) { setResults([]); return; }
    let cancelled = false;
    setLoading(true);
    ndilaMobile.nearby({ origin, radiusMeters, entityTypes })
      .then((r) => { if (!cancelled) setResults(r.items ?? []); })
      .catch((e) => { if (!cancelled) setError(e); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [origin?.latitude, origin?.longitude, radiusMeters, JSON.stringify(entityTypes ?? [])]);
  return { results, loading, error };
}

export function useDeviceLocation(autoCapture = false) {
  const [location, setLocation] = useState<GpsCaptureResult | null>(null);
  const [capturing, setCapturing] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const capture = useCallback(async () => {
    setCapturing(true); setError(null);
    try {
      const r = await captureCurrentLocation({ highAccuracy: true });
      setLocation(r);
      return r;
    } catch (e) { setError(e); throw e; }
    finally { setCapturing(false); }
  }, []);

  useEffect(() => { if (autoCapture) capture().catch(() => {}); }, [autoCapture, capture]);
  return { location, capture, capturing, error };
}

export function useNdilaOfflineQueue() {
  const [count, setCount] = useState<number>(pendingCount());
  useEffect(() => subscribeQueue(() => setCount(pendingCount())), []);

  const queueCapture = useCallback((capture: Parameters<typeof queueLocationCapture>[0]) => {
    return queueLocationCapture(capture);
  }, []);
  const flush = useCallback(() => flushQueue(), []);
  return { pendingCount: count, queueCapture, flush };
}

/** Pure helper, kept exported for screens that need it. */
export const haversine = haversineMeters;
