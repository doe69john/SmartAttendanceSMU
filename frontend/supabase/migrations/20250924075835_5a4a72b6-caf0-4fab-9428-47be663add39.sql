-- Add configuration table for system settings if not exists
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'system_settings') THEN
        CREATE TABLE public.system_settings (
            id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
            setting_key TEXT NOT NULL UNIQUE,
            setting_value JSONB NOT NULL,
            description TEXT,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
            updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
        );
        
        -- Enable RLS
        ALTER TABLE public.system_settings ENABLE ROW LEVEL SECURITY;
        
        -- Create policies
        CREATE POLICY "Admins can manage system settings" 
        ON public.system_settings 
        FOR ALL 
        USING (is_admin());
        
        CREATE POLICY "Authenticated users can view system settings" 
        ON public.system_settings 
        FOR SELECT 
        USING (true);
    END IF;
END $$;

-- Update face_data table with additional fields for enhanced face capture
ALTER TABLE public.face_data 
ADD COLUMN IF NOT EXISTS quality_score NUMERIC CHECK (quality_score >= 0 AND quality_score <= 1),
ADD COLUMN IF NOT EXISTS embedding_vector JSONB,
ADD COLUMN IF NOT EXISTS processing_status TEXT DEFAULT 'pending' CHECK (processing_status IN ('pending', 'processing', 'completed', 'trained', 'failed'));

-- Add indexes for better performance
CREATE INDEX IF NOT EXISTS idx_face_data_processing_status ON public.face_data (processing_status);
CREATE INDEX IF NOT EXISTS idx_face_data_student_id ON public.face_data (student_id);
CREATE INDEX IF NOT EXISTS idx_system_settings_key ON public.system_settings (setting_key);

-- Create trigger for updating updated_at timestamp
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SET search_path = public;

-- Apply trigger to system_settings
DROP TRIGGER IF EXISTS update_system_settings_updated_at ON public.system_settings;
CREATE TRIGGER update_system_settings_updated_at
    BEFORE UPDATE ON public.system_settings
    FOR EACH ROW
    EXECUTE FUNCTION public.update_updated_at_column();

-- Ensure face-images bucket exists and has proper policies
INSERT INTO storage.buckets (id, name, public) 
VALUES ('face-images', 'face-images', false)
ON CONFLICT (id) DO NOTHING;

-- Create storage policies for face images
DROP POLICY IF EXISTS "Users can upload their own face images" ON storage.objects;
CREATE POLICY "Users can upload their own face images" 
ON storage.objects 
FOR INSERT 
WITH CHECK (
    bucket_id = 'face-images' AND 
    auth.uid()::text = (storage.foldername(name))[1]
);

DROP POLICY IF EXISTS "Users can view their own face images" ON storage.objects;
CREATE POLICY "Users can view their own face images" 
ON storage.objects 
FOR SELECT 
USING (
    bucket_id = 'face-images' AND 
    auth.uid()::text = (storage.foldername(name))[1]
);

DROP POLICY IF EXISTS "Professors can view face images of enrolled students" ON storage.objects;
CREATE POLICY "Professors can view face images of enrolled students" 
ON storage.objects 
FOR SELECT 
USING (
    bucket_id = 'face-images' AND 
    EXISTS (
        SELECT 1 
        FROM student_enrollments se
        JOIN sections s ON se.section_id = s.id
        JOIN profiles p ON se.student_id = p.id
        WHERE p.user_id::text = (storage.foldername(name))[1]
        AND s.professor_id = (
            SELECT id FROM profiles 
            WHERE user_id = auth.uid() AND role = 'professor'
        )
    )
);

-- Allow admins to manage all face images
DROP POLICY IF EXISTS "Admins can manage all face images" ON storage.objects;
CREATE POLICY "Admins can manage all face images" 
ON storage.objects 
FOR ALL 
USING (bucket_id = 'face-images' AND is_admin());