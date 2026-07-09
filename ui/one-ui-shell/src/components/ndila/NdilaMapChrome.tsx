"use client";

import { Crosshair, Layers, LocateFixed, Maximize2, Minimize2, Minus, Plus } from "lucide-react";

export type NdilaMapLayerMode = "admin" | "streets";

interface NdilaMapChromeProps {
  layerMode: NdilaMapLayerMode;
  streetsAvailable: boolean;
  isFullscreen: boolean;
  onLayerModeChange: (mode: NdilaMapLayerMode) => void;
  onZoomIn: () => void;
  onZoomOut: () => void;
  onLocate: () => void;
  onFitCountry: () => void;
  onToggleFullscreen: () => void;
}

export function NdilaMapChrome({
  layerMode,
  streetsAvailable,
  isFullscreen,
  onLayerModeChange,
  onZoomIn,
  onZoomOut,
  onLocate,
  onFitCountry,
  onToggleFullscreen,
}: NdilaMapChromeProps) {
  return (
    <div className="pointer-events-none absolute inset-0 z-20">
      <div className="pointer-events-auto absolute left-3 top-3 flex flex-col gap-2">
        <div className="overflow-hidden rounded-lg border border-border/80 bg-card/95 shadow-md backdrop-blur-sm">
          <button
            type="button"
            onClick={onZoomIn}
            className="flex h-9 w-9 items-center justify-center border-b border-border/70 text-foreground hover:bg-muted"
            aria-label="Zoom in"
          >
            <Plus className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={onZoomOut}
            className="flex h-9 w-9 items-center justify-center text-foreground hover:bg-muted"
            aria-label="Zoom out"
          >
            <Minus className="h-4 w-4" />
          </button>
        </div>
        <button
          type="button"
          onClick={onLocate}
          className="flex h-9 w-9 items-center justify-center rounded-lg border border-border/80 bg-card/95 text-foreground shadow-md backdrop-blur-sm hover:bg-muted"
          aria-label="My location"
          title="My location"
        >
          <LocateFixed className="h-4 w-4" />
        </button>
        <button
          type="button"
          onClick={onFitCountry}
          className="flex h-9 w-9 items-center justify-center rounded-lg border border-border/80 bg-card/95 text-foreground shadow-md backdrop-blur-sm hover:bg-muted"
          aria-label="Fit Zimbabwe"
          title="Fit Zimbabwe"
        >
          <Crosshair className="h-4 w-4" />
        </button>
      </div>

      <div className="pointer-events-auto absolute right-3 top-3 flex flex-col gap-2">
        <div className="overflow-hidden rounded-lg border border-border/80 bg-card/95 shadow-md backdrop-blur-sm">
          <div className="flex items-center gap-1 border-b border-border/70 px-2 py-1.5 text-[10px] font-medium text-muted-foreground">
            <Layers className="h-3 w-3" />
            Map type
          </div>
          <button
            type="button"
            onClick={() => onLayerModeChange("admin")}
            className={`block w-full px-3 py-2 text-left text-xs ${layerMode === "admin" ? "bg-indigo-50 font-semibold text-indigo-900" : "text-foreground hover:bg-muted"}`}
          >
            Administrative
          </button>
          <button
            type="button"
            disabled={!streetsAvailable}
            onClick={() => onLayerModeChange("streets")}
            className={`block w-full px-3 py-2 text-left text-xs ${layerMode === "streets" ? "bg-indigo-50 font-semibold text-indigo-900" : "text-foreground hover:bg-muted"} disabled:cursor-not-allowed disabled:opacity-45`}
            title={streetsAvailable ? "Street tiles from Ndila provider" : "Configure Ndila street tile provider"}
          >
            Streets
          </button>
        </div>
        <button
          type="button"
          onClick={onToggleFullscreen}
          className="flex h-9 w-9 items-center justify-center self-end rounded-lg border border-border/80 bg-card/95 text-foreground shadow-md backdrop-blur-sm hover:bg-muted"
          aria-label={isFullscreen ? "Exit fullscreen" : "Fullscreen map"}
        >
          {isFullscreen ? <Minimize2 className="h-4 w-4" /> : <Maximize2 className="h-4 w-4" />}
        </button>
      </div>
    </div>
  );
}
