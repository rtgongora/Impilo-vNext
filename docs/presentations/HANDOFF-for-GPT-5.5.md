# Handoff: "Introduction to Impilo vNext" deck — instructions for GPT 5.5

You (GPT 5.5) are being asked to access, present, or edit a PowerPoint deck that was
already built. Everything you need is in this folder. Read this file top to bottom first.

## 1. Where the files are

All paths are absolute, on the machine this repo lives on
(`/opt/impilo/repos/Impilo-vNext`). This is a **remote Linux host** the user reaches
through Cursor (VS Code Remote). There is no browser "download" button — see §5.

| File | What it is |
|------|-----------|
| `docs/presentations/Introduction-to-Impilo-vNext.pptx` | **The finished deck** — 17 slides, 16:9, speaker notes on every slide. This is the deliverable. |
| `docs/presentations/Introduction-to-Impilo-vNext.md` | The slide-by-slide source script (titles, on-screen bullets, full speaker notes). Human-readable; edit this if you only need the narrative. |
| `docs/presentations/Introduction-to-Impilo-vNext.build.js` | The **Node/pptxgenjs generator** that produces the `.pptx`. Edit this to change the actual slides, then re-run it (see §3). |
| `docs/presentations/HANDOFF-for-GPT-5.5.md` | This file. |

The `.pptx` is already world-readable (`-rw-rw-r--`). No permission changes needed.

## 2. Just opening / reading the deck

- To read the text + speaker notes without any tooling, open the `.md` file, or parse
  the `.pptx` (it is a ZIP of XML): `unzip -o Introduction-to-Impilo-vNext.pptx -d unz`
  then read `unz/ppt/slides/slideN.xml` and `unz/ppt/notesSlides/*.xml`.
- The deck's speaker notes ARE the presenter script. Slide N in the `.pptx` == section
  "SLIDE N" in the `.md`.

## 3. Regenerating or editing the deck (the real slides)

The `.pptx` is generated code, not hand-drawn. To change slides, edit
`Introduction-to-Impilo-vNext.build.js` and re-run it.

**Dependencies are pinned** in `package.json` + `package-lock.json` in this folder
(Node 20 is present on this host; there is NO global npm write access and NO
pip/LibreOffice). Reproducible build — run it right here:

```bash
cd /opt/impilo/repos/Impilo-vNext/docs/presentations
npm ci                 # exact pinned versions from package-lock.json (node_modules is gitignored)
npm run build          # == node Introduction-to-Impilo-vNext.build.js -> writes the .pptx in place
```

(First time on a machine without the lockfile you can use `npm install` instead of
`npm ci`. The pinned versions are pptxgenjs 4.0.1, react/react-dom 19.2.7,
react-icons 5.7.0, sharp 0.35.3.)

Notes:
- The script is self-contained (no external assets — icons are rasterized at runtime via
  `react-icons` + `sharp`; no image files to ship).
- Optional: after `node build.js`, pptxgenjs writes an uncompressed ZIP. To shrink it,
  recompress by repacking the ZIP (any `zip -r` round-trip works). Not required for
  correctness.
- **You cannot render slides to images on this host** (no LibreOffice, no pip/python-pptx).
  Do content QA by parsing the slide XML (§2). Do layout QA by reviewing coordinates in
  the script. If you have a renderer elsewhere, convert to PDF with LibreOffice
  (`soffice --headless --convert-to pdf`) then `pdftoppm -jpeg`.

## 4. Design facts (so edits stay consistent)

- Layout: `LAYOUT_WIDE` = 13.33in × 7.5in. Margin `M = 0.7in`.
- Palette (hex, no `#`): DARK `0A3B37`, TEAL `0D8577`, SEA `14B8A6`, MINT `5EEAD4`,
  AMBER `E1A21A`, CORAL `DB5A34`, INK `12312E`, SLATE `5B6E6B`, BG light `F5F9F8`.
  Dark slides = title(1), north-star(4), why-Zimbabwe(16), close(17). Rest are light.
- Fonts: headers `Cambria`, body `Calibri` (both metric-safe). Do not switch to Aptos.
- Slide order: 1 Title · 2 Problem · 3 What-is-Impilo · 4 North star · 5 OS-vs-App ·
  6 One-Health-ID · 7 Roles/contexts · 8 Seven planes · 9 Trust-first · 10 Experience
  shell · 11 Wellness/friction · 12 Audience 2×2 · 13 Governance · 14 Where-we-are ·
  15 Roadmap · 16 Why-Zimbabwe · 17 Close.
- Placeholders the presenter must fill: `[Date]` and `[Presenter name/role/email]`
  (slides 1 & 17), and the "insert your latest milestone" line (slide 14).

## 5. Making it downloadable for the user

The user could not find a download button because the file is on this remote host.
Give them ONE of these, easiest first:

1. **Cursor / VS Code Remote (recommended):** In the Explorer, right-click
   `docs/presentations/Introduction-to-Impilo-vNext.pptx` → **Download…**.
2. **scp from the user's local machine:**
   `scp <user>@<this-host>:/opt/impilo/repos/Impilo-vNext/docs/presentations/Introduction-to-Impilo-vNext.pptx .`
3. **Temporary HTTP server** (if the user can reach this host's port in a browser):
   ```bash
   cd /opt/impilo/repos/Impilo-vNext/docs/presentations
   python3 -m http.server 8000    # then browse to http://<host>:8000/Introduction-to-Impilo-vNext.pptx
   ```
   Stop it (Ctrl-C) when done.
4. **Commit + push** so they can pull it from the git remote (the repo tracks this
   folder). Follow the project's small-commit rules if you do this.

## 6. Ground rules if you edit

- Keep it audience-safe: the room is mixed (facility users → district → provincial → HQ →
  partners). Plain language, no unexplained jargon.
- Keep claims honest. "Where we are today" (slide 14) and "Roadmap" (slide 15) are
  deliberately modest — do not inflate them into promises. If you add a metric, it must be
  real.
- The content is grounded in the Impilo doctrine (`CLAUDE.md`, `docs/doctrine/`). Do not
  invent new plane names or capabilities.
