/**
 * DicomCanvas — renders DICOM as a visible preview (img + data URL) with annotation overlay.
 */

import {
  useRef,
  useEffect,
  useState,
  useCallback,
  forwardRef,
  useImperativeHandle,
  useLayoutEffect,
} from "react";
import { DicomImage, renderDicomToCanvas, DicomMeasurements } from "@/lib/dicom/dicomService";

export interface ViewerState {
  zoom: number;
  pan: { x: number; y: number };
  rotation: number;
  flipH: boolean;
  flipV: boolean;
  windowWidth: number;
  windowCenter: number;
  invert: boolean;
}

export interface Annotation {
  id: string;
  type: "length" | "angle" | "ellipse" | "rectangle" | "arrow" | "text" | "freehand";
  points: { x: number; y: number }[];
  measurement?: string;
  label?: string;
  color?: string;
  isComplete: boolean;
}

export interface DicomCanvasProps {
  image: DicomImage | null;
  viewerState: ViewerState;
  activeTool: string;
  showAnnotations: boolean;
  annotations: Annotation[];
  onAnnotationAdd?: (annotation: Omit<Annotation, "id">) => void;
  onAnnotationUpdate?: (id: string, annotation: Partial<Annotation>) => void;
  onViewerStateChange?: (state: Partial<ViewerState>) => void;
  onPixelProbe?: (x: number, y: number, value: number) => void;
  className?: string;
}

export interface DicomCanvasRef {
  resetView: () => void;
  fitToWindow: () => void;
  exportImage: () => string | null;
}

const FALLBACK_VIEWPORT = { w: 900, h: 520 };

