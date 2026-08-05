/**
 * Gloss tokens — the PO-approved visual direction (2026-08-04), in mobile terms.
 *
 * The web half of this direction is a light-to-deep teal ramp with the colourful brand
 * mark on the light end, one gradient button language, and glass panels lifted off the
 * ramp. See `docs/design/mobile-visual-redesign-brief.md` (addendum 2026-08-04).
 *
 * Two constraints shape everything here, and both are properties of the platform rather
 * than choices:
 *
 *  1. **React Native has no CSS gradients, and no gradient library is usable here.**
 *     `expo-linear-gradient` and `expo-blur` are installed in neither app.
 *     `react-native-svg` is a dependency of **citizen-app only** — provider-app does not
 *     have it, and this package is shared by both, so importing it would break every
 *     screen in the provider app. It is a native module, so adding it is not a
 *     package.json edit: it needs an `expo prebuild` and a native rebuild that cannot be
 *     verified from this environment. The ramp is therefore painted as flat bands
 *     (`rampBands`), which needs no dependency and cannot fail to render.
 *  2. **The ramp must be anchored in dp, not percentages.** This is the web lesson
 *     carried over verbatim: a percentage ramp slides through the text when content
 *     grows, so growth must extend the dark floor instead. Bands are laid out in absolute
 *     dp from the top and the floor fills everything below the last one.
 *
 * ADDITIVE ONLY. Nothing here replaces `colors`, `glass` or `neon` — this file follows the
 * same rule `colors.ts` states for `gray`/`ui`: name what is actually rendered, never
 * silently repaint an existing surface.
 */

/**
 * The vertical ramp: near-white mint at the top so the colourful brand mark sits on its
 * natural ground, through living teal, to a deep calm floor.
 *
 * `offset` is in **dp from the top of the surface**, not a fraction — see constraint 2.
 * A surface taller than the last stop simply continues in the floor colour.
 */
export const glossRamp = [
  { offset: 0, color: "#F7FCFA" },
  { offset: 96, color: "#E7F5EE" },
  { offset: 168, color: "#8FCDBD" },
  { offset: 240, color: "#2A8B84" },
  { offset: 340, color: "#0B4A4D" },
  { offset: 460, color: "#063139" },
  { offset: 620, color: "#03222A" },
] as const;

/** The floor colour: the ground beneath the bands, and everything below the last stop. */
export const glossFloor = "#03222A";

/** Ink that stays legible on the light end of the ramp (top ~200dp). */
export const glossInkOnLight = {
  heading: "#0A3A30",
  body: "#245549",
  muted: "#4A6D62",
} as const;

/** Ink for the dark half. */
export const glossInkOnDark = {
  heading: "#FFFFFF",
  body: "rgba(226, 250, 243, 0.82)",
  muted: "rgba(226, 250, 243, 0.62)",
} as const;

/**
 * A label that must sit in the ramp's hand-over zone carries its own ground rather than
 * gambling on whichever tone the ramp happens to be at that height on that device.
 * Straight from the web pass, where the "Ask Nompilo" label needed exactly this.
 */
export const glossPill = {
  backgroundColor: "rgba(4, 45, 42, 0.35)",
  color: "#FFFFFF",
} as const;

/** The inner top highlight that makes a filled control read as lit rather than painted. */
export const glossSheen = "rgba(255, 255, 255, 0.35)";

/**
 * Structure over hue.
 *
 * Every primary action shares one *structure* — a lighter-to-base vertical gradient with an
 * inner top highlight — while each app keeps its own accent: citizen Impilo green
 * (`#009739`), provider deep teal (`#0F766E`). Derived from the passed accent so a tenant
 * accent gets the same treatment automatically, exactly as the web's `.impilo-btn-primary`
 * derives its gradient from `--primary` via `color-mix`.
 */
export function glossButtonStops(accent: string): { from: string; to: string } {
  return { from: lighten(accent, 0.22), to: accent };
}

