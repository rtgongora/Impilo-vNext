# EDLIZ engineering seed — clinical governance note

## Status

The repository ships **synthetic seed excerpts** in Flyway `V002__seed_edliz_national_demo.sql` to unblock engineering (APIs, rules, UI, audit pipeline).

The **authoritative EDLIZ 2025 PDF** is versioned at `docs/reference/edliz-2025/EDLIZ-2025-final-for-circulation.pdf` (see `docs/reference/edliz-2025/README.md` for SHA-256 and metadata). Flyway `V003__edliz_2025_reference_bundle.sql` links the seeded `source_documents` row to that path and hash.

## Requirements before production use

1. Run **PDF ingestion / chunking** from the bundled reference (or MoHCC-issued successor) through the curation pipeline; replace or enrich seed `raw_text` in `source_sections` with curator-approved extracts aligned to page numbers.
2. Clinical safety committee sign-off on **dose tables** (especially neonatal gentamicin seed bands) and **gonorrhoea ceftriaxone** thresholds.
3. Map national **C/B/A/S** and **VEN** columns from the official formulary tables into `medicine_guidance`.
4. Enable Kafka outbox relay and attach **immutable document hashes** on `source_documents.content_hash`.

## Assistant behaviour

The assistant returns `INSUFFICIENT_EVIDENCE` when no indexed section or structured row matches; it must not invent doses or regimens.
