import React from "react";

export interface WorkspaceHeroProps {
  title: string;
  subtitle?: string;
  actions?: React.ReactNode;
  watermark?: boolean;
  className?: string;
  children?: React.ReactNode;
}

export function WorkspaceHero({
  title,
  subtitle,
  actions,
  watermark = true,
  className = "",
  children,
}: WorkspaceHeroProps) {
  return (
    <section
      className={`relative overflow-hidden rounded-3xl bg-gradient-to-br from-impilo-900 via-impilo-800 to-impilo-700 px-6 py-8 text-white shadow-impilo-floating sm:px-8 ${className}`}
    >
      {watermark ? (
        <div
          className="pointer-events-none absolute inset-0 opacity-[0.12]"
          style={{
            backgroundImage: "url('/brand/mark-rgb.svg')",
            backgroundRepeat: "no-repeat",
            backgroundPosition: "right 5% bottom 10%",
            backgroundSize: "min(280px, 40vw)",
            filter: "brightness(0) invert(1)",
          }}
          aria-hidden
        />
      ) : null}
      <div className="relative z-10 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">{title}</h1>
          {subtitle ? <p className="mt-2 max-w-2xl text-sm text-white/85">{subtitle}</p> : null}
        </div>
        {actions ? <div className="flex shrink-0 flex-wrap gap-2">{actions}</div> : null}
      </div>
      {children ? <div className="relative z-10 mt-6">{children}</div> : null}
    </section>
  );
}
