"use client";

/**
 * Afro-futurist hero backdrop for auth landing — woven pathways, care routes,
 * digital trust linework. Decorative only; text sits in a separate content layer.
 */

export function AuthHeroAfroFuturistBackground() {
  return (
    <div className="auth-hero-afro-bg pointer-events-none absolute inset-0" aria-hidden>
      {/* Deep sovereign gradient — forest, earth, warm clay */}
      <div className="absolute inset-0 bg-gradient-to-br from-[#041a0f] via-[#0b3d24] via-40% to-[#1a1510]" />
      <div className="absolute inset-0 bg-gradient-to-tr from-[#009739]/25 via-transparent to-[#c4a574]/10" />
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_80%_60%_at_20%_80%,rgba(252,227,0,0.08),transparent_55%)]" />
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_70%_50%_at_85%_15%,rgba(0,151,57,0.12),transparent_50%)]" />

      {/* Linework canvas */}
      <svg
        className="auth-hero-afro-svg absolute inset-0 h-full w-full"
        viewBox="0 0 800 900"
        preserveAspectRatio="xMidYMid slice"
        xmlns="http://www.w3.org/2000/svg"
      >
        <defs>
          <linearGradient id="authHeroRouteGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#009739" stopOpacity="0.35" />
            <stop offset="50%" stopColor="#fce300" stopOpacity="0.2" />
            <stop offset="100%" stopColor="#009739" stopOpacity="0.3" />
          </linearGradient>
          <linearGradient id="authHeroPulseGrad" x1="0%" y1="50%" x2="100%" y2="50%">
            <stop offset="0%" stopColor="#fce300" stopOpacity="0" />
            <stop offset="50%" stopColor="#fce300" stopOpacity="0.45" />
            <stop offset="100%" stopColor="#fce300" stopOpacity="0" />
          </linearGradient>
          <filter id="authHeroGlow" x="-20%" y="-20%" width="140%" height="140%">
            <feGaussianBlur stdDeviation="2" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        {/* Woven geometric grid — subtle, not decorative wallpaper */}
        <g stroke="rgba(255,255,255,0.06)" strokeWidth="0.6" fill="none">
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

        {/* Village-to-facility care routes */}
        <g stroke="url(#authHeroRouteGrad)" strokeWidth="1.2" fill="none" filter="url(#authHeroGlow)">
          <path
            className="auth-hero-route-line"
            d="M 120 720 Q 280 580 420 480 T 680 220"
          />
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
        <g stroke="rgba(0,151,57,0.35)" strokeWidth="0.8" fill="none">
          <path className="auth-hero-circuit-line" d="M 520 140 L 580 140 L 580 200 L 640 200" />
          <path className="auth-hero-circuit-line" d="M 200 600 L 260 600 L 260 540 L 320 540" />
          <circle cx="640" cy="200" r="3" fill="rgba(252,227,0,0.5)" />
          <circle cx="320" cy="540" r="3" fill="rgba(252,227,0,0.4)" />
        </g>

        {/* Life-flow / heartbeat rhythm */}
        <path
          className="auth-hero-heartbeat"
          d="M 60 420 L 140 420 L 165 380 L 190 460 L 215 400 L 240 420 L 740 420"
          stroke="url(#authHeroPulseGrad)"
          strokeWidth="1.5"
          fill="none"
          strokeLinecap="round"
          strokeLinejoin="round"
        />

        {/* Connection nodes — community to facility */}
        <g fill="rgba(255,255,255,0.12)">
          <circle cx="120" cy="720" r="6" className="auth-hero-node-pulse" />
          <circle cx="680" cy="220" r="9" className="auth-hero-node-pulse" style={{ animationDelay: "1s" }} />
          <circle cx="420" cy="480" r="5" className="auth-hero-node-pulse" style={{ animationDelay: "0.5s" }} />
        </g>
      </svg>

      {/* Soft ambient orbs */}
      <div className="auth-hero-orb auth-hero-orb-a absolute -left-16 top-24 h-72 w-72 rounded-full bg-[#009739]/20 blur-3xl" />
      <div className="auth-hero-orb auth-hero-orb-b absolute -right-24 bottom-32 h-96 w-96 rounded-full bg-[#c4a574]/15 blur-3xl" />

      {/* Readability scrim — keeps copy legible */}
      <div className="absolute inset-0 bg-gradient-to-t from-[#041a0f]/85 via-[#041a0f]/25 to-[#041a0f]/40" />
    </div>
  );
}
