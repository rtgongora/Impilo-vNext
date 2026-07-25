# Public visual asset manifest

Date: 2026-07-25

The public experience uses visual assets as optional context. Primary actions, headings
and guidance render before and independently of photography.

## Approved implementation set

| Asset family | Context | Crops | Loading | Status |
|---|---|---|---|---|
| `hero-clinic-nurse` | health worker and facility care | 1920 and 960 WebP | eager only in the first living-canvas viewport | Ministry programme photo; release confirmation required before production |
| `hero-register-digital` | digital registration and facility work | 1920 and 960 WebP | lazy | Ministry programme photo; release confirmation required before production |
| `hero-data-capture` | health information and diagnostics workflow | 1920 and 960 WebP | lazy | Ministry programme photo; release confirmation required before production |
| `hero-fibre-install` | health engineering and digital infrastructure | 1920 and 960 WebP | lazy | Ministry programme photo; release confirmation required before production |
| `hero-app-demo` | citizen digital access | 1920 and 960 WebP | lazy | project product image |
| `hero-telehealth` | telemedicine | 1920 and 960 WebP | lazy | licence unverified; not approved for production publication |

Exact source filenames and provenance are recorded beside the derivatives in
`ui/one-ui-shell/public/experience/hero/ATTRIBUTION.md`.

## Rendering contract

`PublicVisualAsset` owns responsive crops, native image dimensions, lazy/eager loading,
safe object positioning, image-error recovery and low-data behaviour. It switches to a
branded text surface when:

- the image request fails;
- the browser reports data-saver or `slow-2g`; or
- the user enables Impilo low-bandwidth mode.

Every use must provide meaningful alt text unless the image is purely decorative. A page
must remain fully usable with the visual blocked.

## Publication controls and library gaps

Images of identifiable people are excluded unless the Ministry release is confirmed.
The previous site's unlicensed stock imagery and a community-clinic photograph with
unclear patient consent are not used.

The repository does not yet contain cleared assets for every desired population and
programme category (including maternal/newborn care, young people, older persons,
disability, mental wellbeing and rural family care). Until consent/licensing is supplied,
the implementation uses icons, maps, real product content and the no-image treatment
rather than generating or implying real patients or government events. New approved
assets must be added here with source, consent/licence, alt text, crop and compression
records before use.
