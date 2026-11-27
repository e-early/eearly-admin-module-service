-- Language of the patient's app, push notifications and emails, as an ISO 639-1 code.
-- Existing patients get 'sl' too.
ALTER TABLE t_patient ADD COLUMN IF NOT EXISTS language VARCHAR(3) NOT NULL DEFAULT 'sl';
