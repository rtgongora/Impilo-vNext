"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  Contrast,
  Image as ImageIcon,
  Loader2,
  Monitor,
  Move,
  SunMedium,
} from "lucide-react";
import { PageShell } from "@/components/PageShell";
import { useImagingViewerLaunchContext, useLaunchImagingViewer } from "@/hooks/queries/useImaging";
import { apiClient } from "@/lib/api-client";
import { applyWindowLevelToRgba, decodeNativeGrayscaleForWl } from "@/lib/dicom/nativeWindowLevel";

interface DicomwebTagBlock {
  Value?: unknown[];
}

function firstUiValue(row: Record<string, unknown>, tag: string): string | undefined {
  const block = row[tag] as DicomwebTagBlock | undefined;
  const v = block?.Value;
  if (!Array.isArray(v) || v.length === 0) return undefined;
  return String(v[0]);
}

/** Maps window center / width to CSS filter for already-rendered 8-bit frames (approximate). */
function approximateDisplayWl(windowCenter: number, windowWidth: number): { brightness: number; contrast: number } {
  const ww = Math.max(windowWidth, 8);
  const brightness = 100 + ((windowCenter - 128) / 128) * 45;
  const contrast = Math.min(240, Math.max(20, (256 / ww) * 100));
  return { brightness, contrast };
}

