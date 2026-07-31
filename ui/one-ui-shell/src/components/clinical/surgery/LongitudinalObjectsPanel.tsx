"use client";

/**
 * Drains, stomas and wounds on a surgical episode (SB-2). Place / remove / revise.
 * Implants are deliberately absent — inventory owns those. A failed list ≠ empty.
 */

import { useState } from "react";
import {
  useLongitudinalObjects,
  usePlaceLongitudinalObject,
  useRemoveLongitudinalObject,
  useReviseLongitudinalObject,
  type LongitudinalObjectKind,
} from "@/hooks/queries/useSurgeryEpisodes";

const KINDS: LongitudinalObjectKind[] = ["DRAIN", "STOMA", "WOUND"];

export function LongitudinalObjectsPanel({ episodeId }: { episodeId: string }) {
  const listQ = useLongitudinalObjects(episodeId);
  const place = usePlaceLongitudinalObject(episodeId);
  const remove = useRemoveLongitudinalObject(episodeId);
  const revise = useReviseLongitudinalObject(episodeId);

  const [kind, setKind] = useState<LongitudinalObjectKind | "">("");
  const [site, setSite] = useState("");
  const [indication, setIndication] = useState("");
  const [removalReason, setRemovalReason] = useState("");

  const writeFailed = place.isError || remove.isError || revise.isError;

  const resetPlace = () => {
    setKind("");
    setSite("");
    setIndication("");
  };

  return (
    <div
      className="mt-4 rounded-lg border border-gray-100 p-3"
      data-testid="surgery-longitudinal-panel"
    >
      <h4 className="text-xs font-semibold uppercase text-muted-foreground">
        Drains, stomas and wounds (course of care)
      </h4>

      {listQ.isLoading ? (
        <p className="mt-2 text-sm text-muted-foreground">Loading objects…</p>
      ) : listQ.isError ? (
        <p
          className="mt-2 text-sm text-danger"
          role="alert"
          data-testid="surgery-longitudinal-unavailable"
        >
          Could not read drains, stomas and wounds. This is not the same as there being none.
        </p>
      ) : listQ.data && listQ.data.length === 0 ? (
        <p className="mt-2 text-sm text-muted-foreground" data-testid="surgery-longitudinal-empty">
          No drains, stomas or wounds recorded on this episode.
        </p>
      ) : (
        <ul className="mt-2 space-y-1" data-testid="surgery-longitudinal-list">
          {listQ.data?.map((obj) => (
            <li key={obj.id} className="flex flex-wrap items-center gap-2 text-sm">
              <span className="rounded bg-gray-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-gray-600">
                {obj.kind}
              </span>
              <span className="rounded bg-gray-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-gray-600">
                {obj.status}
              </span>
              <span className="font-medium">{obj.site}</span>
              <span className="text-xs text-muted-foreground">{obj.indication}</span>
              {obj.status === "ACTIVE" && (
                <>
                  <button
                    type="button"
                    disabled={remove.isPending}
                    onClick={() =>
                      remove.mutate({
                        objectId: obj.id,
                        removalReason: removalReason.trim() || null,
                      })
                    }
                    className="rounded-md border border-gray-200 px-2 py-0.5 text-xs disabled:opacity-50"
                    data-testid="surgery-longitudinal-remove"
                  >
                    Remove
                  </button>
                  <button
                    type="button"
                    disabled={revise.isPending}
                    onClick={() => revise.mutate({ objectId: obj.id })}
                    className="rounded-md border border-gray-200 px-2 py-0.5 text-xs disabled:opacity-50"
                    data-testid="surgery-longitudinal-revise"
                  >
                    Revise
                  </button>
                </>
              )}
            </li>
          ))}
        </ul>
      )}

      <div className="mt-3 flex flex-wrap items-end gap-2">
        <label className="block text-xs">
          <span className="font-semibold uppercase text-muted-foreground">Kind</span>
          <select
            value={kind}
            onChange={(e) => setKind(e.target.value as LongitudinalObjectKind | "")}
            className="mt-0.5 block rounded-md border border-gray-200 px-2 py-1.5 text-sm"
            data-testid="surgery-longitudinal-kind"
          >
            <option value="">DRAIN / STOMA / WOUND…</option>
            {KINDS.map((k) => (
              <option key={k} value={k}>
                {k}
              </option>
            ))}
          </select>
        </label>
        <label className="block text-xs min-w-[120px]">
          <span className="font-semibold uppercase text-muted-foreground">Site</span>
          <input
            type="text"
            value={site}
            onChange={(e) => setSite(e.target.value)}
            className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
            data-testid="surgery-longitudinal-site"
          />
        </label>
        <label className="block flex-1 text-xs min-w-[140px]">
          <span className="font-semibold uppercase text-muted-foreground">Indication</span>
          <input
            type="text"
            value={indication}
            onChange={(e) => setIndication(e.target.value)}
            className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
            data-testid="surgery-longitudinal-indication"
          />
        </label>
        <button
          type="button"
          disabled={!kind || !site.trim() || !indication.trim() || place.isPending}
          onClick={() =>
            place.mutate(
              {
                kind: kind as LongitudinalObjectKind,
                site: site.trim(),
                indication: indication.trim(),
              },
              { onSuccess: resetPlace },
            )
          }
          className="rounded-md border border-gray-200 px-3 py-1.5 text-sm disabled:opacity-50"
          data-testid="surgery-longitudinal-place"
        >
          {place.isPending ? "Placing…" : "Place"}
        </button>
      </div>
      <label className="mt-2 block text-xs max-w-md">
        <span className="font-semibold uppercase text-muted-foreground">
          Removal reason (optional, used on Remove)
        </span>
        <input
          type="text"
          value={removalReason}
          onChange={(e) => setRemovalReason(e.target.value)}
          className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
          data-testid="surgery-longitudinal-removal-reason"
        />
      </label>
      {writeFailed && (
        <p
          className="mt-2 text-sm text-danger"
          role="alert"
          data-testid="surgery-longitudinal-write-failed"
        >
          That change was NOT saved — the objects on this episode are unchanged.
        </p>
      )}
    </div>
  );
}
