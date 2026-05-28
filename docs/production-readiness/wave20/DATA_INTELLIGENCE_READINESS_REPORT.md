# Data & Intelligence Readiness Report — Wave 20

> Date: 2026-05-28

## Summary

The **Data & Intelligence Plane** is now **visible and navigable** via `/data-intelligence` with sub-hubs for quality, pipelines, integration, reports, and audit. Depth remains **Partial** for live operational metrics.

| Area | Readiness | Surfacing |
|------|-----------|-----------|
| Data quality | Partial | `/data-intelligence/quality` + `/intelligence` DATA_QUALITY_HINTS |
| Data completeness | Partial | Facility reports, registry heuristics |
| Data validation | Partial | Admin/governance routes |
| Pipelines / events | Partial | Links to platform journey monitor |
| Integration monitor | Partial | `/data-intelligence/integration` → `/admin/integration-status` |
| Indicator registry | Partial | Zibo/reporting scattered |
| Reporting | Partial | `/data-intelligence/reports` hub |
| Analytics | Partial | Clinical/ops/finance report dashboards |
| Clinical intelligence | Partial | Control tower, clinical reports |
| Public health intelligence | Partial | `/public-health` dashboards |
| Enterprise intelligence | Partial | Enterprise + ops reports |
| Financial intelligence | Partial | `/finance/reports` |
| Geospatial intelligence | Partial | `/ndila`, NdilaIntelligencePanel |
| Dispatch/logistics intelligence | Partial | `/nhume/analytics` |
| Audit intelligence | Partial | `/data-intelligence/audit` |
| Data governance | Partial | `/admin/data-governance` |
| Nompilo insights | Partial | `/intelligence`, contextual panels |

## Known gaps

- No single live pipeline lag dashboard without reporting-service depth
- Indicator definitions not unified in one registry UI
- Nompilo cannot yet summarize arbitrary dashboard JSON — guidance presets only

## Next steps

1. Wire BFF integration-status into command centre and data-intelligence overview tiles
2. Add indicator catalogue page linked from Zibo
3. Nompilo preset for operational summary on `/data-intelligence`
