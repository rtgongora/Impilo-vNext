"use client";

/**
 * Imaging / PACS Viewer — DICOM study browser and viewer.
 * Route: /ehr/[patientId]/imaging
 *
 * Features:
 * - Study list from Orthanc PACS via BFF proxy
 * - Series browser with thumbnails
 * - Instance viewer with windowing controls
 * - DICOMweb metadata display
 * - Fullscreen mode
 */

import { useState, useRef, useEffect, useCallback } from "react";
import { useParams } from "next/navigation";
import {
  Image,
  Monitor,
  Maximize2,
  Minimize2,
  ZoomIn,
  ZoomOut,
  RotateCw,
  Contrast,
  Loader2,
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  Grid3X3,
  List,
  Calendar,
  Activity,
} from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api-client";

// ── Types ─────────────────────────────────────────────────────────

interface OrthancStudy {
  ID: string;
  MainDicomTags?: {
    StudyDate?: string;
    StudyDescription?: string;
    StudyInstanceUID?: string;
    AccessionNumber?: string;
    ReferringPhysicianName?: string;
  };
  PatientMainDicomTags?: {
    PatientID?: string;
    PatientName?: string;
  };
  Series?: string[];
}

interface OrthancSeries {
  ID: string;
  MainDicomTags?: {
    SeriesDescription?: string;
    Modality?: string;
    SeriesNumber?: string;
    SeriesInstanceUID?: string;
  };
  Instances?: string[];
}

// ── API Hooks ─────────────────────────────────────────────────────

function useStudies() {
  return useQuery({
    queryKey: ["pacs", "studies"],
    queryFn: async () => {
      const studyIds = await apiFetch<string[]>("/internal/v1/pacs/studies");
      // Fetch details for each study (limit to 20 most recent)
      const details = await Promise.all(
        studyIds.slice(0, 20).map(async (id: string) => {
          try {
            return await apiFetch<OrthancStudy>(`/internal/v1/pacs/studies/${id}`);
          } catch {
            return null;
          }
        })
      );
      return details.filter(Boolean) as OrthancStudy[];
    },
    staleTime: 30_000,
  });
}

function useStudySeries(studyId: string | null) {
  return useQuery({
    queryKey: ["pacs", "series", studyId],
    queryFn: async () => {
      if (!studyId) return [];
      const seriesIds = await apiFetch<string[]>(
        `/internal/v1/pacs/studies/${studyId}/series`
      );
      const details = await Promise.all(
        seriesIds.map(async (id: string) => {
          try {
            const series = await apiFetch<OrthancSeries>(
              `/internal/v1/pacs/studies/${studyId}/series`
            );
            return { ...series, ID: id };
          } catch {
            return { ID: id, Instances: [] };
          }
        })
      );
      return details as OrthancSeries[];
    },
    enabled: !!studyId,
    staleTime: 30_000,
  });
}

// ── Page Component ────────────────────────────────────────────────

