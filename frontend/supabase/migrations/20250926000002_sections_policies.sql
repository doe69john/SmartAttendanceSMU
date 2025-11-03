-- Ensure sections table participates fully in realtime replication and is accessible
-- through Supabase APIs. These policies are idempotent and safe to rerun.
ALTER TABLE public.sections ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sections REPLICA IDENTITY FULL;

DO $$
BEGIN
    CREATE POLICY "Service role can manage sections"
        ON public.sections
        FOR ALL
        TO service_role
        USING (true)
        WITH CHECK (true);
EXCEPTION
    WHEN duplicate_object THEN
        NULL;
END $$;

DO $$
BEGIN
    CREATE POLICY "Authenticated users can read active sections"
        ON public.sections
        FOR SELECT
        TO authenticated
        USING (is_active IS TRUE);
EXCEPTION
    WHEN duplicate_object THEN
        NULL;
END $$;

-- Ensure the face-models bucket exists and allow service role automation to manage
-- trained artifacts.
INSERT INTO storage.buckets (id, name, public)
VALUES ('face-models', 'face-models', false)
ON CONFLICT (id) DO NOTHING;

DO $$
BEGIN
    CREATE POLICY "Service role manage face-models"
        ON storage.objects
        FOR ALL
        TO service_role
        USING (bucket_id = 'face-models')
        WITH CHECK (bucket_id = 'face-models');
EXCEPTION
    WHEN duplicate_object THEN
        NULL;
END $$;

DO $$
BEGIN
    CREATE POLICY "Authenticated read face-models"
        ON storage.objects
        FOR SELECT
        TO authenticated
        USING (bucket_id = 'face-models');
EXCEPTION
    WHEN duplicate_object THEN
        NULL;
END $$;
