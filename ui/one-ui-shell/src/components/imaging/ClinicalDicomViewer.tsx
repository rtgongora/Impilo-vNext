"use client";

import dynamic from "next/dynamic";
import { useCallback, useEffect, useRef, useState } from "react";
import {
  Contrast,
  Maximize2,
  Minimize2,
  Move,
  RotateCcw,
  Ruler,
  Square,
  Triangle,
  Upload,
  ZoomIn,
} from "lucide-react";
import type { DwvClinicalViewerHandle, DwvDrawShape, DwvToolName } from "@/components/imaging/DwvClinicalViewer";
import { isDicomCandidate } from "@/lib/dicom/dicomService";

const DwvClinicalViewer = dynamic(
  () => import("@/components/imaging/DwvClinicalViewer").then((m) => m.DwvClinicalViewer),
  {
    ssr: false,
    loading: () => (
      <div className="flex h-full min-h-[420px] items-center justify-center bg-black text-sm text-gray-400">
        Starting DICOM viewer…
      </div>
    ),
  },
);

const WINDOW_PRESETS = [
  { name: "Default", preset: "mediastinum" },
  { name: "Lung", preset: "lung" },
  { name: "Bone", preset: "bone" },
  { name: "Brain", preset: "brain" },
] as const;

const TOOL_BUTTONS = [
  { id: "pan", label: "Pan / zoom (drag image)", dwv: "ZoomAndPan" as DwvToolName, icon: Move },
  { id: "scroll", label: "Scroll slices (mouse wheel)", dwv: "Scroll" as DwvToolName, icon: ZoomIn },
  { id: "window", label: "Window / level (drag on image)", dwv: "WindowLevel" as DwvToolName, icon: Contrast },
  { id: "draw", label: "Measure / draw", dwv: "Draw" as DwvToolName, icon: Ruler },
] as const;

const DRAW_SHAPES: { id: DwvDrawShape; label: string; icon: typeof Ruler }[] = [
  { id: "Ruler", label: "Length", icon: Ruler },
  { id: "Rectangle", label: "Rectangle", icon: Square },
  { id: "Ellipse", label: "Ellipse", icon: Triangle },
  { id: "Arrow", label: "Arrow", icon: Move },
];

export interface ClinicalDicomViewerProps {
  localFiles?: File[];
  instanceId?: string;
  initialLabel?: string;
  seriesLabel?: string;
  isFullscreen?: boolean;
  onToggleFullscreen?: () => void;
  onOpenUpload?: () => void;
  className?: string;
}