export default function ImagingPage() {
  const params = useParams();
  const patientId = params.patientId as string;
  const { data: studies, isLoading, error } = useStudies();

  const [selectedStudy, setSelectedStudy] = useState<OrthancStudy | null>(null);
  const [selectedInstance, setSelectedInstance] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<"grid" | "list">("grid");
  const [isFullscreen, setIsFullscreen] = useState(false);

  // Viewer controls
  const [brightness, setBrightness] = useState(100);
  const [contrast, setContrast] = useState(100);
  const [zoom, setZoom] = useState(100);
  const [rotation, setRotation] = useState(0);

  const viewerRef = useRef<HTMLDivElement>(null);

  const resetViewerControls = useCallback(() => {
    setBrightness(100);
    setContrast(100);
    setZoom(100);
    setRotation(0);
  }, []);

  const toggleFullscreen = useCallback(() => {
    if (!viewerRef.current) return;
    if (!isFullscreen) {
      viewerRef.current.requestFullscreen?.();
    } else {
      document.exitFullscreen?.();
    }
    setIsFullscreen(!isFullscreen);
  }, [isFullscreen]);

  useEffect(() => {
    const handler = () => setIsFullscreen(!!document.fullscreenElement);
    document.addEventListener("fullscreenchange", handler);
    return () => document.removeEventListener("fullscreenchange", handler);
  }, []);

  return (
    <EHRLayout patientId={patientId}>
      <PageShell title="Imaging / PACS" icon={<Image className="w-5 h-5" />}>
        <div className="flex h-[calc(100vh-180px)] gap-4">

          {/* ── Left Panel: Study List ───────────────────────────── */}
          <div className="w-80 flex-shrink-0 border rounded-lg bg-white overflow-hidden flex flex-col">
            <div className="p-3 border-b bg-gray-50 flex items-center justify-between">
              <h3 className="text-sm font-semibold text-gray-700">Studies</h3>
              <div className="flex gap-1">
                <button
                  onClick={() => setViewMode("grid")}
                  className={`p-1 rounded ${viewMode === "grid" ? "bg-blue-100 text-blue-600" : "text-gray-400"}`}
                  aria-label="Grid view"
                >
                  <Grid3X3 className="w-4 h-4" />
                </button>
                <button
                  onClick={() => setViewMode("list")}
                  className={`p-1 rounded ${viewMode === "list" ? "bg-blue-100 text-blue-600" : "text-gray-400"}`}
                  aria-label="List view"
                >
                  <List className="w-4 h-4" />
                </button>
              </div>
            </div>

            <div className="flex-1 overflow-y-auto p-2">
              {isLoading && (
                <div className="flex items-center justify-center py-12">
                  <Loader2 className="w-6 h-6 animate-spin text-blue-500" />
                  <span className="ml-2 text-sm text-gray-500">Loading studies...</span>
                </div>
              )}

              {error && (
                <div className="flex flex-col items-center py-8 text-center">
                  <AlertCircle className="w-8 h-8 text-amber-500 mb-2" />
                  <p className="text-sm text-gray-600">PACS server unavailable</p>
                  <p className="text-xs text-gray-400 mt-1">Orthanc may not be running</p>
                </div>
              )}

              {!isLoading && !error && studies?.length === 0 && (
                <div className="flex flex-col items-center py-8 text-center">
                  <Image className="w-8 h-8 text-gray-300 mb-2" />
                  <p className="text-sm text-gray-500">No imaging studies found</p>
                </div>
              )}

              {studies?.map((study) => (
                <button
                  key={study.ID}
                  onClick={() => {
                    setSelectedStudy(study);
                    setSelectedInstance(null);
                    resetViewerControls();
                  }}
                  className={`w-full text-left p-3 rounded-lg mb-2 border transition-colors ${
                    selectedStudy?.ID === study.ID
                      ? "border-blue-500 bg-blue-50"
                      : "border-gray-200 hover:border-gray-300 hover:bg-gray-50"
                  }`}
                >
                  <div className="flex items-start gap-2">
                    <Monitor className="w-4 h-4 mt-0.5 text-purple-500 flex-shrink-0" />
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-gray-800 truncate">
                        {study.MainDicomTags?.StudyDescription || "Unnamed Study"}
                      </p>
                      <div className="flex items-center gap-2 mt-1">
                        <Calendar className="w-3 h-3 text-gray-400" />
                        <span className="text-xs text-gray-500">
                          {formatDicomDate(study.MainDicomTags?.StudyDate)}
                        </span>
                      </div>
                      {study.MainDicomTags?.AccessionNumber && (
                        <p className="text-xs text-gray-400 mt-0.5">
                          Accession: {study.MainDicomTags.AccessionNumber}
                        </p>
                      )}
                      {study.Series && (
                        <p className="text-xs text-gray-400 mt-0.5">
                          {study.Series.length} series
                        </p>
                      )}
                    </div>
                  </div>
                </button>
              ))}
            </div>
          </div>

          {/* ── Center: Viewer ───────────────────────────────────── */}
          <div
            ref={viewerRef}
            className="flex-1 border rounded-lg bg-black overflow-hidden flex flex-col"
          >
            {/* Viewer toolbar */}
            <div className="flex items-center justify-between px-3 py-2 bg-gray-900 border-b border-gray-700">
              <div className="flex items-center gap-1">
                <ToolButton
                  icon={<ZoomIn className="w-4 h-4" />}
                  label="Zoom In"
                  onClick={() => setZoom((z) => Math.min(z + 25, 400))}
                />
                <ToolButton
                  icon={<ZoomOut className="w-4 h-4" />}
                  label="Zoom Out"
                  onClick={() => setZoom((z) => Math.max(z - 25, 25))}
                />
                <ToolButton
                  icon={<RotateCw className="w-4 h-4" />}
                  label="Rotate"
                  onClick={() => setRotation((r) => (r + 90) % 360)}
                />
                <ToolButton
                  icon={<Contrast className="w-4 h-4" />}
                  label="Reset W/L"
                  onClick={resetViewerControls}
                />
                <div className="w-px h-5 bg-gray-700 mx-1" />
                <span className="text-xs text-gray-400">{zoom}%</span>
              </div>

              <div className="flex items-center gap-2">
                <div className="flex items-center gap-1">
                  <label className="text-xs text-gray-400">B:</label>
                  <input
                    type="range"
                    min="0"
                    max="200"
                    value={brightness}
                    onChange={(e) => setBrightness(Number(e.target.value))}
                    className="w-16 h-1"
                    aria-label="Brightness"
                  />
                </div>
                <div className="flex items-center gap-1">
                  <label className="text-xs text-gray-400">C:</label>
                  <input
                    type="range"
                    min="0"
                    max="200"
                    value={contrast}
                    onChange={(e) => setContrast(Number(e.target.value))}
                    className="w-16 h-1"
                    aria-label="Contrast"
                  />
                </div>
                <ToolButton
                  icon={isFullscreen ? <Minimize2 className="w-4 h-4" /> : <Maximize2 className="w-4 h-4" />}
                  label={isFullscreen ? "Exit Fullscreen" : "Fullscreen"}
                  onClick={toggleFullscreen}
                />
              </div>
            </div>

            {/* Viewport */}
            <div className="flex-1 flex items-center justify-center overflow-hidden">
              {!selectedStudy && (
                <div className="text-center">
                  <Monitor className="w-16 h-16 text-gray-600 mx-auto mb-3" />
                  <p className="text-gray-400 text-sm">Select a study to view</p>
                  <p className="text-gray-600 text-xs mt-1">
                    Studies are loaded from Orthanc PACS
                  </p>
                </div>
              )}

              {selectedStudy && !selectedInstance && (
                <SeriesBrowser
                  studyId={selectedStudy.ID}
                  onSelectInstance={(instanceId) => setSelectedInstance(instanceId)}
                />
              )}

              {selectedStudy && selectedInstance && (
                <div
                  className="w-full h-full flex items-center justify-center"
                  style={{
                    filter: `brightness(${brightness}%) contrast(${contrast}%)`,
                    transform: `scale(${zoom / 100}) rotate(${rotation}deg)`,
                    transition: "transform 0.2s ease",
                  }}
                >
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={`${process.env.NEXT_PUBLIC_BFF_URL || "http://localhost:8160"}/internal/v1/pacs/instances/${selectedInstance}/preview`}
                    alt="DICOM instance"
                    className="max-w-full max-h-full object-contain"
                    onError={(e) => {
                      (e.target as HTMLImageElement).style.display = "none";
                    }}
                  />
                </div>
              )}
            </div>

            {/* Instance navigation */}
            {selectedStudy && selectedInstance && (
              <div className="flex items-center justify-center gap-2 py-2 bg-gray-900 border-t border-gray-700">
                <button
                  onClick={() => setSelectedInstance(null)}
                  className="text-xs text-blue-400 hover:text-blue-300 mr-4"
                >
                  Back to series
                </button>
              </div>
            )}
          </div>

          {/* ── Right Panel: Study Info ──────────────────────────── */}
          {selectedStudy && (
            <div className="w-64 flex-shrink-0 border rounded-lg bg-white overflow-y-auto">
              <div className="p-3 border-b bg-gray-50">
                <h3 className="text-sm font-semibold text-gray-700">Study Details</h3>
              </div>
              <div className="p-3 space-y-3">
                <InfoRow label="Description" value={selectedStudy.MainDicomTags?.StudyDescription} />
                <InfoRow label="Date" value={formatDicomDate(selectedStudy.MainDicomTags?.StudyDate)} />
                <InfoRow label="Accession" value={selectedStudy.MainDicomTags?.AccessionNumber} />
                <InfoRow label="Study UID" value={selectedStudy.MainDicomTags?.StudyInstanceUID} mono />
                <InfoRow label="Referring" value={selectedStudy.MainDicomTags?.ReferringPhysicianName} />
                <InfoRow label="Patient ID" value={selectedStudy.PatientMainDicomTags?.PatientID} />
                <InfoRow label="Series Count" value={String(selectedStudy.Series?.length || 0)} />
                <InfoRow label="Orthanc ID" value={selectedStudy.ID} mono />
              </div>
            </div>
          )}
        </div>
      </PageShell>
    </EHRLayout>
  );
}