/** Mix `amount` of white into a #rrggbb colour. Returns the input unchanged if unparseable. */
export function lighten(hex: string, amount: number): string {
  const m = /^#([0-9a-f]{6})$/i.exec(hex.trim());
  if (!m) return hex;
  const n = parseInt(m[1], 16);
  const r = (n >> 16) & 0xff;
  const g = (n >> 8) & 0xff;
  const b = n & 0xff;
  const t = Math.min(Math.max(amount, 0), 1);
  const up = (c: number) => Math.round(c + (255 - c) * t);
  return `#${((up(r) << 16) | (up(g) << 8) | up(b)).toString(16).padStart(6, "0")}`;
}

/**
 * Resolve the ramp colour at a given dp offset. Used to sample each band's colour, to
 * pick legible ink for content at a known height, and to tint a child surface to the
 * exact tone sitting behind it.
 */
export function rampColorAt(dp: number): string {
  if (!Number.isFinite(dp)) return glossRamp[0].color;
  if (dp <= glossRamp[0].offset) return glossRamp[0].color;
  const last = glossRamp[glossRamp.length - 1];
  if (dp >= last.offset) return last.color;

  for (let i = 1; i < glossRamp.length; i += 1) {
    const prev = glossRamp[i - 1];
    const next = glossRamp[i];
    if (dp <= next.offset) {
      const span = next.offset - prev.offset;
      const t = span === 0 ? 0 : (dp - prev.offset) / span;
      return mix(prev.color, next.color, t);
    }
  }
  return last.color;
}

/** Linear blend between two #rrggbb colours. */
export function mix(from: string, to: string, t: number): string {
  const a = /^#([0-9a-f]{6})$/i.exec(from.trim());
  const b = /^#([0-9a-f]{6})$/i.exec(to.trim());
  if (!a || !b) return from;
  const av = parseInt(a[1], 16);
  const bv = parseInt(b[1], 16);
  const k = Math.min(Math.max(t, 0), 1);
  const ch = (shift: number) => {
    const x = (av >> shift) & 0xff;
    const y = (bv >> shift) & 0xff;
    return Math.round(x + (y - x) * k);
  };
  return `#${((ch(16) << 16) | (ch(8) << 8) | ch(0)).toString(16).padStart(6, "0")}`;
}

/**
 * Lay the ramp out as flat bands for a platform with no gradient primitive.
 *
 * Each band is a solid step sampled at its own midpoint, so the sequence approximates the
 * ramp without any band being a colour the ramp never passes through. Anchored in dp from
 * the top — the caller paints the floor below `height` — which is what keeps content
 * growth from sliding the ramp (see `glossRamp`).
 */
export function rampBands(
  height: number,
  count: number,
  /**
   * Where in the ramp this surface begins, in dp.
   *
   * The light top of the ramp is LOAD-BEARING: it exists so the colourful brand mark can
   * sit on its natural ground. A surface whose content is white ink must not get it —
   * that would both break contrast and reproduce the decorative dead band the PO rejected
   * on web (Wave 1). Such surfaces start mid-ramp instead and still get real depth.
   */
  startAt = 0,
): Array<{ top: number; height: number; color: string }> {
  const h = Number.isFinite(height) && height > 0 ? height : 0;
  const n = Number.isFinite(count) ? Math.floor(count) : 0;
  const from = Number.isFinite(startAt) && startAt > 0 ? startAt : 0;
  if (h === 0 || n <= 0) return [];

  const step = h / n;
  const bands: Array<{ top: number; height: number; color: string }> = [];
  for (let i = 0; i < n; i += 1) {
    const top = i * step;
    bands.push({
      top,
      // Overlap by a hair so sub-pixel rounding cannot leave seams between bands.
      height: step + 1,
      color: rampColorAt(from + top + step / 2),
    });
  }
  return bands;
}

export const gloss = {
  ramp: glossRamp,
  floor: glossFloor,
  inkOnLight: glossInkOnLight,
  inkOnDark: glossInkOnDark,
  pill: glossPill,
  sheen: glossSheen,
} as const;
