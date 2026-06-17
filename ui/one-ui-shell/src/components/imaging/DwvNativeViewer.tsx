"use client";

/**
 * Governed DICOMweb WADO instance viewer powered by DWV (https://github.com/ivmartel/dwv).
 * Loads a single instance via existing BFF DICOMweb proxy — no local file upload in Phase A.
 */

import { useCallback, useEffect, useId, useLayoutEffect, useRef, useState } from "react";
import { AlertCircle, Loader2 } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { loadDwvModule, type DwvModule } from "@/lib/imaging/loadDwv";
import "./dwv-native-viewer.css";

const LOAD_TIMEOUT_MS = 20_000;

export interface DwvNativeViewerProps {
  studyUid: string;
  seriesUid: string;
  sopInstanceUid: string;
  className?: string;
}

function instanceSourceKey(studyUid: string, seriesUid: string, sopInstanceUid: string): string {
  return `${studyUid}|${seriesUid}|${sopInstanceUid}`;
}

export function DwvNativeViewer({ studyUid, seriesUid, sopInstanceUid, className = "" }: DwvNativeViewerProps) {
  const reactId = useId().replace(/:/g, "");
  const layerId = `impilo-dwv-native-${reactId}`;
  const shellRef = useRef<HTMLDivElement>(null);
  const appRef = useRef<InstanceType<DwvModule["App"]> | null>(null);
  const initStartedRef = useRef(false);
  const loadGenRef = useRef(0);
  const loadFinishedRef = useRef(false);

  const [engineReady, setEngineReady] = useState(false);
  const [dataLoading, setDataLoading] = useState(false);
  const [errorText, setErrorText] = useState<string | null>(null);
  const [statusLabel, setStatusLabel] = useState<string | null>(null);

  const fitToContainer = useCallback(() => {
    try {
      appRef.current?.fitToContainer();
    } catch {
      /* ignore */
    }
  }, []);

  const finishLoading = useCallback(() => {
    if (loadFinishedRef.current) return;
    loadFinishedRef.current = true;
    setDataLoading(false);
    requestAnimationFrame(() => fitToContainer());
  }, [fitToContainer]);

  const reportError = useCallback((message: string) => {
    setErrorText(message);
    setDataLoading(false);
  }, []);

  const teardownApp = useCallback(() => {
    const app = appRef.current;
    if (app) {
      try {
        app.reset();
      } catch {
        /* ignore */
      }
    }
    appRef.current = null;
    initStartedRef.current = false;
    setEngineReady(false);
  }, []);

  useLayoutEffect(() => {
    if (initStartedRef.current) return;

    const host = document.getElementById(layerId);
    if (!host) return;

    let cancelled = false;
    initStartedRef.current = true;

    void (async () => {
      try {
        const dwv = await loadDwvModule();
        if (cancelled) return;

        const hostEl = document.getElementById(layerId);
        if (!hostEl) {
          reportError("DICOM viewer container not found.");
          initStartedRef.current = false;
          return;
        }

        const viewConfig = new dwv.ViewConfig(layerId);
        const options = new dwv.AppOptions({ "*": [viewConfig] });
        options.tools = {
          Scroll: { options: [] },
          ZoomAndPan: { options: [] },
          WindowLevel: { options: [] },
        };

        const app = new dwv.App();

        app.addEventListener("load", () => {
          if (cancelled) return;
          finishLoading();
        });
        app.addEventListener("loadend", () => {
          if (cancelled) return;
          const ids = app.getDataIds();
          if (ids.length > 0) {
            setStatusLabel(`Loaded · ${ids.length} dataset(s)`);
          }
          finishLoading();
        });
        app.addEventListener("error", (event: unknown) => {
          if (cancelled) return;
          const msg =
            event && typeof event === "object" && "message" in event
              ? String((event as { message?: string }).message)
              : "DWV could not display this DICOM instance.";
          reportError(msg);
        });

        app.init(options);
        if (cancelled) {
          try {
            app.reset();
          } catch {
            /* ignore */
          }
          return;
        }

        try {
          app.setTool("ZoomAndPan");
        } catch {
          /* image not loaded yet */
        }

        appRef.current = app;
        setEngineReady(true);
      } catch (e) {
        initStartedRef.current = false;
        if (!cancelled) {
          reportError(e instanceof Error ? e.message : "Failed to initialise DWV viewer.");
        }
      }
    })();

    return () => {
      cancelled = true;
      teardownApp();
    };
  }, [finishLoading, layerId, reportError, teardownApp]);

  const sourceKey = instanceSourceKey(studyUid, seriesUid, sopInstanceUid);

  useEffect(() => {
    const app = appRef.current;
    if (!engineReady || !app || !sourceKey) return;

    const gen = ++loadGenRef.current;
    loadFinishedRef.current = false;
    setDataLoading(true);
    setErrorText(null);
    setStatusLabel(null);

    void (async () => {
      try {
        const blob = await apiClient.getBlob(
          `/internal/v1/pacs/dicomweb/studies/${encodeURIComponent(studyUid)}/series/${encodeURIComponent(
            seriesUid,
          )}/instances/${encodeURIComponent(sopInstanceUid)}`,
        );
        if (gen !== loadGenRef.current) return;
        if (!blob.size) {
          reportError("Empty DICOM response from governed DICOMweb proxy.");
          return;
        }
        const file = new File([blob], `${sopInstanceUid}.dcm`, { type: "application/dicom" });
        setStatusLabel(`WADO · ${sopInstanceUid.slice(0, 12)}…`);
        app.loadFiles([file]);
      } catch (e) {
        if (gen !== loadGenRef.current) return;
        reportError(
          e instanceof Error
            ? e.message
            : "Could not load DICOM instance from governed DICOMweb path. Ensure Orthanc is reachable.",
        );
      }
    })();
  }, [engineReady, reportError, seriesUid, sopInstanceUid, sourceKey, studyUid]);

  useEffect(() => {
    if (!dataLoading) return;
    const t = window.setTimeout(() => {
      if (!loadFinishedRef.current) {
        reportError("Timed out loading DICOM instance into DWV.");
      }
    }, LOAD_TIMEOUT_MS);
    return () => window.clearTimeout(t);
  }, [dataLoading, reportError]);

  useEffect(() => {
    const shell = shellRef.current;
    if (!shell || !engineReady) return;

    const onResize = () => requestAnimationFrame(() => fitToContainer());
    const ro = new ResizeObserver(onResize);
    ro.observe(shell);
    window.addEventListener("resize", onResize);

    return () => {
      ro.disconnect();
      window.removeEventListener("resize", onResize);
    };
  }, [engineReady, fitToContainer]);

  const showSpinner = !engineReady || dataLoading;

  return (
    <div className={`impilo-dwv-native-root ${className}`} ref={shellRef} aria-busy={showSpinner}>
      <div id={layerId} className="dwv-layer-host" data-testid="dwv-native-layer-host" />
      {showSpinner && !errorText && (
        <div className="pointer-events-none absolute inset-0 z-10 flex flex-col items-center justify-center bg-black/70">
          <Loader2 className="h-8 w-8 animate-spin text-primary" aria-hidden />
          <p className="mt-2 text-sm text-muted-foreground">
            {!engineReady ? "Starting DWV viewer…" : "Loading DICOM instance…"}
          </p>
        </div>
      )}
      {errorText && (
        <div
          className="absolute inset-0 z-20 flex flex-col items-center justify-center gap-2 bg-black p-6 text-center"
          role="alert"
        >
          <AlertCircle className="h-10 w-10 text-warning" aria-hidden />
          <p className="max-w-md text-sm text-warning-foreground">{errorText}</p>
        </div>
      )}
      {statusLabel && !errorText && !showSpinner && (
        <p className="absolute bottom-2 left-2 z-10 rounded bg-black/60 px-2 py-1 text-[10px] font-mono text-muted-foreground">
          {statusLabel}
        </p>
      )}
    </div>
  );
}