// ── Sub-Components ──────────────────────────────────────────────────

function SeriesBrowser({
  studyId,
  onSelectInstance,
}: {
  studyId: string;
  onSelectInstance: (id: string) => void;
}) {
  const { data: series, isLoading } = useStudySeries(studyId);

  if (isLoading) {
    return (
      <div className="text-center">
        <Loader2 className="w-8 h-8 animate-spin text-blue-400 mx-auto mb-2" />
        <p className="text-gray-400 text-sm">Loading series...</p>
      </div>
    );
  }

  if (!series || series.length === 0) {
    return (
      <div className="text-center">
        <Grid3X3 className="w-10 h-10 text-gray-600 mx-auto mb-2" />
        <p className="text-gray-400 text-sm">No series in this study</p>
      </div>
    );
  }

  return (
    <div className="p-4 w-full overflow-y-auto">
      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        {series.map((s) => (
          <button
            key={s.ID}
            onClick={() => {
              if (s.Instances && s.Instances.length > 0) {
                onSelectInstance(s.Instances[0]);
              }
            }}
            className="bg-gray-800 rounded-lg p-3 border border-gray-700 hover:border-blue-500 transition-colors text-left"
          >
            <div className="aspect-square bg-gray-900 rounded mb-2 flex items-center justify-center">
              <Activity className="w-8 h-8 text-gray-600" />
            </div>
            <p className="text-xs text-white truncate">
              {s.MainDicomTags?.SeriesDescription || `Series ${s.MainDicomTags?.SeriesNumber || s.ID.slice(0, 8)}`}
            </p>
            <p className="text-xs text-gray-400">
              {s.MainDicomTags?.Modality || "Unknown"} · {s.Instances?.length || 0} images
            </p>
          </button>
        ))}
      </div>
    </div>
  );
}

function ToolButton({
  icon,
  label,
  onClick,
}: {
  icon: React.ReactNode;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className="p-1.5 rounded hover:bg-gray-700 text-gray-300 hover:text-white transition-colors"
      title={label}
      aria-label={label}
    >
      {icon}
    </button>
  );
}

function InfoRow({
  label,
  value,
  mono,
}: {
  label: string;
  value?: string;
  mono?: boolean;
}) {
  if (!value) return null;
  return (
    <div>
      <dt className="text-xs font-medium text-gray-500">{label}</dt>
      <dd className={`text-sm text-gray-800 mt-0.5 ${mono ? "font-mono text-xs break-all" : ""}`}>
        {value}
      </dd>
    </div>
  );
}

// ── Helpers ──────────────────────────────────────────────────────────

function formatDicomDate(date?: string): string {
  if (!date) return "Unknown";
  // DICOM dates are YYYYMMDD
  if (date.length === 8) {
    return `${date.slice(0, 4)}-${date.slice(4, 6)}-${date.slice(6, 8)}`;
  }
  return date;
}
