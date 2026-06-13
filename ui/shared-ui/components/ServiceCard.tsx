import React from "react";

export interface ServiceCardProps {
  title: string;
  description?: string;
  href?: string;
  icon?: React.ReactNode;
  status?: React.ReactNode;
  accentColor?: "green" | "yellow" | "red";
  className?: string;
  onClick?: () => void;
}

export function ServiceCard({
  title,
  description,
  href,
  icon,
  status,
  accentColor = "green",
  className = "",
  onClick,
}: ServiceCardProps) {
  const accentTop =
    accentColor === "green"
      ? "border-t-primary"
      : accentColor === "yellow"
        ? "border-t-warning"
        : "border-t-danger";

  const iconBg =
    accentColor === "green"
      ? "bg-primary-soft text-primary"
      : accentColor === "yellow"
        ? "bg-warning-soft text-warning-foreground"
        : "bg-danger-soft text-danger";

  const inner = (
    <>
      <div className="flex items-start gap-3">
        {icon ? (
          <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${iconBg}`}>
            {icon}
          </div>
        ) : null}
        <div className="min-w-0 flex-1">
          <h3 className="text-sm font-semibold text-foreground group-hover:text-primary">{title}</h3>
          {description ? (
            <p className="mt-1 text-xs text-muted-foreground line-clamp-2">{description}</p>
          ) : null}
        </div>
      </div>
      {status ? <div className="mt-3">{status}</div> : null}
    </>
  );

  const cardClass = `group flex flex-col rounded-2xl border border-border bg-card p-4 shadow-impilo-card
    border-t-4 ${accentTop} transition hover:border-primary/30 hover:shadow-impilo-floating ${className}`;

  if (href) {
    return (
      <a href={href} className={cardClass}>
        {inner}
      </a>
    );
  }

  if (onClick) {
    return (
      <button type="button" onClick={onClick} className={`${cardClass} text-left`}>
        {inner}
      </button>
    );
  }

  return <article className={cardClass}>{inner}</article>;
}
