-- =============================================
-- Service : oros-service
-- Flyway  : V011__workflow_state.sql
-- Purpose : Generalise the fine-grained fulfilment lifecycle beyond imaging. A single
--           category-agnostic workflow_state column carries the granular journey for every
--           order category (imaging/lab/procedure/...); the legal transitions are enforced
--           per-category by deny-by-default guards (ImagingWorkflow / LabWorkflow /
--           ProcedureWorkflow) selected via the WorkflowGuardRegistry.
--
--           imaging_state (V004) is retained and written in lockstep for IMAGING orders so the
--           existing imaging worklist / reconciliation surfaces keep working unchanged.
-- =============================================

ALTER TABLE oros_orders
    ADD COLUMN workflow_state VARCHAR(48);

-- Backfill the unified column from the imaging projection so historical imaging orders surface
-- their fine-grained state through the generalised column immediately.
UPDATE oros_orders
   SET workflow_state = imaging_state
 WHERE imaging_state IS NOT NULL
   AND workflow_state IS NULL;

CREATE INDEX idx_oros_orders_workflow_state
    ON oros_orders (tenant_id, facility_id, workflow_state)
    WHERE workflow_state IS NOT NULL;
