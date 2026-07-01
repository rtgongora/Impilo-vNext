# Brand favicon & app-icon assets (one-ui-shell)

Canonical frontend: `ui/one-ui-shell`. All platform brand assets live under
`ui/one-ui-shell/public/brand/` and are served from `/brand/...`.

## Current state (what is wired)

- **Browser tab / favicon**: `/brand/mark-rgb.svg` (+ `/favicon.svg` fallback),
  declared in `src/app/layout.tsx` `metadata.icons`. SVG favicons render in all
  current evergreen browsers (Chrome, Edge, Firefox, Safari 16+).
- **PWA manifest**: `src/app/manifest.ts` → served at `/manifest.webmanifest`,
  linked from `metadata.manifest`. Icons reference the scalable SVG mark.
- **Theme colour**: `#039748` (Impilo green) via `viewport.themeColor`.
- **In-app brand**: `ImpiloBrandLogo` (`src/components/brand/ImpiloBrandLogo.tsx`)
  renders the SVG wordmark/mark in the taskbar, sidebar, utility strip and auth
  shell. Service (module) logos are separate — see `serviceBranding.ts`.

## Known gap — raster icons (PNG / ICO)

The following raster assets are **not** generated because this VM has **no SVG
rasteriser** (`rsvg-convert`, `inkscape`, `resvg`, `cairosvg`) and no
ImageMagick, and no `pip` to install one. Upscaling would produce blurry icons,
so they were intentionally omitted rather than shipped broken:

| Asset | Purpose | Status |
|-------|---------|--------|
| `favicon.ico` | Legacy browsers / bookmarks | pending rasteriser |
| `apple-touch-icon.png` (180×180) | iOS home screen (iOS ignores SVG) | pending rasteriser |
| `icon-192.png`, `icon-512.png` (+ maskable) | PWA install / Android | pending rasteriser |

### How to generate them (when tooling is available)

Source of truth: `ui/one-ui-shell/public/brand/mark-rgb.svg`.

```bash
cd ui/one-ui-shell/public
# with librsvg (rsvg-convert) + ImageMagick, or sharp-cli
for s in 32 48 180 192 512; do
  rsvg-convert -w $s -h $s brand/mark-rgb.svg -o brand/icon-$s.png
done
cp brand/icon-180.png apple-touch-icon.png
cp brand/icon-192.png brand/icon-192.png
cp brand/icon-512.png brand/icon-512.png
magick brand/icon-32.png brand/icon-48.png favicon.ico
```

Then add the PNG entries to `manifest.ts` (`icon-192`/`icon-512`, `purpose:
"maskable"`) and `layout.tsx` `metadata.icons.apple` + `icon` (`favicon.ico`).
