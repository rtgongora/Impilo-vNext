"use client";

/**
 * DICOM viewer powered by DWV (https://github.com/ivmartel/dwv).
 * React owns the DOM — DWV mounts into a stable layer div (never use innerHTML).
 */

import {
  forwardRef,
  useCallback,
  useEffect,
  useId,
  useImperativeHandle,
  useLayoutEffect,
  useRef,
  useState,
} from "react";
import { AlertCircle, Loader2 } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { loadDwvModule, type DwvModule } from "@/lib/dicom/loadDwv";
import "./dwv-viewer.css";

export type DwvToolName = "Scroll" | "ZoomAndPan" | "WindowLevel" | "Draw";
export type DwvDrawShape = "Ruler" | "Rectangle" | "Ellipse" | "Arrow";

export interface DwvClinicalViewerHandle {
  setTool: (tool: DwvToolName) => void;
  setDrawShape: (shape: DwvDrawShape) => void;
  resetView: () => void;
  fitToContainer: () => void;
}

export interface DwvClinicalViewerProps {
  files?: File[];
  pacsInstanceId?: string;
  activeTool?: DwvToolName;
  drawShape?: DwvDrawShape;
  windowPreset?: string;
  className?: string;
  onReady?: () => void;
  onError?: (message: string) => void;
  onLabel?: (label: string) => void;
}

const LOAD_TIMEOUT_MS = 20_000;

function fileSignature(files?: File[]): string {
  const f = files?.[0];
  if (!f) return "";
  return `${f.name}|${f.size}|${f.lastModified}`;
}

