"use client";

import { useMemo, useState } from "react";
import { CheckCircle2, Circle, ExternalLink, FileText, Play, Wrench } from "lucide-react";

type LessonRecord = Record<string, unknown>;

function parsePracticalSteps(raw: string): string[] {
  if (!raw.trim()) return [];
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (Array.isArray(parsed)) {
      return parsed.map((item) => {
        if (typeof item === "string") return item;
        if (item && typeof item === "object" && "text" in item) return String((item as { text: unknown }).text);
        return String(item);
      });
    }
    if (parsed && typeof parsed === "object" && Array.isArray((parsed as { steps?: unknown }).steps)) {
      return ((parsed as { steps: unknown[] }).steps ?? []).map((s) => String(s));
    }
  } catch {
    // plain text fallback
  }
  return raw
    .split(/\n+/)
    .map((line) => line.replace(/^[-*]\s*/, "").trim())
    .filter(Boolean);
}

function toEmbedUrl(ref: string): string | null {
  const trimmed = ref.trim();
  if (!trimmed) return null;
  if (trimmed.includes("/embed/")) return trimmed;
  const ytMatch = trimmed.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/)([\w-]+)/i);
  if (ytMatch) return `https://www.youtube.com/embed/${ytMatch[1]}`;
  const vimeoMatch = trimmed.match(/vimeo\.com\/(\d+)/i);
  if (vimeoMatch) return `https://player.vimeo.com/video/${vimeoMatch[1]}`;
  if (/\.(mp4|webm|ogg)(\?|$)/i.test(trimmed)) return trimmed;
  return null;
}

function StructuredBlocks({ blocks }: { blocks: Array<Record<string, unknown>> }) {
  return (
    <div className="space-y-2" data-testid="fundo-lesson-blocks">
      {blocks.map((block, idx) => (
        <div key={`${String(block.type ?? "block")}-${idx}`}>
          {String(block.type) === "heading" ? (
            <h3 className="text-sm font-semibold text-gray-900">{String(block.text ?? "")}</h3>
          ) : (
            <p className="text-sm whitespace-pre-wrap text-gray-700">{String(block.text ?? "")}</p>
          )}
        </div>
      ))}
    </div>
  );
}

