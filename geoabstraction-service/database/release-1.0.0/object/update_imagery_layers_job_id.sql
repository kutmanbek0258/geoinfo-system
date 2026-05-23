-- Add job_id column to imagery_layers table
ALTER TABLE geoabstraction.imagery_layers ADD COLUMN job_id UUID;

-- Add index for job_id
CREATE INDEX ix_imagery_layers_job_id ON geoabstraction.imagery_layers (job_id);
