"use client";

/**
 * Afro-futurist hero backdrop for auth landing — woven pathways, bead rhythms,
 * care routes, and digital trust linework. Decorative only; copy sits above.
 */

export function AuthHeroAfroFuturistBackground() {
  return (
    <div className="auth-hero-afro-bg pointer-events-none absolute inset-0" aria-hidden>
      {/* Deep sovereign gradient — forest, charcoal, clay */}
      <div className="absolute inset-0 bg-gradient-to-br from-[#0B0F0E] via-[#0F2D23] via-45% to-[#1a1510]" />
      <div className="absolute inset-0 bg-gradient-to-tr from-[#22C55E]/12 via-transparent to-[#8B6E4D]/14" />
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_80%_60%_at_18%_82%,rgba(212,175,55,0.1),transparent_55%)]" />
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_70%_50%_at_88%_12%,rgba(34,197,94,0.14),transparent_50%)]" />

      {/* Linework canvas */}
      <svg
        className="auth-hero-afro-svg absolute inset-0 h-full w-full"
        viewBox="0 0 800 900"
        preserveAspectRatio="xMidYMid slice"
        xmlns="http://www.w3.org/2000/svg"
      >
        <defs>
          <linearGradient id="authHeroRouteGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#22C55E" stopOpacity="0.4" />
            <stop offset="50%" stopColor="#D4AF37" stopOpacity="0.25" />
            <stop offset="100%" stopColor="#22C55E" stopOpacity="0.35" />
          </linearGradient>
          <linearGradient id="authHeroPulseGrad" x1="0%" y1="50%" x2="100%" y2="50%">
            <stop offset="0%" stopColor="#D4AF37" stopOpacity="0" />
            <stop offset="50%" stopColor="#D4AF37" stopOpacity="0.5" />
            <stop offset="100%" stopColor="#D4AF37" stopOpacity="0" />
          </linearGradient>
          <pattern id="authHeroBeadGrid" width="24" height="24" patternUnits="userSpaceOnUse">
            <circle cx="12" cy="12" r="1.2" fill="rgba(212,175,55,0.18)" />
            <circle cx="0" cy="0" r="0.8" fill="rgba(34,197,94,0.12)" />
            <circle cx="24" cy="24" r="0.8" fill="rgba(34,197,94,0.12)" />
          </pattern>
          <pattern id="authHeroBasketWeave" width="40" height="40" patternUnits="userSpaceOnUse">
            <path
              d="M0 20 Q10 10 20 20 T40 20 M20 0 Q10 10 20 20 T20 40"
              stroke="rgba(255,255,255,0.05)"
              strokeWidth="0.7"
              fill="none"
            />
          </pattern>
          <filter id="authHeroGlow" x="-20%" y="-20%" width="140%" height="140%">
            <feGaussianBlur stdDeviation="2" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        {/* Bead rhythm field */}
        <rect width="800" height="900" fill="url(#authHeroBeadGrid)" opacity="0.85" />
        <rect width="800" height="900" fill="url(#authHeroBasketWeave)" opacity="0.9" />

        {/* Woven geometric grid */}
        <g stroke="rgba(255,255,255,0.07)" strokeWidth="0.65" fill="none">
          {[0, 1, 2, 3, 4, 5, 6].map((i) => (
            <path
              key={`w-${i}`}
              d={`M ${80 + i * 100} 0 L ${180 + i * 100} 900`}
              className="auth-hero-weave-line"
              style={{ animationDelay: `${i * 0.4}s` }}
            />
          ))}
          {[0, 1, 2, 3, 4, 5, 6, 7].map((i) => (
            <path
              key={`h-${i}`}
              d={`M 0 ${60 + i * 110} L 800 ${160 + i * 110}`}
              className="auth-hero-weave-line"
              style={{ animationDelay: `${i * 0.35}s` }}
            />
          ))}
        </g>

        {/* Diamond lattice — Afro-geometric accent */}
        <g stroke="rgba(212,175,55,0.12)" strokeWidth="0.5" fill="none">
          {[0, 1, 2, 3, 4].map((row) =>
            [0, 1, 2, 3, 4, 5].map((col) => {
              const cx = 80 + col * 120 + (row % 2) * 60;
              const cy = 120 + row * 140;
              return (
                <path
                  key={`d-${row}-${col}`}
                  d={`M ${cx} ${cy - 18} L ${cx + 18} ${cy} L ${cx} ${cy + 18} L ${cx - 18} ${cy} Z`}
                />
              );
            }),
          )}
        </g>

        {/* Village-to-facility care routes */}
        <g stroke="url(#authHeroRouteGrad)" strokeWidth="1.3" fill="none" filter="url(#authHeroGlow)">
          <path className="auth-hero-route-line" d="M 120 720 Q 280 580 420 480 T 680 220" />
          <path
            className="auth-hero-route-line auth-hero-route-line-delayed"
            d="M 80 640 Q 240 520 380 400 T 620 180"
          />
          <path
            className="auth-hero-route-line auth-hero-route-line-slow"
            d="M 160 780 Q 320 660 500 520 T 720 320"
          />
        </g>

        {/* Circuit / data connectors */}
        <g stroke="rgba(34,197,94,0.38)" strokeWidth="0.85" fill="none">
          <path className="auth-hero-circuit-line" d="M 520 140 L 580 140 L 580 200 L 640 200" />
          <path className="auth-hero-circuit-line" d="M 200 600 L 260 600 L 260 540 L 320 540" />
          <path className="auth-hero-circuit-line" d="M 640 680 L 700 680 L 700 620 L 760 620" />
          <circle cx="640" cy="200" r="3.5" fill="rgba(212,175,55,0.55)" />
          <circle cx="320" cy="540" r="3" fill="rgba(212,175,55,0.45)" />
          <circle cx="760" cy="620" r="2.5" fill="rgba(34,197,94,0.5)" />
        </g>

        {/* Life-flow / heartbeat rhythm */}
        <path
          className="auth-hero-heartbeat"
          d="M 60 420 L 140 420 L 165 380 L 190 460 L 215 400 L 240 420 L 740 420"
          stroke="url(#authHeroPulseGrad)"
          strokeWidth="1.6"
          fill="none"
          strokeLinecap="round"
          strokeLinejoin="round"
        />

        {/* Connection nodes */}
        <g fill="rgba(255,255,255,0.14)">
          <circle cx="120" cy="720" r="6" className="auth-hero-node-pulse" />
          <circle cx="680" cy="220" r="9" className="auth-hero-node-pulse" style={{ animationDelay: "1s" }} />
          <circle cx="420" cy="480" r="5" className="auth-hero-node-pulse" style={{ animationDelay: "0.5s" }} />
        </g>
      </svg>

      {/* Soft ambient orbs */}
      <div className="auth-hero-orb auth-hero-orb-a absolute -left-16 top-24 h-72 w-72 rounded-full bg-[#22C55E]/18 blur-3xl" />
      <div className="auth-hero-orb auth-hero-orb-b absolute -right-24 bottom-32 h-96 w-96 rounded-full bg-[#8B6E4D]/18 blur-3xl" />

      {/* Readability scrim — keeps copy legible, quieter behind headline zone */}
      <div className="absolute inset-0 bg-gradient-to-t from-[#0B0F0E]/90 via-[#0F2D23]/30 to-[#0B0F0E]/35" />
      <div className="absolute inset-y-0 left-0 w-[58%] bg-gradient-to-r from-[#0B0F0E]/55 via-[#0F2D23]/20 to-transparent" />
    </div>
  );
}
