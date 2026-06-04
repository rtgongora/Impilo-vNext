"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import {
  Activity,
  AlertCircle,
  Calendar,
  ClipboardList,
  FileText,
  Grid3X3,
  Image,
  Loader2,
  Monitor,
  Search,
  Upload,
} from "lucide-react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ClinicalDicomViewer } from "@/components/imaging/ClinicalDicomViewer";
import { DicomDropZone } from "@/components/imaging/DicomDropZone";
import { DicomUploadDialog } from "@/components/imaging/DicomUploadDialog";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useImagingStudies, useSyncImagingHierarchy } from "@/hooks/queries/useImaging";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient } from "@/lib/api-client";
import { isDicomCandidate } from "@/lib/dicom/dicomService";

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
  const queryClient = useQueryClient();
  const facility = useFacilityStore((state) => state.facility);
  const { data: encountersData } = useEncounters(patientId);
  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.status === "IN_PROGRESS" || encounter.attributes.status === "ACTIVE"
  );
  const { data: studies = [], isLoading, error } = useStudies(patientId);
  const { data: imagingStudiesData } = useImagingStudies(patientId);
  const governedStudies = (imagingStudiesData as { data?: unknown[] } | undefined)?.data ?? [];
  const syncHierarchy = useSyncImagingHierarchy(patientId);

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
  const [uploadOpen, setUploadOpen] = useState(false);
  const [localFiles, setLocalFiles] = useState<File[] | undefined>(undefined);
  const [localFileName, setLocalFileName] = useState<string | null>(null);
  const [localLoading, setLocalLoading] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);
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

  const toggleFullscreen = useCallback(() => {
    if (!viewerRef.current) return;

    if (!isFullscreen) {
      viewerRef.current.requestFullscreen?.();
    } else {
      document.exitFullscreen?.();
    }
  }, [isFullscreen]);

  const loadLocalDicomFile = useCallback((file: File) => {
    if (!isDicomCandidate(file)) {
      setLocalError("Please select a DICOM file (.dcm). ZIP folders or JPEG previews cannot be opened directly.");
      return;
    }
    setLocalLoading(true);
    setLocalError(null);
    setLocalFiles([file]);
    setLocalFileName(file.name);
    setSelectedInstance(null);
    setSelectedStudy(null);
    setLocalLoading(false);
  }, []);

  const handleViewLocalPayload = useCallback((payload: { file: File; fileName: string }) => {
    setLocalFiles([payload.file]);
    setLocalFileName(payload.fileName);
    setSelectedInstance(null);
    setSelectedStudy(null);
    setLocalError(null);
  }, []);

  const handleClearLocalImage = useCallback(() => {
    setLocalFiles(undefined);
    setLocalFileName(null);
    setLocalError(null);
  }, []);

  const viewerActive = Boolean((localFiles && localFiles.length > 0) || selectedInstance);

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
                label: "Governed imaging",
                value: String(governedStudies.length),
                detail:
                  governedStudies.length > 0
                    ? "Studies returned from the governed imaging service for this patient."
                    : "No governed imaging studies yet; the PACS workspace view is active below.",
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
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => setUploadOpen(true)}
                    className="inline-flex items-center gap-1 rounded-lg border border-impilo-200 bg-white px-2 py-1 text-xs font-medium text-impilo-700 hover:bg-impilo-50"
                  >
                    <Upload className="h-3.5 w-3.5" />
                    View / upload
                  </button>
                  <div className="rounded-full bg-impilo-50 px-2.5 py-1 text-xs font-medium text-impilo-600">
                    {visibleStudies.length}
                  </div>
                </div>
              </div>

              <div className="max-h-[calc(100vh-340px)] space-y-2 overflow-y-auto p-3">
                {isLoading && (
                  <div className="flex items-center justify-center py-12">
                    <Loader2 className="h-6 w-6 animate-spin text-impilo-400" />
                    <span className="ml-2 text-sm text-gray-500">Loading studies...</span>
                  </div>
                )}

                {!isLoading && error && (
                  <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-center">
                    <AlertCircle className="mx-auto mb-2 h-8 w-8 text-amber-500" />
                    <p className="text-sm text-gray-700">PACS server unavailable</p>
                    <p className="mt-1 text-xs text-gray-500">
                      Orthanc isn&apos;t reachable. You can still open a local <code>.dcm</code> file and diagnose it with measurements below.
                    </p>
                    <button
                      type="button"
                      onClick={() => setUploadOpen(true)}
                      className="mt-3 inline-flex items-center gap-1 rounded-lg bg-impilo-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-impilo-700"
                    >
                      <Upload className="h-3.5 w-3.5" />
                      Upload / view DICOM
                    </button>
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

                {governedStudies.length > 0 && (
                  <div className="mb-3 rounded-xl border border-impilo-100 bg-impilo-50/60 p-3">
                    <p className="text-xs font-semibold uppercase tracking-wide text-impilo-700">
                      Governed imaging (adapter)
                    </p>
                    <ul className="mt-2 space-y-2">
                      {governedStudies
                        .map((raw) => {
                          const g = raw as Record<string, unknown>;
                          const id = g.id;
                          const studyUid = (g.studyUid ?? g.study_uid) as string | undefined;
                          if (id == null || !studyUid) return null;
                          return (
                            <li
                              key={String(id)}
                              className="flex flex-col gap-1 rounded-lg border border-white/60 bg-white/80 p-2 text-xs text-slate-700"
                            >
                              <span className="font-mono text-[11px] text-slate-500">{studyUid}</span>
                              <div className="flex flex-wrap gap-2">
                                <Link
                                  className="inline-flex items-center rounded-full bg-impilo-600 px-2.5 py-1 text-[11px] font-medium text-white hover:bg-impilo-700"
                                  href={`/ehr/${encodeURIComponent(patientId)}/imaging/viewer?studyUid=${encodeURIComponent(
                                    studyUid,
                                  )}&governedStudyId=${encodeURIComponent(String(id))}`}
                                >
                                  DICOMweb viewer
                                </Link>
                                <button
                                  type="button"
                                  className="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-[11px] font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
                                  disabled={syncHierarchy.isPending}
                                  onClick={() => syncHierarchy.mutate({ studyId: String(id) })}
                                >
                                  Sync hierarchy
                                </button>
                              </div>
                            </li>
                          );
                        })
                        .filter(Boolean)}
                    </ul>
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
                      }}
                      className={`w-full rounded-xl border p-3 text-left transition-colors ${
                        isSelected
                          ? "border-impilo-400 bg-impilo-50"
                          : "border-gray-200 hover:border-gray-300 hover:bg-gray-50"
                      }`}
                    >
                      <div className="flex items-start gap-3">
                        <Monitor className="mt-0.5 h-4 w-4 flex-shrink-0 text-impilo-500" />
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
              className="impilo-imaging-viewer-panel flex min-h-[min(72vh,640px)] flex-col overflow-hidden rounded-2xl border border-gray-200 bg-black shadow-sm"
            >
              {localError && (
                <div className="border-b border-amber-700/50 bg-amber-900/40 px-3 py-2 text-xs text-amber-200">
                  {localError}
                </div>
              )}

              <div className="flex min-h-0 flex-1 flex-col">
                {localFiles && localFiles.length > 0 ? (
                  <ClinicalDicomViewer
                    localFiles={localFiles}
                    initialLabel={localFileName ? `Local · ${localFileName}` : "Local DICOM"}
                    isFullscreen={isFullscreen}
                    onToggleFullscreen={toggleFullscreen}
                    onOpenUpload={() => setUploadOpen(true)}
                    className="flex-1"
                  />
                ) : selectedInstance ? (
                  <ClinicalDicomViewer
                    instanceId={selectedInstance}
                    seriesLabel={selectedSeries?.label}
                    isFullscreen={isFullscreen}
                    onToggleFullscreen={toggleFullscreen}
                    onOpenUpload={() => setUploadOpen(true)}
                    className="flex-1"
                  />
                ) : selectedStudy && !selectedInstance ? (
                  <SeriesBrowser
                    isLoading={isLoadingSeries}
                    series={series}
                    onSelectInstance={setSelectedInstance}
                  />
                ) : (
                  <DicomDropZone
                    loading={localLoading}
                    onFileSelected={(file) => void loadLocalDicomFile(file)}
                    onOpenUploadDialog={() => setUploadOpen(true)}
                    onReject={(msg) => setLocalError(msg)}
                  />
                )}

                {viewerActive && (
                  <div className="flex items-center justify-between gap-2 border-t border-gray-700 bg-gray-900 px-4 py-2 text-xs text-gray-300">
                    <span>
                      {localFiles?.length
                        ? "DWV viewer: pan/zoom, window/level, scroll, draw (ruler, shapes). Upload more via toolbar."
                        : `PACS · ${selectedSeries?.label ?? "instance"}. Same diagnosis tools in the toolbar.`}
                    </span>
                    {localFiles?.length ? (
                      <button
                        type="button"
                        onClick={handleClearLocalImage}
                        className="shrink-0 rounded border border-gray-600 px-2 py-1 hover:bg-gray-800"
                      >
                        Close image
                      </button>
                    ) : null}
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
                    {selectedStudy.MainDicomTags?.StudyInstanceUID && (
                      <Link
                        className="inline-flex w-full items-center justify-center rounded-xl bg-impilo-600 px-3 py-2 text-center text-xs font-semibold text-white hover:bg-impilo-700"
                        href={`/ehr/${encodeURIComponent(patientId)}/imaging/viewer?studyUid=${encodeURIComponent(
                          selectedStudy.MainDicomTags.StudyInstanceUID,
                        )}`}
                      >
                        Open DICOMweb viewer (study / series / instances)
                      </Link>
                    )}
                    <div className="rounded-xl border border-slate-200 bg-slate-50 p-3 text-xs text-slate-600">
                      Patient context is aligned in PACS metadata for this study. Review findings here, then carry the outcome into results, documents, or notes.
                    </div>
                  </>
                )}
              </div>
            </div>
          </div>
        </div>

        <DicomUploadDialog
          open={uploadOpen}
          onOpenChange={setUploadOpen}
          patientId={patientId}
          onViewLocal={handleViewLocalPayload}
          onSuccess={(lastInstanceId) => {
            void queryClient.invalidateQueries({ queryKey: ["pacs", "studies", patientId] });
            if (lastInstanceId) {
              setLocalFiles(undefined);
              setLocalFileName(null);
              setSelectedInstance(lastInstanceId);
            }
          }}
        />
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
        <Loader2 className="mb-2 h-8 w-8 animate-spin text-impilo-300" />
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
          className="rounded-xl border border-gray-700 bg-gray-800 p-3 text-left transition-colors hover:border-impilo-400"
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
