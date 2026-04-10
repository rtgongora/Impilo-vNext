"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import {
  Activity,
  AlertCircle,
  Calendar,
  ClipboardList,
  Contrast,
  FileText,
  Grid3X3,
  Image,
  Loader2,
  Maximize2,
  Minimize2,
  Monitor,
  RotateCw,
  Search,
  ZoomIn,
  ZoomOut,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient } from "@/lib/api-client";

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
  label: string;
  modality: string;
  instances: string[];
}

function normalizePatientIdentifier(value?: string) {
  return value?.toLowerCase().replace(/[^a-z0-9]/g, "") ?? "";
}

function matchesPatient(study: OrthancStudy, patientId: string) {
  return (
    normalizePatientIdentifier(study.PatientMainDicomTags?.PatientID) ===
    normalizePatientIdentifier(patientId)
  );
}

function formatDicomDate(date?: string): string {
  if (!date) return "Unknown";
  if (date.length === 8) {
    return `${date.slice(0, 4)}-${date.slice(4, 6)}-${date.slice(6, 8)}`;
  }
  return date;
}

function useStudies(chartPatientId: string | undefined) {
  return useQuery({
    queryKey: ["pacs", "studies", chartPatientId ?? "unknown"],
    queryFn: async () => {
      const studyIds = await apiClient.get<string[]>("/internal/v1/pacs/studies");
      const details = await Promise.all(
        studyIds.slice(0, 20).map(async (id) => {
          try {
            return await apiClient.get<OrthancStudy>(`/internal/v1/pacs/studies/${id}`);
          } catch {
            return null;
          }
        })
      );
      return details.filter(Boolean) as OrthancStudy[];
    },
    staleTime: 30_000,
    enabled: Boolean(chartPatientId),
  });
}

function useStudySeries(studyId: string | null, seriesIds: string[]) {
  return useQuery({
    queryKey: ["pacs", "series", studyId, ...seriesIds],
    queryFn: async () => {
      if (!studyId || seriesIds.length === 0) return [];

      const series = await Promise.all(
        seriesIds.map(async (seriesId, index) => {
          try {
            const instances = await apiClient.get<string[]>(`/internal/v1/pacs/series/${seriesId}/instances`);
            return {
              ID: seriesId,
              label: `Series ${index + 1}`,
              modality: "DICOM",
              instances,
            } satisfies OrthancSeries;
          } catch {
            return {
              ID: seriesId,
              label: `Series ${index + 1}`,
              modality: "DICOM",
              instances: [],
            } satisfies OrthancSeries;
          }
        })
      );

      return series;
    },
    enabled: !!studyId,
    staleTime: 30_000,
  });
}