export function ClinicalDicomViewer({
  localFiles,
  instanceId,
  initialLabel,
  seriesLabel,
  isFullscreen = false,
  onToggleFullscreen,
  onOpenUpload,
  className = "",
}: ClinicalDicomViewerProps) {
  const dwvRef = useRef<DwvClinicalViewerHandle>(null);
  const localInputRef = useRef<HTMLInputElement>(null);
  const [files, setFiles] = useState<File[] | undefined>(localFiles);
  const [activeTool, setActiveTool] = useState<DwvToolName>("ZoomAndPan");
  const [drawShape, setDrawShape] = useState<DwvDrawShape>("Ruler");
  const [windowPreset, setWindowPreset] = useState<string | undefined>();
  const [loadError, setLoadError] = useState<string | null>(null);
  const [sourceLabel, setSourceLabel] = useState(initialLabel);

  useEffect(() => {
    if (localFiles?.length) {
      setFiles(localFiles);
      setLoadError(null);
      setSourceLabel(`Local · ${localFiles[0].name}`);
    }
  }, [localFiles]);

  const handleLocalFile = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;

    if (!isDicomCandidate(file)) {
      setLoadError("Please choose a .dcm / DICOM file.");
      setFiles(undefined);
      return;
    }

    setLoadError(null);
    setFiles([file]);
    setSourceLabel(`Local · ${file.name}`);
  }, []);

  const selectTool = useCallback((tool: DwvToolName) => {
    setActiveTool(tool);
    dwvRef.current?.setTool(tool);
    if (tool === "Draw") {
      dwvRef.current?.setDrawShape(drawShape);
    }
  }, [drawShape]);

  const selectDrawShape = useCallback((shape: DwvDrawShape) => {
    setDrawShape(shape);
    setActiveTool("Draw");
    dwvRef.current?.setTool("Draw");
    dwvRef.current?.setDrawShape(shape);
  }, []);

  const hasSource = (files && files.length > 0) || Boolean(instanceId);

  return (
    <div className={`flex min-h-[520px] flex-1 flex-col ${className}`}>
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-gray-700 bg-gray-900 px-2 py-2">
        <div className="flex flex-wrap items-center gap-0.5">
          {TOOL_BUTTONS.map((tool) => {
            const Icon = tool.icon;
            const active = activeTool === tool.dwv;
            return (
              <button
                key={tool.id}
                type="button"
                title={tool.label}
                aria-label={tool.label}
                onClick={() => selectTool(tool.dwv)}
                className={`rounded p-1.5 transition-colors ${
                  active ? "bg-impilo-600 text-white" : "text-gray-300 hover:bg-gray-700 hover:text-white"
                }`}
              >
                <Icon className="h-4 w-4" />
              </button>
            );
          })}
          {activeTool === "Draw" && (
            <span className="mx-1 flex items-center gap-0.5 border-l border-gray-600 pl-1">
              {DRAW_SHAPES.map((shape) => {
                const Icon = shape.icon;
                return (
                  <button
                    key={shape.id}
                    type="button"
                    title={shape.label}
                    onClick={() => selectDrawShape(shape.id)}
                    className={`rounded p-1 ${
                      drawShape === shape.id ? "bg-impilo-700 text-white" : "text-gray-400 hover:bg-gray-700"
                    }`}
                  >
                    <Icon className="h-3.5 w-3.5" />
                  </button>
                );
              })}
            </span>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <select
            className="rounded border border-gray-600 bg-gray-800 px-2 py-1 text-xs text-gray-200"
            defaultValue=""
            onChange={(e) => {
              const preset = WINDOW_PRESETS.find((p) => p.name === e.target.value);
              if (preset) setWindowPreset(preset.preset);
              e.target.value = "";
            }}
            aria-label="Window presets"
          >
            <option value="" disabled>
              W/L preset
            </option>
            {WINDOW_PRESETS.map((p) => (
              <option key={p.name} value={p.name}>
                {p.name}
              </option>
            ))}
          </select>
          <button
            type="button"
            title="Reset view (display + window/level)"
            className="rounded p-1.5 text-gray-300 hover:bg-gray-700"
            onClick={() => dwvRef.current?.resetView()}
          >
            <RotateCcw className="h-4 w-4" />
          </button>
          <button
            type="button"
            title="Fit image to viewer"
            className="rounded px-2 py-1 text-xs text-gray-300 hover:bg-gray-700"
            onClick={() => dwvRef.current?.fitToContainer()}
          >
            Fit
          </button>
          <input
            ref={localInputRef}
            type="file"
            accept=".dcm,.dicom,.DCM,.DICOM,application/dicom,application/octet-stream,*/*"
            className="hidden"
            onChange={handleLocalFile}
          />
          <button
            type="button"
            title="Open local DICOM"
            className="rounded p-1.5 text-gray-300 hover:bg-gray-700"
            onClick={() => localInputRef.current?.click()}
          >
            <Upload className="h-4 w-4" />
          </button>
          {onOpenUpload && (
            <button
              type="button"
              className="rounded-lg border border-gray-600 px-2 py-1 text-xs text-gray-200 hover:bg-gray-700"
              onClick={onOpenUpload}
            >
              Upload to PACS
            </button>
          )}
          {onToggleFullscreen && (
            <button
              type="button"
              title="Fullscreen"
              className="rounded p-1.5 text-gray-300 hover:bg-gray-700"
              onClick={onToggleFullscreen}
            >
              {isFullscreen ? <Minimize2 className="h-4 w-4" /> : <Maximize2 className="h-4 w-4" />}
            </button>
          )}
        </div>
      </div>

      <div className="relative min-h-0 flex-1">
        {loadError && (
          <div className="absolute inset-x-0 top-0 z-20 border-b border-amber-700/50 bg-amber-900/50 px-3 py-2 text-xs text-amber-200">
            {loadError}
          </div>
        )}

        {!hasSource && (
          <div className="absolute inset-0 z-[1] flex flex-col items-center justify-center text-zinc-500">
            <p className="text-lg">No DICOM loaded</p>
            <p className="mt-1 text-sm">Use the upload icon in the toolbar or drop a .dcm file</p>
          </div>
        )}

        {hasSource && (
          <DwvClinicalViewer
            ref={dwvRef}
            files={files}
            pacsInstanceId={instanceId}
            activeTool={activeTool}
            drawShape={drawShape}
            windowPreset={windowPreset}
            className="absolute inset-0 h-full w-full"
            onReady={() => {
              requestAnimationFrame(() => dwvRef.current?.fitToContainer());
            }}
            onError={(msg) => setLoadError(msg)}
            onLabel={(label) => setSourceLabel(label)}
          />
        )}

        {sourceLabel && hasSource && (
          <div className="pointer-events-none absolute left-3 top-3 z-20 rounded bg-gray-900/90 px-2 py-1 text-[10px] text-green-400">
            {sourceLabel}
            {seriesLabel && <span className="ml-2 text-gray-500">{seriesLabel}</span>}
            <span className="ml-2 text-gray-600">· {activeTool}</span>
          </div>
        )}
      </div>
    </div>
  );
}
