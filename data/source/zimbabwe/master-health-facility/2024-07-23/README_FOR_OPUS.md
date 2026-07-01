# Zimbabwe Facility Master Absorption Package

Source: Master Health Facility 23 July 2024.xlsx
Source label: MASTER_HEALTH_FACILITY_2024_07_23
Prepared for: Impilo vNext / TUSO facility lifecycle absorption
Generated: 2026-07-01 17:45:12

## Product-owner import rules applied

1. Facilities without a facility code are excluded from the clean import and placed in review.
2. Duplicate facility codes are excluded from the clean import and placed in review.
3. Duplicate normalized facility names are excluded from the clean import and placed in review.
4. Missing latitude/longitude is acceptable and must surface on the frontend as missing geospatial readiness.
5. Missing facility type is acceptable and must surface on the frontend as missing facility type.
6. Missing ownership is acceptable and must surface on the frontend as missing ownership.
7. Missing status is acceptable and must surface on the frontend as missing status.
8. Clean rows are import candidates, not fully configured facilities. They should enter TUSO as IMPORTED_PENDING_CONFIGURATION.

## Counts

- Source rows: 2023
- Clean import rows: 1773
- Excluded/review rows: 250
- Missing facility code rows: 124
- Duplicate code review rows: 81
- Duplicate name review rows: 47
- Clean rows with acceptable missing fields: 194

## Suggested repo placement for Opus

Place this whole folder/package under:

`data/source/zimbabwe/master-health-facility/2024-07-23/`

Then instruct Opus to use `clean_tuso_facility_import.csv` as the apply dataset and the review CSVs as staging/review datasets.

## Critical rule

Do not treat clean rows as fully operational facilities. They are facility master records pending full lifecycle setup: practitioner in charge, regulatory compliance, service points, workspaces, queues, staff, stock, digital readiness, and downstream materialisation.