export default function DicomViewerPage() {
  const params = useParams<{ patientId: string }>();
  const search = useSearchParams();
  const patientId = params.patientId;
  const studyUid = search.get("studyUid") ?? "";
  const initialSeriesUid = search.get("seriesUid");
  const governedStudyId = search.get("governedStudyId");
  const requestedViewerType = search.get("viewerType");
  const ohifBaseUrl =
    process.env.NEXT_PUBLIC_OHIF_BASE_URL ??
    (process.env.NODE_ENV === "development" ? "http://localhost:3005" : "");

  const launchViewer = useLaunchImagingViewer(patientId);
  const launchContextQ = useImagingViewerLaunchContext(governedStudyId, {
    chartPatientCpid: patientId,
    viewerType: requestedViewerType,
  });
  const sessionRecorded = useRef(false);
  const resolvedViewerEngine = String(
    (launchContextQ.data?.data as { viewerEngine?: string } | undefined)?.viewerEngine ??
      requestedViewerType ??
      "DICOMWEB_STACK",
  )
    .trim()
    .toUpperCase();
  const useOhif = resolvedViewerEngine === "OHIF";
  const ohifUrl = useMemo(() => {
    if (!useOhif || !ohifBaseUrl || !studyUid) return null;
    const normalizedBase = ohifBaseUrl.replace(/\/+$/, "");
    const url = new URL(`${normalizedBase}/viewer`);
    url.searchParams.set("StudyInstanceUIDs", studyUid);
    return url.toString();
  }, [useOhif, ohifBaseUrl, studyUid]);

  useEffect(() => {
    if (!governedStudyId || sessionRecorded.current) return;
    sessionRecorded.current = true;
    void launchViewer.mutateAsync({
      studyId: governedStudyId,
      body: {
        viewerType: requestedViewerType ?? "DICOMWEB_STACK",
        contextRef: `/ehr/${patientId}/imaging/viewer`,
        scope: "FULL_DICOM",
      },
    });
  }, [governedStudyId, patientId, launchViewer, requestedViewerType]);

  const { data: seriesJson, isLoading: loadingSeries, error: seriesError } = useQuery({
    queryKey: ["dicomweb-series", studyUid],
    queryFn: () =>
      apiClient.get<Record<string, unknown>[]>(
        `/internal/v1/pacs/dicomweb/studies/${encodeURIComponent(studyUid)}/series`,
      ),
    enabled: Boolean(studyUid),
    staleTime: 15_000,
  });

  const seriesList = useMemo(() => {
    const rows = Array.isArray(seriesJson) ? seriesJson : [];
    return rows
      .map((row) => {
        const seriesUid = firstUiValue(row, "0020000E");
        if (!seriesUid) return null;
        return {
          seriesUid,
          modality: firstUiValue(row, "00080060"),
          description: firstUiValue(row, "0008103E"),
        };
      })
      .filter((s): s is NonNullable<typeof s> => s != null);
  }, [seriesJson]);

  const [activeSeriesUid, setActiveSeriesUid] = useState<string | null>(null);

  useEffect(() => {
    if (activeSeriesUid) return;
    if (!seriesList.length) return;
    const first = seriesList[0];
    const pick =
      initialSeriesUid && seriesList.some((s) => s.seriesUid === initialSeriesUid)
        ? initialSeriesUid
        : first.seriesUid;
    setActiveSeriesUid(pick ?? null);
  }, [activeSeriesUid, seriesList, initialSeriesUid]);

  const { data: instancesJson, isLoading: loadingInstances } = useQuery({
    queryKey: ["dicomweb-instances", studyUid, activeSeriesUid],
    queryFn: () =>
      apiClient.get<Record<string, unknown>[]>(
        `/internal/v1/pacs/dicomweb/studies/${encodeURIComponent(studyUid)}/series/${encodeURIComponent(
          activeSeriesUid!,
        )}/instances`,
      ),
    enabled: Boolean(studyUid && activeSeriesUid),
    staleTime: 15_000,
  });

  const instances = useMemo(() => {
    const rows = Array.isArray(instancesJson) ? instancesJson : [];
    return rows
      .map((row) => {
        const rawNf = firstUiValue(row, "00280008");
        const parsed = rawNf != null ? Number(rawNf) : 1;
        const numberOfFrames = Number.isFinite(parsed) && parsed >= 1 ? Math.min(10_000, Math.floor(parsed)) : 1;
        return {
          sop: firstUiValue(row, "00080018"),
          instNum: Number(firstUiValue(row, "00200013") ?? 0),
          numberOfFrames,
        };
      })
      .filter((i) => i.sop)
      .sort((a, b) => a.instNum - b.instNum);
  }, [instancesJson]);

  const [instanceIx, setInstanceIx] = useState(0);
  const [subFrameIx, setSubFrameIx] = useState(0);

  useEffect(() => {
    setInstanceIx(0);
    setSubFrameIx(0);
  }, [activeSeriesUid, instances.length]);

  const active = instances[instanceIx];
  const frameCount = active?.numberOfFrames ?? 1;
  const dicomFrameNumber = Math.min(subFrameIx + 1, frameCount);

  useEffect(() => {
    setSubFrameIx((ix) => Math.min(ix, Math.max(0, frameCount - 1)));
  }, [frameCount, active?.sop]);

  const [viewMode, setViewMode] = useState<"rendered" | "native">("rendered");
  const [windowCenter, setWindowCenter] = useState(128);
  const [windowWidth, setWindowWidth] = useState(256);
  const [zoom, setZoom] = useState(100);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const panDrag = useRef<{
    active: boolean;
    startX: number;
    startY: number;
    originX: number;
    originY: number;
  } | null>(null);

  const wlFilter = useMemo(
    () => approximateDisplayWl(windowCenter, windowWidth),
    [windowCenter, windowWidth],
  );

  const [imgUrl, setImgUrl] = useState<string | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [nativeLayer, setNativeLayer] = useState<{
    hu: Float32Array;
    w: number;
    h: number;
  } | null>(null);
  const [nativeHint, setNativeHint] = useState<string | null>(null);

  useEffect(() => {
    const sop = active?.sop;
    if (viewMode !== "rendered" || !studyUid || !activeSeriesUid || !sop) {
      setImgUrl(null);
      return;
    }
    let cancelled = false;
    let objectUrl: string | null = null;
    (async () => {
      try {
        const blob = await apiClient.getBlob(
          `/internal/v1/pacs/dicomweb/studies/${encodeURIComponent(studyUid)}/series/${encodeURIComponent(
            activeSeriesUid,
          )}/instances/${encodeURIComponent(sop)}/frames/${dicomFrameNumber}/rendered`,
        );
        objectUrl = URL.createObjectURL(blob);
        if (!cancelled) {
          setImgUrl(objectUrl);
        }
      } catch {
        if (!cancelled) setImgUrl(null);
      }
    })();
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [studyUid, activeSeriesUid, active?.sop, dicomFrameNumber, viewMode]);

  useEffect(() => {
    setNativeLayer(null);
    setNativeHint(null);
    const sop = active?.sop;
    if (viewMode !== "native" || !studyUid || !activeSeriesUid || !sop) {
      return;
    }
    let cancelled = false;
    void (async () => {
      try {
        const blob = await apiClient.getBlob(
          `/internal/v1/pacs/dicomweb/studies/${encodeURIComponent(studyUid)}/series/${encodeURIComponent(
            activeSeriesUid,
          )}/instances/${encodeURIComponent(sop)}`,
        );
        const ab = await blob.arrayBuffer();
        if (cancelled) return;
        const dec = decodeNativeGrayscaleForWl(ab);
        if (!dec.ok) {
          setNativeHint(dec.reason);
          setNativeLayer(null);
          return;
        }
        setWindowCenter(Math.round(dec.defaultCenter));
        setWindowWidth(Math.round(dec.defaultWidth));
        setNativeLayer({ hu: dec.hu, w: dec.width, h: dec.height });
        setNativeHint(
          frameCount > 1
            ? "Native WL uses the first frame in Pixel Data only; switch to Rendered for full multi-frame cine."
            : null,
        );
      } catch {
        if (!cancelled) {
          setNativeHint("Could not load DICOM instance for native decode.");
          setNativeLayer(null);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [viewMode, studyUid, activeSeriesUid, active?.sop, frameCount]);

  useEffect(() => {
    if (!nativeLayer || viewMode !== "native") return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    canvas.width = nativeLayer.w;
    canvas.height = nativeLayer.h;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    const imgData = ctx.createImageData(nativeLayer.w, nativeLayer.h);
    applyWindowLevelToRgba(
      nativeLayer.hu,
      nativeLayer.w,
      nativeLayer.h,
      windowCenter,
      windowWidth,
      imgData.data,
    );
    ctx.putImageData(imgData, 0, 0);
  }, [nativeLayer, viewMode, windowCenter, windowWidth]);

  const nextInstance = useCallback(() => {
    setInstanceIx((i) => Math.min(i + 1, Math.max(0, instances.length - 1)));
    setSubFrameIx(0);
    setPan({ x: 0, y: 0 });
  }, [instances.length]);

  const prevInstance = useCallback(() => {
    setInstanceIx((i) => Math.max(i - 1, 0));
    setSubFrameIx(0);
    setPan({ x: 0, y: 0 });
  }, []);

  const nextFrame = useCallback(() => {
    setSubFrameIx((ix) => {
      if (ix + 1 < frameCount) return ix + 1;
      return ix;
    });
  }, [frameCount]);

  const prevFrame = useCallback(() => {
    setSubFrameIx((ix) => Math.max(ix - 1, 0));
  }, []);

  const onPanPointerDown = useCallback(
    (e: React.PointerEvent<HTMLDivElement>) => {
      if (e.button !== 0) return;
      e.currentTarget.setPointerCapture(e.pointerId);
      panDrag.current = {
        active: true,
        startX: e.clientX,
        startY: e.clientY,
        originX: pan.x,
        originY: pan.y,
      };
    },
    [pan.x, pan.y],
  );

  const onPanPointerMove = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    const d = panDrag.current;
    if (!d?.active) return;
    setPan({
      x: d.originX + (e.clientX - d.startX),
      y: d.originY + (e.clientY - d.startY),
    });
  }, []);

  const onPanPointerUp = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    panDrag.current = null;
    try {
      e.currentTarget.releasePointerCapture(e.pointerId);
    } catch {
      /* ignore */
    }
  }, []);

  const resetView = useCallback(() => {
    setPan({ x: 0, y: 0 });
    setZoom(100);
    if (viewMode === "rendered") {
      setWindowCenter(128);
      setWindowWidth(256);
    } else if (nativeLayer) {
      /* defaults re-applied on next decode; approximate reset */
      setWindowCenter(40);
      setWindowWidth(400);
    }
  }, [viewMode, nativeLayer]);

  return (
    <PageShell
        title="DICOM viewer"
        subtitle="Study / series / instance navigation over DICOMweb (WADO-RS rendered frames)"
      >
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <Link
            href={`/ehr/${encodeURIComponent(patientId)}/imaging`}
            className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-50"
          >
            <ArrowLeft className="h-4 w-4" />
            Back to imaging workspace
          </Link>
          <span className="text-xs text-slate-500">
            Chart context: <span className="font-mono text-slate-700">{patientId}</span>
          </span>
        </div>
        {governedStudyId && (
          <div className="mb-4 rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs text-slate-700">
            Viewer engine{" "}
            <span className="font-semibold">
              {resolvedViewerEngine}
            </span>
            {" · "}
            PACS backend{" "}
            <span className="font-semibold">
              {String(
                (launchContextQ.data?.data as { backend?: { provider?: string } } | undefined)?.backend?.provider ??
                  "ORTHANC",
              )}
            </span>
            <span className="mx-2 text-slate-300">|</span>
            <Link
              href={`/ehr/${encodeURIComponent(patientId)}/imaging/viewer?studyUid=${encodeURIComponent(
                studyUid,
              )}${governedStudyId ? `&governedStudyId=${encodeURIComponent(governedStudyId)}` : ""}&viewerType=DICOMWEB_STACK`}
              className={`mr-2 rounded px-2 py-1 ${resolvedViewerEngine === "DICOMWEB_STACK" ? "bg-slate-100 font-semibold" : "hover:bg-slate-50"}`}
            >
              DICOMWEB_STACK
            </Link>
            <Link
              href={`/ehr/${encodeURIComponent(patientId)}/imaging/viewer?studyUid=${encodeURIComponent(
                studyUid,
              )}${governedStudyId ? `&governedStudyId=${encodeURIComponent(governedStudyId)}` : ""}&viewerType=OHIF`}
              className={`rounded px-2 py-1 ${resolvedViewerEngine === "OHIF" ? "bg-slate-100 font-semibold" : "hover:bg-slate-50"}`}
            >
              OHIF
            </Link>
          </div>
        )}

        {useOhif && !ohifUrl && (
          <div className="mb-4 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950">
            OHIF engine selected, but no OHIF host is configured. Set{" "}
            <code className="font-mono">NEXT_PUBLIC_OHIF_BASE_URL</code> (dev default is{" "}
            <code className="font-mono">http://localhost:3005</code>), or switch to{" "}
            <code className="font-mono">DICOMWEB_STACK</code>.
          </div>
        )}

        {useOhif && ohifUrl && (
          <div className="mb-4 rounded-2xl border border-slate-200 bg-white p-2 shadow-sm">
            <iframe
              title="OHIF viewer"
              src={ohifUrl}
              className="h-[78vh] w-full rounded-xl border border-slate-200"
              referrerPolicy="no-referrer"
            />
          </div>
        )}

        {!studyUid && (
          <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950">
            Missing <code className="font-mono">studyUid</code> query parameter. Open this viewer from the imaging
            workspace with a valid DICOM Study Instance UID.
          </div>
        )}

        {studyUid && loadingSeries && (
          <div className="flex items-center gap-2 text-sm text-slate-600">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading series (QIDO-RS)…
          </div>
        )}

        {studyUid && seriesError && (
          <div className="rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-900">
            Could not load DICOMweb series. Ensure Orthanc is reachable and the study UID exists.
          </div>
        )}

        {!useOhif && studyUid && !loadingSeries && !seriesError && (
          <div className="grid gap-4 xl:grid-cols-[240px_minmax(0,1fr)]">
            <div className="rounded-2xl border border-slate-200 bg-white p-3 shadow-sm">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Series</p>
              <div className="mt-2 space-y-1 max-h-[70vh] overflow-y-auto">
                {seriesList.map((s) => {
                  const selected = s.seriesUid === activeSeriesUid;
                  return (
                    <button
                      key={s.seriesUid}
                      type="button"
                      onClick={() => setActiveSeriesUid(s.seriesUid)}
                      className={`w-full rounded-xl border px-2 py-2 text-left text-xs ${
                        selected ? "border-impilo-400 bg-impilo-50" : "border-slate-100 hover:bg-slate-50"
                      }`}
                    >
                      <div className="flex items-center gap-2">
                        <Monitor className="h-3.5 w-3.5 text-impilo-500" />
                        <span className="font-mono text-[11px] break-all">{s.seriesUid}</span>
                      </div>
                      <div className="mt-1 text-slate-600">
                        {s.modality ?? "—"} {s.description ? `· ${s.description}` : ""}
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="space-y-3">
              <div className="rounded-2xl border border-slate-200 bg-slate-900 p-3 text-slate-100">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="text-xs font-mono break-all opacity-90">Study {studyUid}</div>
                  <div className="flex flex-wrap gap-1 rounded-lg border border-slate-600 p-0.5 text-[10px]">
                    <button
                      type="button"
                      onClick={() => {
                        setViewMode("rendered");
                        setNativeLayer(null);
                        setNativeHint(null);
                        setWindowCenter(128);
                        setWindowWidth(256);
                      }}
                      className={`rounded-md px-2 py-1 ${
                        viewMode === "rendered" ? "bg-slate-100 text-slate-900" : "text-slate-400 hover:text-slate-200"
                      }`}
                    >
                      Rendered
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setViewMode("native");
                        setImgUrl(null);
                      }}
                      className={`rounded-md px-2 py-1 ${
                        viewMode === "native" ? "bg-slate-100 text-slate-900" : "text-slate-400 hover:text-slate-200"
                      }`}
                    >
                      Native WL
                    </button>
                  </div>
                  <div className="flex flex-wrap items-center gap-1">
                    <span className="mr-1 text-[10px] uppercase text-slate-500">Instance</span>
                    <button
                      type="button"
                      onClick={prevInstance}
                      className="rounded-lg border border-slate-600 px-2 py-1 hover:bg-slate-800"
                      aria-label="Previous instance"
                    >
                      <ChevronLeft className="h-4 w-4" />
                    </button>
                    <button
                      type="button"
                      onClick={nextInstance}
                      className="rounded-lg border border-slate-600 px-2 py-1 hover:bg-slate-800"
                      aria-label="Next instance"
                    >
                      <ChevronRight className="h-4 w-4" />
                    </button>
                    {frameCount > 1 && (
                      <>
                        <span className="ml-2 mr-1 text-[10px] uppercase text-slate-500">Frame</span>
                        <button
                          type="button"
                          onClick={prevFrame}
                          className="rounded-lg border border-slate-600 px-2 py-1 hover:bg-slate-800"
                          aria-label="Previous frame"
                        >
                          <ChevronLeft className="h-4 w-4" />
                        </button>
                        <button
                          type="button"
                          onClick={nextFrame}
                          className="rounded-lg border border-slate-600 px-2 py-1 hover:bg-slate-800"
                          aria-label="Next frame"
                        >
                          <ChevronRight className="h-4 w-4" />
                        </button>
                      </>
                    )}
                  </div>
                </div>
                <div
                  role="presentation"
                  onPointerDown={onPanPointerDown}
                  onPointerMove={onPanPointerMove}
                  onPointerUp={onPanPointerUp}
                  onPointerCancel={onPanPointerUp}
                  onDoubleClick={resetView}
                  className="relative mt-2 flex min-h-[360px] cursor-grab touch-none items-center justify-center overflow-hidden rounded-xl bg-black active:cursor-grabbing"
                >
                  {loadingInstances && viewMode === "rendered" && (
                    <Loader2 className="h-8 w-8 animate-spin text-slate-300" />
                  )}
                  {viewMode === "rendered" && !loadingInstances && imgUrl && (
                    <div
                      className="flex max-h-[70vh] max-w-full items-center justify-center"
                      style={{
                        transform: `translate(${pan.x}px, ${pan.y}px)`,
                      }}
                    >
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img
                        src={imgUrl}
                        alt="DICOM rendered frame"
                        className="max-h-[70vh] max-w-full select-none object-contain"
                        draggable={false}
                        style={{
                          filter: `brightness(${wlFilter.brightness}%) contrast(${wlFilter.contrast}%)`,
                          transform: `scale(${zoom / 100})`,
                        }}
                      />
                    </div>
                  )}
                  {viewMode === "native" && (
                    <div
                      className="flex max-h-[70vh] max-w-full items-center justify-center"
                      style={{
                        transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom / 100})`,
                      }}
                    >
                      <canvas
                        ref={canvasRef}
                        className="max-h-[70vh] max-w-full select-none object-contain bg-black"
                        style={{ imageRendering: "pixelated" }}
                      />
                    </div>
                  )}
                  {viewMode === "rendered" && !loadingInstances && !imgUrl && (
                    <div className="flex flex-col items-center gap-2 p-6 text-center text-sm text-slate-400">
                      <ImageIcon className="h-10 w-10 opacity-50" />
                      No rendered frame for this instance (check WADO-RS / Orthanc).
                    </div>
                  )}
                  {viewMode === "native" && !nativeLayer && !nativeHint && (
                    <Loader2 className="h-8 w-8 animate-spin text-slate-300" />
                  )}
                  {viewMode === "native" && nativeHint && !nativeLayer && (
                    <div className="max-w-md p-4 text-center text-sm text-amber-200">{nativeHint}</div>
                  )}
                </div>
                <div className="mt-2 grid gap-2 text-xs text-slate-300 md:grid-cols-3">
                  <label className="flex flex-col gap-1">
                    <span className="flex items-center gap-1">
                      <SunMedium className="h-4 w-4" />
                      Window center {windowCenter}
                    </span>
                    <input
                      type="range"
                      min={viewMode === "native" ? -1024 : 1}
                      max={viewMode === "native" ? 3072 : 255}
                      value={windowCenter}
                      onChange={(e) => setWindowCenter(Number(e.target.value))}
                      className="w-full"
                    />
                  </label>
                  <label className="flex flex-col gap-1">
                    <span className="flex items-center gap-1">
                      <Contrast className="h-4 w-4" />
                      Window width {windowWidth}
                    </span>
                    <input
                      type="range"
                      min={viewMode === "native" ? 1 : 8}
                      max={viewMode === "native" ? 4096 : 512}
                      value={windowWidth}
                      onChange={(e) => setWindowWidth(Number(e.target.value))}
                      className="w-full"
                    />
                  </label>
                  <label className="flex flex-col gap-1">
                    Zoom {zoom}%
                    <input
                      type="range"
                      min={50}
                      max={200}
                      value={zoom}
                      onChange={(e) => setZoom(Number(e.target.value))}
                      className="w-full"
                    />
                  </label>
                </div>
                <p className="mt-1 flex items-center gap-1 text-[10px] text-slate-500">
                  <Move className="h-3 w-3" />
                  Drag the viewport to pan. Double-click resets pan, zoom, and window.
                </p>
                <div className="mt-2 text-[11px] font-mono text-slate-400 break-all">
                  Series {activeSeriesUid ?? "—"} · SOP {active?.sop ?? "—"} · Multi-frame{" "}
                  {subFrameIx + 1} / {frameCount} · Instance {instanceIx + 1} / {Math.max(1, instances.length)}
                </div>
                {nativeHint && (
                  <p className="mt-1 text-[10px] text-amber-200/90">{nativeHint}</p>
                )}
              </div>
            </div>
          </div>
        )}
      </PageShell>
  );
}
