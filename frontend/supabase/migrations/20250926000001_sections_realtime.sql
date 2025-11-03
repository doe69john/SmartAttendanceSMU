-- Ensure realtime replication includes section lifecycle changes for model automation.
DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.sections;
EXCEPTION
    WHEN duplicate_object THEN
        NULL;
END $$;

