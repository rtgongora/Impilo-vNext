"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { FundoStudioWorkspace } from "@/components/learning/FundoStudioWorkspace";
import { useCreateMediaAsset, useFundoMediaAssets } from "@/hooks/queries/useFundoStudio";

export default function FundoStudioMediaPage() {
  const { data, refetch } = useFundoMediaAssets();
  const createMedia = useCreateMediaAsset();
  const [recordingUrl, setRecordingUrl] = useState<string>("");
  const [recordingActive, setRecordingActive] = useState(false);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<BlobPart[]>([]);

  async function startRecording() {
    if (!navigator.mediaDevices || !("MediaRecorder" in window)) return;
    const stream = await navigator.mediaDevices.getDisplayMedia({ video: true, audio: true });
    const recorder = new MediaRecorder(stream);
    recorderRef.current = recorder;
    chunksRef.current = [];
    recorder.ondataavailable = (event) => chunksRef.current.push(event.data);
    recorder.onstop = () => {
      const blob = new Blob(chunksRef.current, { type: "video/webm" });
      setRecordingUrl(URL.createObjectURL(blob));
      setRecordingActive(false);
      stream.getTracks().forEach((t) => t.stop());
    };
    recorder.start();
    setRecordingActive(true);
  }

  function stopRecording() {
    recorderRef.current?.stop();
  }

  async function saveRecordingDraft() {
    await createMedia.mutateAsync({
      title: `Screen recording ${new Date().toISOString()}`,
      mediaType: "VIDEO",
      status: "DRAFT",
      recordingType: "SCREEN_RECORDING",
      metadata: { source: "browser-media-recorder", localOnly: true },
    });
    void refetch();
  }

  const items = ((data?.data as { items?: Array<{ id: string; title: string; mediaType?: string; status?: string }> } | undefined)?.items ?? []);

  return (
    <FundoStudioWorkspace title="Studio Media" subtitle="Manage uploads, recording metadata, voiceover drafts and media-to-lesson linking.">
      <div className="rounded-lg border border-gray-200 bg-white p-4">
        <p className="text-sm font-semibold text-gray-900">Screen recording foundation</p>
        <p className="mt-1 text-xs text-gray-600">When storage adapters are unavailable, recording remains local preview + draft metadata.</p>
        <div className="mt-3 flex flex-wrap gap-2">
          <button onClick={startRecording} disabled={recordingActive} className="rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700 disabled:opacity-60">Start recording</button>
          <button onClick={stopRecording} disabled={!recordingActive} className="rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700 disabled:opacity-60">Stop recording</button>
          <button onClick={saveRecordingDraft} disabled={!recordingUrl || createMedia.isPending} className="rounded bg-teal-600 px-3 py-1.5 text-sm text-white disabled:opacity-60">Save metadata draft</button>
        </div>
        {recordingUrl ? <video className="mt-3 max-h-64 w-full rounded border border-gray-200" src={recordingUrl} controls /> : null}
        <div className="mt-3 flex gap-2 text-xs">
          <Link href="/learning/studio/media/recordings" className="rounded border border-gray-300 px-2 py-1 text-gray-700">Recordings</Link>
          <Link href="/learning/studio/media/scripts" className="rounded border border-gray-300 px-2 py-1 text-gray-700">Scripts</Link>
          <Link href="/learning/studio/media/voiceovers" className="rounded border border-gray-300 px-2 py-1 text-gray-700">Voiceovers</Link>
        </div>
      </div>
      <div className="mt-4 rounded-lg border border-gray-200 bg-white p-4">
        <p className="text-sm font-semibold text-gray-900">Media assets</p>
        <ul className="mt-2 space-y-2 text-sm">
          {items.map((m) => (
            <li key={m.id} className="flex items-center justify-between rounded border border-gray-100 px-3 py-2">
              <span>{m.title} · {m.mediaType ?? "VIDEO"} · {m.status ?? "DRAFT"}</span>
              <Link href={`/learning/studio/media/${m.id}`} className="text-teal-700 hover:underline">Open</Link>
            </li>
          ))}
        </ul>
      </div>
    </FundoStudioWorkspace>
  );
}