export const DicomCanvas = forwardRef<DicomCanvasRef, DicomCanvasProps>(
  (
    {
      image,
      viewerState,
      activeTool,
      showAnnotations,
      annotations,
      onAnnotationAdd,
      onViewerStateChange,
      onPixelProbe,
      className = "",
    },
    ref,
  ) => {
    const containerRef = useRef<HTMLDivElement>(null);
    const stageRef = useRef<HTMLDivElement>(null);
    const renderCanvasRef = useRef<HTMLCanvasElement>(null);
    const overlayCanvasRef = useRef<HTMLCanvasElement>(null);

    const [containerSize, setContainerSize] = useState(FALLBACK_VIEWPORT);
    const [previewUrl, setPreviewUrl] = useState<string | null>(null);
    const [isDragging, setIsDragging] = useState(false);
    const [dragStart, setDragStart] = useState({ x: 0, y: 0 });
    const [currentAnnotation, setCurrentAnnotation] = useState<Partial<Annotation> | null>(null);

    useEffect(() => {
      const el = containerRef.current;
      if (!el) return;

      const update = () => {
        const w = el.clientWidth;
        const h = el.clientHeight;
        if (w > 8 && h > 8) {
          setContainerSize({ w, h });
        }
      };

      update();
      const ro = new ResizeObserver(update);
      ro.observe(el);
      window.addEventListener("resize", update);
      return () => {
        ro.disconnect();
        window.removeEventListener("resize", update);
      };
    }, []);

    useEffect(() => {
      if (!image) return;
      const el = containerRef.current;
      if (!el) return;
      const measure = () => {
        const w = el.clientWidth;
        const h = el.clientHeight;
        if (w > 8 && h > 8) setContainerSize({ w, h });
      };
      measure();
      requestAnimationFrame(measure);
      const t = window.setTimeout(measure, 50);
      return () => window.clearTimeout(t);
    }, [image]);

    const viewportW = containerSize.w > 0 ? containerSize.w : FALLBACK_VIEWPORT.w;
    const viewportH = containerSize.h > 0 ? containerSize.h : FALLBACK_VIEWPORT.h;

    const baseFit = image
      ? Math.min(viewportW / image.width, viewportH / image.height)
      : 0;

    const zoomFactor = Math.max(viewerState.zoom, 10) / 100;
    const displayScale = baseFit * zoomFactor;
    const displayW = image ? Math.max(1, Math.round(image.width * displayScale)) : 0;
    const displayH = image ? Math.max(1, Math.round(image.height * displayScale)) : 0;

    const stageTransform = image
      ? `translate(${viewerState.pan.x}px, ${viewerState.pan.y}px) rotate(${viewerState.rotation}deg) scaleX(${viewerState.flipH ? -1 : 1}) scaleY(${viewerState.flipV ? -1 : 1})`
      : undefined;

    useImperativeHandle(ref, () => ({
      resetView: () => {
        onViewerStateChange?.({
          zoom: 100,
          pan: { x: 0, y: 0 },
          rotation: 0,
          flipH: false,
          flipV: false,
          invert: false,
        });
      },
      fitToWindow: () => {
        onViewerStateChange?.({ zoom: 100, pan: { x: 0, y: 0 } });
      },
      exportImage: () => renderCanvasRef.current?.toDataURL("image/png") ?? previewUrl,
    }));

    useLayoutEffect(() => {
      if (!image || !renderCanvasRef.current) {
        setPreviewUrl(null);
        return;
      }

      renderDicomToCanvas(
        renderCanvasRef.current,
        image,
        viewerState.windowWidth,
        viewerState.windowCenter,
        { invert: viewerState.invert },
      );

      try {
        setPreviewUrl(renderCanvasRef.current.toDataURL("image/png"));
      } catch {
        setPreviewUrl(null);
      }
    }, [
      image,
      viewerState.windowWidth,
      viewerState.windowCenter,
      viewerState.invert,
    ]);

    useEffect(() => {
      if (!overlayCanvasRef.current || !image) return;

      const canvas = overlayCanvasRef.current;
      const ctx = canvas.getContext("2d");
      if (!ctx) return;

      canvas.width = image.width;
      canvas.height = image.height;
      ctx.clearRect(0, 0, canvas.width, canvas.height);

      if (!showAnnotations) return;

      const allAnnotations = [...annotations, ...(currentAnnotation ? [currentAnnotation as Annotation] : [])];

      for (const ann of allAnnotations) {
        const color = ann.color || "#00ff00";
        ctx.strokeStyle = color;
        ctx.fillStyle = color;
        ctx.lineWidth = 2;
        ctx.font = "14px monospace";

        const points = ann.points || [];
        if (points.length === 0) continue;

        switch (ann.type) {
          case "length":
            if (points.length >= 2) {
              ctx.beginPath();
              ctx.moveTo(points[0].x, points[0].y);
              ctx.lineTo(points[1].x, points[1].y);
              ctx.stroke();
              ctx.beginPath();
              ctx.arc(points[0].x, points[0].y, 4, 0, Math.PI * 2);
              ctx.fill();
              ctx.beginPath();
              ctx.arc(points[1].x, points[1].y, 4, 0, Math.PI * 2);
              ctx.fill();
              if (ann.measurement) {
                ctx.fillText(ann.measurement, (points[0].x + points[1].x) / 2 + 5, (points[0].y + points[1].y) / 2 - 5);
              }
            }
            break;
          case "angle":
            if (points.length >= 3) {
              ctx.beginPath();
              ctx.moveTo(points[0].x, points[0].y);
              ctx.lineTo(points[1].x, points[1].y);
              ctx.lineTo(points[2].x, points[2].y);
              ctx.stroke();
              const a1 = Math.atan2(points[0].y - points[1].y, points[0].x - points[1].x);
              const a2 = Math.atan2(points[2].y - points[1].y, points[2].x - points[1].x);
              ctx.beginPath();
              ctx.arc(points[1].x, points[1].y, 20, a1, a2);
              ctx.stroke();
              if (ann.measurement) ctx.fillText(ann.measurement, points[1].x + 25, points[1].y);
            }
            break;
          case "ellipse":
            if (points.length >= 2) {
              const cx = (points[0].x + points[1].x) / 2;
              const cy = (points[0].y + points[1].y) / 2;
              const rx = Math.abs(points[1].x - points[0].x) / 2;
              const ry = Math.abs(points[1].y - points[0].y) / 2;
              ctx.beginPath();
              ctx.ellipse(cx, cy, rx, ry, 0, 0, Math.PI * 2);
              ctx.stroke();
              if (ann.measurement) ctx.fillText(ann.measurement, cx, cy - ry - 5);
            }
            break;
          case "rectangle":
            if (points.length >= 2) {
              const x = Math.min(points[0].x, points[1].x);
              const y = Math.min(points[0].y, points[1].y);
              ctx.strokeRect(x, y, Math.abs(points[1].x - points[0].x), Math.abs(points[1].y - points[0].y));
              if (ann.measurement) ctx.fillText(ann.measurement, x, y - 5);
            }
            break;
          case "arrow":
            if (points.length >= 2) {
              const headLen = 15;
              const dx = points[1].x - points[0].x;
              const dy = points[1].y - points[0].y;
              const angle = Math.atan2(dy, dx);
              ctx.beginPath();
              ctx.moveTo(points[0].x, points[0].y);
              ctx.lineTo(points[1].x, points[1].y);
              ctx.lineTo(
                points[1].x - headLen * Math.cos(angle - Math.PI / 6),
                points[1].y - headLen * Math.sin(angle - Math.PI / 6),
              );
              ctx.moveTo(points[1].x, points[1].y);
              ctx.lineTo(
                points[1].x - headLen * Math.cos(angle + Math.PI / 6),
                points[1].y - headLen * Math.sin(angle + Math.PI / 6),
              );
              ctx.stroke();
              if (ann.label) ctx.fillText(ann.label, points[0].x, points[0].y - 5);
            }
            break;
          case "text":
            if (points.length >= 1 && ann.label) ctx.fillText(ann.label, points[0].x, points[0].y);
            break;
          case "freehand":
            if (points.length >= 2) {
              ctx.beginPath();
              ctx.moveTo(points[0].x, points[0].y);
              for (let i = 1; i < points.length; i++) ctx.lineTo(points[i].x, points[i].y);
              if (ann.isComplete) ctx.closePath();
              ctx.stroke();
            }
            break;
        }
      }
    }, [annotations, currentAnnotation, showAnnotations, image]);

    const screenToImage = useCallback(
      (screenX: number, screenY: number): { x: number; y: number } => {
        if (!image || !stageRef.current) return { x: 0, y: 0 };
        const rect = stageRef.current.getBoundingClientRect();
        if (rect.width < 1 || rect.height < 1) return { x: 0, y: 0 };
        return {
          x: ((screenX - rect.left) / rect.width) * image.width,
          y: ((screenY - rect.top) / rect.height) * image.height,
        };
      },
      [image],
    );

    const handleMouseDown = useCallback(
      (e: React.MouseEvent) => {
        if (!image) return;
        const imagePos = screenToImage(e.clientX, e.clientY);
        setIsDragging(true);
        setDragStart({ x: e.clientX, y: e.clientY });

        if (["measure", "angle", "circle", "rectangle", "arrow", "annotate", "freehand"].includes(activeTool)) {
          const typeMap: Record<string, Annotation["type"]> = {
            measure: "length",
            angle: "angle",
            circle: "ellipse",
            rectangle: "rectangle",
            arrow: "arrow",
            annotate: "text",
            freehand: "freehand",
          };
          setCurrentAnnotation({
            type: typeMap[activeTool] || "length",
            points: [imagePos],
            isComplete: false,
            color: "#00ff00",
          });
        }
      },
      [image, activeTool, screenToImage],
    );

    const handleMouseMove = useCallback(
      (e: React.MouseEvent) => {
        if (!image) return;
        const imagePos = screenToImage(e.clientX, e.clientY);

        if (onPixelProbe && imagePos.x >= 0 && imagePos.x < image.width && imagePos.y >= 0 && imagePos.y < image.height) {
          const idx = Math.floor(imagePos.y) * image.width + Math.floor(imagePos.x);
          const pixelData = image.getPixelData();
          if (idx >= 0 && idx < pixelData.length) {
            onPixelProbe(Math.floor(imagePos.x), Math.floor(imagePos.y), pixelData[idx] * image.slope + image.intercept);
          }
        }

        if (!isDragging) return;
        const dx = e.clientX - dragStart.x;
        const dy = e.clientY - dragStart.y;

        if (activeTool === "pan") {
          onViewerStateChange?.({ pan: { x: viewerState.pan.x + dx, y: viewerState.pan.y + dy } });
          setDragStart({ x: e.clientX, y: e.clientY });
        } else if (activeTool === "zoom") {
          onViewerStateChange?.({ zoom: Math.max(10, Math.min(400, viewerState.zoom + dy * -0.5)) });
          setDragStart({ x: e.clientX, y: e.clientY });
        } else if (activeTool === "window") {
          onViewerStateChange?.({
            windowWidth: Math.max(1, viewerState.windowWidth + dx * 2),
            windowCenter: viewerState.windowCenter + dy * 2,
          });
          setDragStart({ x: e.clientX, y: e.clientY });
        } else if (currentAnnotation) {
          if (currentAnnotation.type === "freehand") {
            setCurrentAnnotation({
              ...currentAnnotation,
              points: [...(currentAnnotation.points || []), imagePos],
            });
          } else if (currentAnnotation.type === "angle") {
            const points = currentAnnotation.points || [];
            if (points.length === 1) {
              setCurrentAnnotation({ ...currentAnnotation, points: [points[0], imagePos] });
            } else if (points.length === 2) {
              setCurrentAnnotation({ ...currentAnnotation, points: [points[0], points[1], imagePos] });
            }
          } else {
            const points = currentAnnotation.points || [];
            setCurrentAnnotation({ ...currentAnnotation, points: [points[0], imagePos] });
          }
        }
      },
      [image, isDragging, dragStart, activeTool, viewerState, currentAnnotation, onViewerStateChange, onPixelProbe, screenToImage],
    );

    const handleMouseUp = useCallback(() => {
      setIsDragging(false);
      if (currentAnnotation?.points && currentAnnotation.points.length >= 2) {
        const points = currentAnnotation.points;
        let measurement = "";
        const pixelSpacing: [number, number] = [image?.rowPixelSpacing || 1, image?.columnPixelSpacing || 1];
        if (currentAnnotation.type === "length" && points.length >= 2) {
          measurement = `${DicomMeasurements.calculateDistance(points[0], points[1], pixelSpacing).toFixed(1)} mm`;
        } else if (currentAnnotation.type === "angle" && points.length >= 3) {
          measurement = `${DicomMeasurements.calculateAngle(points[0], points[1], points[2]).toFixed(1)}°`;
        } else if (currentAnnotation.type === "ellipse" && points.length >= 2) {
          const rx = (Math.abs(points[1].x - points[0].x) / 2) * pixelSpacing[1];
          const ry = (Math.abs(points[1].y - points[0].y) / 2) * pixelSpacing[0];
          measurement = `Area: ${(Math.PI * rx * ry).toFixed(1)} mm²`;
        } else if (currentAnnotation.type === "rectangle" && points.length >= 2) {
          measurement = `${(Math.abs(points[1].x - points[0].x) * pixelSpacing[1]).toFixed(1)} × ${(Math.abs(points[1].y - points[0].y) * pixelSpacing[0]).toFixed(1)} mm`;
        }
        onAnnotationAdd?.({ ...currentAnnotation, measurement, isComplete: true } as Omit<Annotation, "id">);
      }
      setCurrentAnnotation(null);
    }, [currentAnnotation, image, onAnnotationAdd]);

    const handleWheel = useCallback(
      (e: React.WheelEvent) => {
        e.preventDefault();
        onViewerStateChange?.({ zoom: Math.max(10, Math.min(400, viewerState.zoom + (e.deltaY > 0 ? -10 : 10))) });
      },
      [viewerState.zoom, onViewerStateChange],
    );

    return (
      <div
        ref={containerRef}
        className={`relative flex h-full min-h-[420px] w-full items-center justify-center overflow-hidden bg-black ${className}`}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        onWheel={handleWheel}
        style={{ cursor: getCursorForTool(activeTool) }}
      >
        <canvas ref={renderCanvasRef} className="pointer-events-none absolute left-[-9999px] top-0 opacity-0" aria-hidden />

        {image && previewUrl && (
          <div
            ref={stageRef}
            className="relative shrink-0 select-none"
            style={{
              width: displayW,
              height: displayH,
              transform: stageTransform,
              transformOrigin: "center center",
            }}
          >
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={previewUrl}
              alt="DICOM preview"
              width={displayW}
              height={displayH}
              className="block h-full w-full"
              style={{ imageRendering: "pixelated" }}
              draggable={false}
            />
            <canvas
              ref={overlayCanvasRef}
              className="pointer-events-none absolute inset-0 h-full w-full"
              style={{ imageRendering: "pixelated" }}
            />
          </div>
        )}

        {image && !previewUrl && (
          <p className="text-sm text-gray-400">Rendering preview…</p>
        )}

        {!image && (
          <div className="text-center text-zinc-500">
            <div className="mb-4 text-6xl">🩻</div>
            <p className="text-lg">No DICOM image loaded</p>
            <p className="text-sm">Use the toolbar upload icon or drop a .dcm file</p>
          </div>
        )}
      </div>
    );
  },
);

DicomCanvas.displayName = "DicomCanvas";

function getCursorForTool(tool: string): string {
  switch (tool) {
    case "pan":
      return "grab";
    case "zoom":
      return "zoom-in";
    case "window":
      return "ns-resize";
    case "measure":
    case "angle":
    case "circle":
    case "rectangle":
    case "arrow":
    case "annotate":
    case "freehand":
      return "crosshair";
    default:
      return "default";
  }
}

export default DicomCanvas;
