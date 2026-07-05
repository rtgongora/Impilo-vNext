-- Appointment provenance on journeys: booking-service has always sent
-- appointmentId when starting a journey at appointment check-in, but the
-- field was silently dropped. Persisting it distinguishes scheduled
-- check-ins from walk-ins and lets the appointment, journey, and queue
-- item be correlated end-to-end.

ALTER TABLE pct_journeys ADD COLUMN appointment_id UUID;

CREATE INDEX idx_pct_journeys_tenant_appointment
    ON pct_journeys (tenant_id, appointment_id)
    WHERE appointment_id IS NOT NULL;
