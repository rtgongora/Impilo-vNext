"use client";

/**
 * ImpiloLogo — Brand logomark and wordmark for the Impilo Health OS.
 *
 * The logomark is a stylised shield with a health cross,
 * rendered in the Impilo brand green (#1F7A3A).
 */

interface ImpiloLogoProps {
  /** Show wordmark text alongside the logomark */
  variant?: "mark" | "full";
  /** Height in pixels (width scales proportionally) */
  size?: number;
  /** Override the fill colour */
  className?: string;
}

export function ImpiloLogo({
  variant = "full",
  size = 32,
  className,
}: ImpiloLogoProps) {
  return (
    <span className={`inline-flex items-center gap-2 ${className ?? ""}`}>
      {/* Logomark — shield + health cross */}
      <svg
        width={size}
        height={size}
        viewBox="0 0 48 48"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        aria-hidden="true"
      >
        {/* Shield body */}
        <path
          d="M24 4L6 12v12c0 11.1 7.68 21.48 18 24 10.32-2.52 18-12.9 18-24V12L24 4z"
          fill="currentColor"
          className="text-[#1F7A3A]"
        />
        {/* Health cross (white) */}
        <rect x="20" y="14" width="8" height="22" rx="2" fill="white" />
        <rect x="13" y="21" width="22" height="8" rx="2" fill="white" />
      </svg>

      {variant === "full" && (
        <span
          className="font-bold tracking-tight text-[#1F7A3A]"
          style={{ fontSize: size * 0.65 }}
        >
          Impilo
        </span>
      )}
    </span>
  );
}