export const DwvClinicalViewer = forwardRef<DwvClinicalViewerHandle, DwvClinicalViewerProps>(
  function DwvClinicalViewer(
    {
      files,
      pacsInstanceId,
      activeTool = "ZoomAndPan",
      drawShape = "Ruler",
      windowPreset,
      className = "",
      onReady,
      onError,
      onLabel,
    },
    ref,
  ) {
    const reactId = useId().replace(/:/g, "");
    const layerId = `impilo-dwv-${reactId}`;
    const shellRef = useRef<HTMLDivElement>(null);
    const appRef = useRef<InstanceType<DwvModule["App"]> | null>(null);
    const initStartedRef = useRef(false);
    const loadGenRef = useRef(0);
    const loadFinishedRef = useRef(false);

    const activeToolRef = useRef(activeTool);
    const drawShapeRef = useRef(drawShape);
    const windowPresetRef = useRef(windowPreset);
    const onReadyRef = useRef(onReady);
    const onErrorRef = useRef(onError);
    const onLabelRef = useRef(onLabel);
    activeToolRef.current = activeTool;
    drawShapeRef.current = drawShape;
    windowPresetRef.current = windowPreset;
    onReadyRef.current = onReady;
    onErrorRef.current = onError;
    onLabelRef.current = onLabel;

    const [engineReady, setEngineReady] = useState(false);
    const [dataLoading, setDataLoading] = useState(false);
    const [errorText, setErrorText] = useState<string | null>(null);

    const applyTool = useCallback((app: InstanceType<DwvModule["App"]>, tool: DwvToolName) => {
      try {
        app.setTool(tool);
        if (tool === "Draw") {
          app.setToolFeatures({ shapeName: drawShapeRef.current });
        }
      } catch {
        /* image may not be loaded yet */
      }
    }, []);

    const fitToContainer = useCallback(() => {
      try {
        appRef.current?.fitToContainer();
      } catch {
        /* ignore */
      }
    }, []);

    const resetView = useCallback(() => {
      const app = appRef.current;
      if (!app) return;
      try {
        app.resetDisplay();
        app.fitToContainer();
        applyTool(app, activeToolRef.current);
        if (windowPresetRef.current) {
          app.setWindowLevelPreset(windowPresetRef.current);
        }
      } catch {
        /* ignore */
      }
    }, [applyTool]);

    useImperativeHandle(
      ref,
      () => ({
        setTool: (tool) => {
          activeToolRef.current = tool;
          const app = appRef.current;
          if (app) applyTool(app, tool);
        },
        setDrawShape: (shape) => {
          drawShapeRef.current = shape;
          const app = appRef.current;
          if (app && activeToolRef.current === "Draw") {
            try {
              app.setTool("Draw");
              app.setToolFeatures({ shapeName: shape });
            } catch {
              /* ignore */
            }
          }
        },
        resetView,
        fitToContainer,
      }),
      [applyTool, fitToContainer, resetView],
    );

    const finishLoading = useCallback(() => {
      if (loadFinishedRef.current) return;
      loadFinishedRef.current = true;
      setDataLoading(false);
      onReadyRef.current?.();
      requestAnimationFrame(() => fitToContainer());
    }, [fitToContainer]);

    const reportError = useCallback((message: string) => {
      setErrorText(message);
      setDataLoading(false);
      onErrorRef.current?.(message);
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

    // Initialise DWV once into the React-rendered layer host.
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
            Draw: { options: ["Ruler", "Rectangle", "Ellipse", "Arrow"] },
          };

          const app = new dwv.App();

          const onDwvLoad = () => {
            if (cancelled) return;
            applyTool(app, activeToolRef.current);
            if (windowPresetRef.current) {
              try {
                app.setWindowLevelPreset(windowPresetRef.current);
              } catch {
                /* ignore */
              }
            }
            finishLoading();
          };

          app.addEventListener("load", onDwvLoad);
          app.addEventListener("loadend", () => {
            if (cancelled) return;
            const ids = app.getDataIds();
            if (ids.length > 0) {
              onLabelRef.current?.(`Loaded · ${ids.length} dataset(s)`);
            }
            finishLoading();
          });
          app.addEventListener("error", (event: unknown) => {
            if (cancelled) return;
            const msg =
              event && typeof event === "object" && "message" in event
                ? String((event as { message?: string }).message)
                : "DWV could not display this DICOM.";
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

          appRef.current = app;
          setEngineReady(true);
        } catch (e) {
          initStartedRef.current = false;
          if (!cancelled) {
            reportError(e instanceof Error ? e.message : "Failed to initialise DICOM viewer.");
          }
        }
      })();

      return () => {
        cancelled = true;
        teardownApp();
      };
    }, [layerId, applyTool, finishLoading, reportError, teardownApp]);

    const fileKey = fileSignature(files);
    const sourceKey = fileKey || pacsInstanceId || "";

    // Load DICOM when engine is ready and source changes.
    useEffect(() => {
      const app = appRef.current;
      if (!engineReady || !app || !sourceKey) return;

      const gen = ++loadGenRef.current;
      loadFinishedRef.current = false;
      setDataLoading(true);
      setErrorText(null);

      void (async () => {
        try {
          if (files?.length) {
            onLabelRef.current?.(`Local · ${files[0].name}`);
            app.loadFiles(files);
            return;
          }

          if (pacsInstanceId) {
            const blob = await apiClient.getBlob(`/internal/v1/pacs/instances/${pacsInstanceId}/file`);
            if (gen !== loadGenRef.current) return;
            if (!blob.size) {
              reportError("Empty DICOM response from PACS.");
              return;
            }
            const file = new File([blob], `instance-${pacsInstanceId}.dcm`, { type: "application/dicom" });
            onLabelRef.current?.(`PACS · ${pacsInstanceId.slice(0, 8)}…`);
            app.loadFiles([file]);
          }
        } catch (e) {
          if (gen !== loadGenRef.current) return;
          reportError(e instanceof Error ? e.message : "Failed to load DICOM.");
        }
      })();
    }, [engineReady, sourceKey, fileKey, files, pacsInstanceId, reportError]);

    useEffect(() => {
      if (!dataLoading) return;
      const t = window.setTimeout(() => finishLoading(), LOAD_TIMEOUT_MS);
      return () => window.clearTimeout(t);
    }, [dataLoading, finishLoading]);

    useEffect(() => {
      const app = appRef.current;
      if (!app || !engineReady) return;
      applyTool(app, activeTool);
    }, [activeTool, drawShape, engineReady, applyTool]);

    useEffect(() => {
      const app = appRef.current;
      if (!app || !engineReady || !windowPreset) return;
      try {
        app.setWindowLevelPreset(windowPreset);
      } catch {
        /* ignore */
      }
    }, [windowPreset, engineReady]);

    useEffect(() => {
      const shell = shellRef.current;
      if (!shell || !engineReady) return;

      const onResize = () => requestAnimationFrame(() => fitToContainer());
      const ro = new ResizeObserver(onResize);
      ro.observe(shell);
      window.addEventListener("resize", onResize);
      document.addEventListener("fullscreenchange", onResize);

      return () => {
        ro.disconnect();
        window.removeEventListener("resize", onResize);
        document.removeEventListener("fullscreenchange", onResize);
      };
    }, [fitToContainer, engineReady]);

    const showSpinner = !engineReady || dataLoading;

    return (
      <div className={`impilo-dwv-root ${className}`} ref={shellRef}>
        <div id={layerId} className="dwv-layer-host" />
        {showSpinner && !errorText && (
          <div className="pointer-events-none absolute inset-0 z-10 flex flex-col items-center justify-center bg-black/70">
            <Loader2 className="h-8 w-8 animate-spin text-impilo-400" />
            <p className="mt-2 text-sm text-gray-400">
              {!engineReady ? "Starting viewer…" : "Loading DICOM…"}
            </p>
          </div>
        )}
        {errorText && (
          <div className="absolute inset-0 z-20 flex flex-col items-center justify-center gap-2 bg-black p-6 text-center">
            <AlertCircle className="h-10 w-10 text-amber-400" />
            <p className="max-w-md text-sm text-amber-100">{errorText}</p>
          </div>
        )}
      </div>
    );
  },
);

DwvClinicalViewer.displayName = "DwvClinicalViewer";