function VideoLesson({ refUrl, title }: { refUrl: string; title: string }) {
  const embedUrl = toEmbedUrl(refUrl);
  if (embedUrl && (embedUrl.includes("youtube.com/embed") || embedUrl.includes("vimeo.com/video"))) {
    return (
      <div className="space-y-2" data-testid="fundo-lesson-video-embed">
        <div className="aspect-video overflow-hidden rounded-lg border border-gray-200 bg-black">
          <iframe
            title={title}
            src={embedUrl}
            className="h-full w-full"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
            allowFullScreen
          />
        </div>
        <a href={refUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-xs text-teal-700 hover:underline">
          <ExternalLink className="h-3 w-3" /> Open source video
        </a>
      </div>
    );
  }
  if (embedUrl) {
    return (
      <video controls className="w-full rounded-lg border border-gray-200" data-testid="fundo-lesson-video-native">
        <source src={embedUrl} />
        Your browser does not support inline video playback.
      </video>
    );
  }
  return (
    <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900" data-testid="fundo-lesson-video-link">
      <p className="flex items-center gap-2 font-medium">
        <Play className="h-4 w-4" /> Video lesson
      </p>
      <a href={refUrl} target="_blank" rel="noreferrer" className="mt-1 inline-flex items-center gap-1 text-teal-700 hover:underline">
        Watch video <ExternalLink className="h-3 w-3" />
      </a>
    </div>
  );
}

function PracticalTaskLesson({ body, taskRef }: { body: string; taskRef: string }) {
  const steps = useMemo(() => parsePracticalSteps(body || taskRef), [body, taskRef]);
  const [checked, setChecked] = useState<Record<number, boolean>>({});
  const doneCount = steps.filter((_, idx) => checked[idx]).length;

  return (
    <div className="space-y-3" data-testid="fundo-lesson-practical">
      <p className="flex items-center gap-2 text-sm font-medium text-gray-900">
        <Wrench className="h-4 w-4 text-teal-700" /> Practical competency checklist
      </p>
      {steps.length === 0 ? (
        <p className="text-sm text-gray-600">Complete the supervised practical task described by your trainer.</p>
      ) : (
        <ul className="space-y-2">
          {steps.map((step, idx) => (
            <li key={`${idx}-${step}`}>
              <label className="flex cursor-pointer items-start gap-2 rounded border border-gray-200 bg-white p-2 text-sm text-gray-800">
                <input
                  type="checkbox"
                  checked={Boolean(checked[idx])}
                  onChange={(e) => setChecked((prev) => ({ ...prev, [idx]: e.target.checked }))}
                  className="mt-0.5"
                />
                <span>{step}</span>
              </label>
            </li>
          ))}
        </ul>
      )}
      <p className="text-xs text-gray-500">
        {steps.length > 0 ? (
          <span className="inline-flex items-center gap-1">
            {doneCount === steps.length ? <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" /> : <Circle className="h-3.5 w-3.5" />}
            {doneCount} of {steps.length} checklist items marked
          </span>
        ) : (
          "Mark lesson complete after trainer sign-off."
        )}
      </p>
    </div>
  );
}

export function FundoLessonContent({ lesson }: { lesson: LessonRecord }) {
  const contentType = String(lesson.contentType ?? "TEXT");
  const contentFormat = String(lesson.contentFormat ?? "PLAIN_TEXT");
  const body = String(lesson.contentBody ?? "");
  const ref = String(lesson.contentRef ?? "");
  const title = String(lesson.title ?? "Lesson");
  const blocksRaw = String(lesson.contentBlocksJson ?? "");
  const blocks = useMemo(() => {
    if (!blocksRaw) return [] as Array<Record<string, unknown>>;
    try {
      const parsed = JSON.parse(blocksRaw);
      return Array.isArray(parsed) ? (parsed as Array<Record<string, unknown>>) : [];
    } catch {
      return [];
    }
  }, [blocksRaw]);

  return (
    <div data-testid="fundo-lesson-content" data-content-type={contentType}>
      {contentFormat === "STRUCTURED_BLOCKS" && blocks.length > 0 ? <StructuredBlocks blocks={blocks} /> : null}
      {contentFormat !== "STRUCTURED_BLOCKS" || blocks.length === 0 ? (
        <>
          {contentType === "TEXT" ? (
            <p className="text-sm whitespace-pre-wrap text-gray-700">{body || "No text content provided."}</p>
          ) : null}
          {contentType === "DOCUMENT" ? (
            <div className="flex items-start gap-2 text-sm text-gray-700" data-testid="fundo-lesson-document">
              <FileText className="mt-0.5 h-4 w-4 text-teal-700" />
              <a className="text-teal-700 hover:underline" href={ref} target="_blank" rel="noreferrer">
                {body || ref || "Open document"}
              </a>
            </div>
          ) : null}
          {contentType === "LINK" ? (
            <a className="inline-flex items-center gap-1 text-sm text-teal-700 hover:underline" href={ref} target="_blank" rel="noreferrer" data-testid="fundo-lesson-link">
              {body || ref || "Open external resource"} <ExternalLink className="h-3.5 w-3.5" />
            </a>
          ) : null}
          {contentType === "VIDEO" ? <VideoLesson refUrl={ref || body} title={title} /> : null}
          {contentType === "PRACTICAL_TASK" ? <PracticalTaskLesson body={body} taskRef={ref} /> : null}
          {!["TEXT", "DOCUMENT", "LINK", "VIDEO", "PRACTICAL_TASK"].includes(contentType) ? (
            <p className="text-sm text-gray-700">{body || ref || "Unsupported lesson type."}</p>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
