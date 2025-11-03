-- Adds storage path tracking for per-section LBPH models.
ALTER TABLE public.sections
    ADD COLUMN IF NOT EXISTS model_storage_path text;

COMMENT ON COLUMN public.sections.model_storage_path
    IS 'Storage prefix within face-models bucket that holds the active LBPH model artifacts for this section.';

-- Ensure realtime replication streams storage object changes (required for bucket event listener).
DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE storage.objects;
EXCEPTION
    WHEN duplicate_object THEN
        NULL;
END $$;