export default function ImagingPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const facility = useFacilityStore((state) => state.facility);
  const { data: encountersData } = useEncounters(patientId);
  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.status === "IN_PROGRESS" || encounter.attributes.status === "ACTIVE"
  );
  const { data: studies = [], isLoading, error } = useStudies(patientId);

  const patientMatchedStudies = studies.filter((study) => matchesPatient(study, patientId));
  /** Never list unrelated DICOM patients in this chart's rail — avoids wrong-patient review. */
  const visibleStudies = patientMatchedStudies;
  const selectedStudyDefault = visibleStudies[0] ?? null;
  const hasPacsStudies = studies.length > 0;
  const hasPatientMatch = patientMatchedStudies.length > 0;
  const pacsHasUnmatchedStudies = !isLoading && !error && hasPacsStudies && !hasPatientMatch;
  const [selectedStudy, setSelectedStudy] = useState<OrthancStudy | null>(selectedStudyDefault);
  const [selectedInstance, setSelectedInstance] = useState<string | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [brightness, setBrightness] = useState(100);
  const [contrast, setContrast] = useState(100);
  const [zoom, setZoom] = useState(100);
  const [rotation, setRotation] = useState(0);
  const viewerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!selectedStudyDefault) {
      setSelectedStudy(null);
      return;
    }

    setSelectedStudy((current) => {
      if (!current) return selectedStudyDefault;
      const stillVisible = visibleStudies.some((study) => study.ID === current.ID);
      return stillVisible ? current : selectedStudyDefault;
    });
  }, [selectedStudyDefault, visibleStudies]);

  const { data: series = [], isLoading: isLoadingSeries } = useStudySeries(
    selectedStudy?.ID ?? null,
    selectedStudy?.Series ?? []
  );

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
  }, [isFullscreen]);

  useEffect(() => {
    const handler = () => setIsFullscreen(Boolean(document.fullscreenElement));
    document.addEventListener("fullscreenchange", handler);
    return () => document.removeEventListener("fullscreenchange", handler);
  }, []);

  const contextMode = hasPatientMatch ? "patient" : pacsHasUnmatchedStudies ? "reconcile" : "empty";
  const selectedSeries = series.find((entry) => entry.instances.includes(selectedInstance ?? ""));

  return (
    <EHRLayout>
      <PageShell title="Imaging / PACS" subtitle="Diagnostic imaging review and viewer continuity">
        <div className="space-y-6">
          <ClinicalReviewHeader
            badge="Imaging continuity"
            badgeIcon={Image}
            title="Keep imaging review attached to patient context, ordering follow-up, and clinical communication rather than opening a detached PACS tool."
            description="The viewer now makes it clear whether it found patient-matched PACS studies, what the next imaging action is, and where to continue into orders, results, documents, or notes."
            facilityName={facility?.name}
            encounterLabel={
              activeEncounter
                ? `${activeEncounter.attributes.encounterType} since ${new Date(activeEncounter.attributes.startedAt).toLocaleString()}`
                : null
            }
            actions={[
              { href: `/ehr/${patientId}/orders`, label: "Orders", icon: ClipboardList },
              {
                href: `/ehr/${patientId}/results`,
                label: "Results",
                icon: Activity,
                tone: "secondary",
              },
              {
                href: `/ehr/${patientId}/documents`,
                label: "Documents",
                icon: FileText,
                tone: "secondary",
              },
              {
                href: `/ehr/${patientId}/notes`,
                label: "Notes",
                icon: FileText,
                tone: "secondary",
              },
            ]}
            metrics={[
              {
                label: "Patient-matched",
                value: String(patientMatchedStudies.length),
                detail:
                  patientMatchedStudies.length > 0
                    ? "Studies tagged to this patient are available in the viewer."
                    : "No study tags matched this chart ID, so the workspace may need order or PACS reconciliation.",
              },
              {
                label: "PACS (workspace)",
                value: String(studies.length),
                detail:
                  hasPatientMatch
                    ? "Orthanc returned this many studies for the workspace query; only patient-matched rows appear in the rail."
                    : "Studies exist in PACS but none matched this chart ID — reconcile DICOM PatientID with the chart before expecting a list here.",
              },
              {
                label: "Next action",
                value: selectedInstance ? "Review image" : selectedStudy ? "Choose series" : "Select study",
                detail:
                  selectedInstance
                    ? "Viewer controls are active for the selected instance."
                    : selectedStudy
                      ? "Pick a series to open the first image preview."
                      : "Start by selecting a study from the study list.",
              },
            ]}
          />

          <div className="rounded-3xl border border-slate-200 bg-slate-50/70 p-4">
            <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">
              Imaging loop status
            </p>
            <p className="mt-2 text-sm text-slate-800">
              {error
                ? "The imaging viewer could not reach PACS, so the clinical next step is to stay in orders, results, and documents while the external service is unavailable."
                : contextMode === "patient"
                  ? "Patient-matched imaging is available from this chart, so review can happen in place before handing findings into results, documents, or notes."
                  : contextMode === "reconcile"
                    ? "PACS returned studies for the workspace, but none matched this chart's patient identifier. The study rail stays empty here so another patient's images are not shown in this chart; reconcile PatientID (or use Orders/Documents) before review."
                    : "No patient-matched studies are available yet. Continue from Orders or Documents until PACS metadata aligns with this chart."}
            </p>
            <p className="mt-1 text-xs text-slate-500">
              Use Orders to place or reconcile imaging requests, Results for radiology outcome review, Documents for attached reports, and Notes for team communication or interpretation handoff.
            </p>
          </div>

          <div className="grid gap-4 xl:grid-cols-[320px_minmax(0,1fr)_260px]">
            <div className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
              <div className="flex items-center justify-between border-b border-gray-200 bg-gray-50 px-4 py-3">
                <div>
                  <h3 className="text-sm font-semibold text-gray-700">Studies</h3>
                  <p className="text-xs text-gray-500">
                    {contextMode === "patient"
                      ? "Patient-matched imaging studies"
                      : "Patient-matched studies only (no cross-patient fallback)"}
                  </p>
                </div>
                <div className="rounded-full bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700">
                  {visibleStudies.length}
                </div>
              </div>

              <div className="max-h-[calc(100vh-340px)] space-y-2 overflow-y-auto p-3">
                {isLoading && (
                  <div className="flex items-center justify-center py-12">
                    <Loader2 className="h-6 w-6 animate-spin text-blue-500" />
                    <span className="ml-2 text-sm text-gray-500">Loading studies...</span>
                  </div>
                )}

                {!isLoading && error && (
                  <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-center">
                    <AlertCircle className="mx-auto mb-2 h-8 w-8 text-amber-500" />
                    <p className="text-sm text-gray-700">PACS server unavailable</p>
                    <p className="mt-1 text-xs text-gray-500">Orthanc may not be running.</p>
                  </div>
                )}

                {!isLoading && !error && visibleStudies.length === 0 && (
                  <div className="rounded-xl border border-dashed border-gray-200 p-6 text-center">
                    <Search className="mx-auto mb-2 h-8 w-8 text-gray-300" />
                    <p className="text-sm text-gray-500">
                      {contextMode === "reconcile"
                        ? "No studies matched this patient in PACS"
                        : "No imaging studies found"}
                    </p>
                    <p className="mt-1 text-xs text-gray-400">
                      {contextMode === "reconcile"
                        ? `Orthanc has ${studies.length} workspace study/studies, but DICOM PatientID did not match this chart. Reconcile identifiers or open imaging from Orders.`
                        : "Continue from Orders or Documents until PACS metadata arrives."}
                    </p>
                  </div>
                )}

                {!isLoading && !error && contextMode === "reconcile" && (
                  <div className="rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-950">
                    Safety: unrelated patients&apos; studies are not listed in this chart. Align DICOM PatientID with `{patientId}` (or route via Orders) to populate the rail.
                  </div>
                )}

                {visibleStudies.map((study) => {
                  const isSelected = selectedStudy?.ID === study.ID;
                  return (
                    <button
                      key={study.ID}
                      type="button"
                      onClick={() => {
                        setSelectedStudy(study);
                        setSelectedInstance(null);
                        resetViewerControls();
                      }}
                      className={`w-full rounded-xl border p-3 text-left transition-colors ${
                        isSelected
                          ? "border-blue-500 bg-blue-50"
                          : "border-gray-200 hover:border-gray-300 hover:bg-gray-50"
                      }`}
                    >
                      <div className="flex items-start gap-3">
                        <Monitor className="mt-0.5 h-4 w-4 flex-shrink-0 text-blue-600" />
                        <div className="min-w-0">
                          <p className="truncate text-sm font-medium text-gray-800">
                            {study.MainDicomTags?.StudyDescription || "Unnamed study"}
                          </p>
                          <div className="mt-1 flex items-center gap-2 text-xs text-gray-500">
                            <Calendar className="h-3 w-3" />
                            <span>{formatDicomDate(study.MainDicomTags?.StudyDate)}</span>
                          </div>
                          <p className="mt-1 text-xs text-gray-400">
                            Patient tag: {study.PatientMainDicomTags?.PatientID || "Unavailable"}
                          </p>
                          <p className="mt-0.5 text-xs text-gray-400">
                            {study.Series?.length ?? 0} series
                          </p>
                        </div>
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>

            <div
              ref={viewerRef}
              className="overflow-hidden rounded-2xl border border-gray-200 bg-black shadow-sm"
            >
              <div className="flex items-center justify-between border-b border-gray-700 bg-gray-900 px-3 py-2">
                <div className="flex items-center gap-1">
                  <ToolButton
                    icon={<ZoomIn className="h-4 w-4" />}
                    label="Zoom in"
                    onClick={() => setZoom((current) => Math.min(current + 25, 400))}
                  />
                  <ToolButton
                    icon={<ZoomOut className="h-4 w-4" />}
                    label="Zoom out"
                    onClick={() => setZoom((current) => Math.max(current - 25, 25))}
                  />
                  <ToolButton
                    icon={<RotateCw className="h-4 w-4" />}
                    label="Rotate"
                    onClick={() => setRotation((current) => (current + 90) % 360)}
                  />
                  <ToolButton
                    icon={<Contrast className="h-4 w-4" />}
                    label="Reset view"
                    onClick={resetViewerControls}
                  />
                  <span className="ml-2 text-xs text-gray-400">{zoom}%</span>
                </div>

                <div className="flex items-center gap-2">
                  <div className="flex items-center gap-1">
                    <label className="text-xs text-gray-400">B</label>
                    <input
                      type="range"
                      min="0"
                      max="200"
                      value={brightness}
                      onChange={(event) => setBrightness(Number(event.target.value))}
                      className="h-1 w-16"
                      aria-label="Brightness"
                    />
                  </div>
                  <div className="flex items-center gap-1">
                    <label className="text-xs text-gray-400">C</label>
                    <input
                      type="range"
                      min="0"
                      max="200"
                      value={contrast}
                      onChange={(event) => setContrast(Number(event.target.value))}
                      className="h-1 w-16"
                      aria-label="Contrast"
                    />
                  </div>
                  <ToolButton
                    icon={isFullscreen ? <Minimize2 className="h-4 w-4" /> : <Maximize2 className="h-4 w-4" />}
                    label={isFullscreen ? "Exit fullscreen" : "Fullscreen"}
                    onClick={toggleFullscreen}
                  />
                </div>
              </div>

              <div className="flex min-h-[520px] flex-col">
                <div className="flex-1 items-center justify-center overflow-hidden p-4">
                  {!selectedStudy && (
                    <div className="flex h-full flex-col items-center justify-center text-center">
                      <Monitor className="mb-3 h-16 w-16 text-gray-600" />
                      <p className="text-sm text-gray-400">Select a study to review imaging</p>
                      <p className="mt-1 text-xs text-gray-600">
                        The viewer keeps chart follow-up linked to PACS review.
                      </p>
                    </div>
                  )}

                  {selectedStudy && !selectedInstance && (
                    <SeriesBrowser
                      isLoading={isLoadingSeries}
                      series={series}
                      onSelectInstance={setSelectedInstance}
                    />
                  )}

                  {selectedStudy && selectedInstance && (
                    <div className="flex h-full items-center justify-center overflow-hidden">
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img
                        src={`${process.env.NEXT_PUBLIC_BFF_URL || "http://localhost:8160"}/internal/v1/pacs/instances/${selectedInstance}/preview`}
                        alt="DICOM instance preview"
                        className="max-h-full max-w-full object-contain"
                        style={{
                          filter: `brightness(${brightness}%) contrast(${contrast}%)`,
                          transform: `scale(${zoom / 100}) rotate(${rotation}deg)`,
                          transition: "transform 0.2s ease",
                        }}
                      />
                    </div>
                  )}
                </div>

                {selectedStudy && selectedInstance && (
                  <div className="border-t border-gray-700 bg-gray-900 px-4 py-2 text-xs text-gray-300">
                    Reviewing {selectedSeries?.label ?? "selected series"}. Use Documents or Notes to record interpretation handoff from this image review.
                  </div>
                )}
              </div>
            </div>

            <div className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
              <div className="border-b border-gray-200 bg-gray-50 px-4 py-3">
                <h3 className="text-sm font-semibold text-gray-700">Study Details</h3>
              </div>
              <div className="space-y-3 p-4">
                {!selectedStudy && (
                  <div className="rounded-xl border border-dashed border-gray-200 p-4 text-sm text-gray-500">
                    Select a study to see details, then continue into documents, results, or notes from the header actions.
                  </div>
                )}

                {selectedStudy && (
                  <>
                    <InfoRow label="Description" value={selectedStudy.MainDicomTags?.StudyDescription} />
                    <InfoRow label="Date" value={formatDicomDate(selectedStudy.MainDicomTags?.StudyDate)} />
                    <InfoRow label="Accession" value={selectedStudy.MainDicomTags?.AccessionNumber} />
                    <InfoRow label="Study UID" value={selectedStudy.MainDicomTags?.StudyInstanceUID} mono />
                    <InfoRow label="Referring" value={selectedStudy.MainDicomTags?.ReferringPhysicianName} />
                    <InfoRow label="Patient Tag" value={selectedStudy.PatientMainDicomTags?.PatientID} />
                    <InfoRow label="Patient Name" value={selectedStudy.PatientMainDicomTags?.PatientName} />
                    <InfoRow label="Series Count" value={String(selectedStudy.Series?.length ?? 0)} />
                    <InfoRow label="Orthanc ID" value={selectedStudy.ID} mono />
                    <div className="rounded-xl border border-slate-200 bg-slate-50 p-3 text-xs text-slate-600">
                      Patient context is aligned in PACS metadata for this study. Review findings here, then carry the outcome into results, documents, or notes.
                    </div>
                  </>
                )}
              </div>
            </div>
          </div>
        </div>
      </PageShell>
    </EHRLayout>
  );
}

function SeriesBrowser({
  isLoading,
  onSelectInstance,
  series,
}: {
  isLoading: boolean;
  onSelectInstance: (id: string) => void;
  series: OrthancSeries[];
}) {
  if (isLoading) {
    return (
      <div className="flex h-full flex-col items-center justify-center text-center">
        <Loader2 className="mb-2 h-8 w-8 animate-spin text-blue-400" />
        <p className="text-sm text-gray-400">Loading series...</p>
      </div>
    );
  }

  if (series.length === 0) {
    return (
      <div className="flex h-full flex-col items-center justify-center text-center">
        <Grid3X3 className="mb-2 h-10 w-10 text-gray-600" />
        <p className="text-sm text-gray-400">No series available for this study</p>
      </div>
    );
  }

  return (
    <div className="grid gap-3 p-4 md:grid-cols-2 xl:grid-cols-3">
      {series.map((entry) => (
        <button
          key={entry.ID}
          type="button"
          onClick={() => {
            if (entry.instances[0]) {
              onSelectInstance(entry.instances[0]);
            }
          }}
          className="rounded-xl border border-gray-700 bg-gray-800 p-3 text-left transition-colors hover:border-blue-500"
        >
          <div className="mb-2 flex aspect-square items-center justify-center rounded-lg bg-gray-900">
            <Image className="h-8 w-8 text-gray-600" />
          </div>
          <p className="truncate text-xs font-medium text-white">{entry.label}</p>
          <p className="text-xs text-gray-400">
            {entry.modality} � {entry.instances.length} images
          </p>
        </button>
      ))}
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
      type="button"
      onClick={onClick}
      className="rounded p-1.5 text-gray-300 transition-colors hover:bg-gray-700 hover:text-white"
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
      <dd className={`mt-0.5 text-sm text-gray-800 ${mono ? "break-all font-mono text-xs" : ""}`}>
        {value}
      </dd>
    </div>
  );
}
