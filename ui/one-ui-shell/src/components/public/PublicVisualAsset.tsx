"use client";

import type { ReactNode } from "react";
import { useEffect, useState } from "react";
import { ImageOff } from "lucide-react";

type NetworkInformation = {
  saveData?: boolean;
  effectiveType?: string;
};

function prefersLowData(): boolean {
  if (typeof document === "undefined" || typeof navigator === "undefined") return false;
  const connection = (navigator as Navigator & { connection?: NetworkInformation }).connection;
  return (
    document.documentElement.classList.contains("impilo-low-bandwidth") ||
    connection?.saveData === true ||
    connection?.effectiveType === "slow-2g"
  );
}

/**
 * Responsive public-experience image with an honest, useful no-image state.
 *
 * The fallback remains attractive and fully readable when an image fails, the user
 * enables Impilo's low-bandwidth mode, or the browser reports data-saver mode.
 */
export function PublicVisualAsset({
  src,
  compactSrc,
  alt,
  className = "",
  objectPosition = "center",
  priority = false,
  fallback,
}: {
  src: string;
  compactSrc?: string;
  alt: string;
  className?: string;
  objectPosition?: string;
  priority?: boolean;
  fallback?: ReactNode;
}) {
  const [failed, setFailed] = useState(false);
  const [lowData, setLowData] = useState(false);

  useEffect(() => {
    setLowData(prefersLowData());
  }, []);

  const showFallback = failed || lowData;

  return (
    <div
      className={`relative isolate overflow-hidden bg-gradient-to-br from-emerald-950 via-emerald-900 to-teal-800 ${className}`}
      data-image-state={showFallback ? "fallback" : "image"}
    >
      {!showFallback && (
        <picture>
          {compactSrc && <source media="(max-width: 767px)" srcSet={compactSrc} />}
          <img
            src={src}
            alt={alt}
            width={1920}
            height={1080}
            loading={priority ? "eager" : "lazy"}
            decoding="async"
            onError={() => setFailed(true)}
            className="absolute inset-0 h-full w-full object-cover"
            style={{ objectPosition }}
          />
        </picture>
      )}

      <div
        className={`absolute inset-0 ${
          showFallback
            ? "bg-[radial-gradient(circle_at_18%_18%,rgba(52,211,153,.28),transparent_38%),radial-gradient(circle_at_86%_80%,rgba(250,204,21,.12),transparent_32%)]"
            : "bg-gradient-to-t from-emerald-950/90 via-emerald-950/30 to-slate-950/10"
        }`}
        aria-hidden
      />

      <div className="relative z-10 flex h-full min-h-56 flex-col justify-end p-5 text-white sm:min-h-72 sm:p-7">
        {showFallback && (
          <span className="mb-auto inline-flex w-fit items-center gap-2 rounded-full bg-white/10 px-3 py-1.5 text-xs font-medium ring-1 ring-white/20">
            <ImageOff className="h-3.5 w-3.5" aria-hidden />
            Low-data view
          </span>
        )}
        {fallback}
      </div>
    </div>
  );
}
