-- Add missing tables for comprehensive smart attendance system

-- Create storage bucket for face images
INSERT INTO storage.buckets (id, name, public) VALUES ('face-images', 'face-images', false);

-- Create policies for face images storage
CREATE POLICY "Students can upload their own face images"
ON storage.objects
FOR INSERT
WITH CHECK (
  bucket_id = 'face-images' AND 
  auth.uid()::text = (storage.foldername(name))[1] AND
  EXISTS (SELECT 1 FROM profiles WHERE user_id = auth.uid() AND role = 'student')
);

CREATE POLICY "Students can view their own face images"
ON storage.objects
FOR SELECT
USING (
  bucket_id = 'face-images' AND 
  auth.uid()::text = (storage.foldername(name))[1]
);

CREATE POLICY "Professors can view face images of enrolled students"
ON storage.objects
FOR SELECT
USING (
  bucket_id = 'face-images' AND
  EXISTS (
    SELECT 1 FROM student_enrollments se
    JOIN sections s ON se.section_id = s.id
    JOIN profiles student_profile ON se.student_id = student_profile.id
    JOIN profiles prof_profile ON s.professor_id = prof_profile.id
    WHERE prof_profile.user_id = auth.uid() 
    AND prof_profile.role = 'professor'
    AND student_profile.user_id::text = (storage.foldername(name))[1]
  )
);

-- Enhance face_data table with processing status
ALTER TABLE face_data 
ADD COLUMN processing_status TEXT DEFAULT 'pending' CHECK (processing_status IN ('pending', 'processing', 'completed', 'failed')),
ADD COLUMN quality_score DECIMAL(3,2),
ADD COLUMN face_encoding JSONB,
ADD COLUMN metadata JSONB;

-- Create attendance analytics view
CREATE OR REPLACE VIEW attendance_analytics AS
SELECT 
  p.id as student_id,
  p.full_name as student_name,
  c.course_code,
  s.section_code,
  COUNT(ar.id) as total_sessions,
  COUNT(CASE WHEN ar.status = 'present' THEN 1 END) as present_count,
  COUNT(CASE WHEN ar.status = 'late' THEN 1 END) as late_count,
  COUNT(CASE WHEN ar.status = 'absent' THEN 1 END) as absent_count,
  ROUND(
    (COUNT(CASE WHEN ar.status IN ('present', 'late') THEN 1 END)::decimal / 
     NULLIF(COUNT(ar.id), 0)) * 100, 2
  ) as attendance_percentage
FROM profiles p
JOIN student_enrollments se ON p.id = se.student_id
JOIN sections s ON se.section_id = s.id
JOIN courses c ON s.course_id = c.id
LEFT JOIN attendance_records ar ON p.id = ar.student_id
LEFT JOIN attendance_sessions asess ON ar.session_id = asess.id AND asess.section_id = s.id
WHERE p.role = 'student' AND se.is_active = true
GROUP BY p.id, p.full_name, c.course_code, s.section_code;

-- Create session roster table for managing attendance per session
CREATE TABLE session_rosters (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES attendance_sessions(id) ON DELETE CASCADE,
  student_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  expected_status attendance_status DEFAULT 'pending',
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  UNIQUE(session_id, student_id)
);

-- Enable RLS on session_rosters
ALTER TABLE session_rosters ENABLE ROW LEVEL SECURITY;

-- RLS policies for session_rosters
CREATE POLICY "Professors can manage session rosters for their sessions"
ON session_rosters
FOR ALL
USING (
  EXISTS (
    SELECT 1 FROM attendance_sessions asess
    JOIN profiles p ON asess.professor_id = p.id
    WHERE asess.id = session_rosters.session_id
    AND p.user_id = auth.uid()
    AND p.role = 'professor'
  )
);

CREATE POLICY "Students can view their session roster entries"
ON session_rosters
FOR SELECT
USING (
  student_id = (SELECT id FROM profiles WHERE user_id = auth.uid())
);

-- Create system settings table
CREATE TABLE system_settings (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  setting_key TEXT UNIQUE NOT NULL,
  setting_value JSONB NOT NULL,
  description TEXT,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- Enable RLS on system_settings
ALTER TABLE system_settings ENABLE ROW LEVEL SECURITY;

-- RLS policies for system_settings
CREATE POLICY "Admins can manage system settings"
ON system_settings
FOR ALL
USING (is_admin());

CREATE POLICY "Authenticated users can view system settings"
ON system_settings
FOR SELECT
TO authenticated
USING (true);

-- Insert default system settings
INSERT INTO system_settings (setting_key, setting_value, description) VALUES
('face_recognition_threshold', '0.85', 'Minimum confidence threshold for face recognition'),
('late_threshold_minutes', '15', 'Default minutes after start time to mark as late'),
('auto_mark_absent_hours', '24', 'Hours after session to auto-mark absent students'),
('max_face_images_per_student', '5', 'Maximum number of face images per student');

-- Create trigger for updating session_rosters updated_at
CREATE TRIGGER update_session_rosters_updated_at
  BEFORE UPDATE ON session_rosters
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- Create trigger for updating system_settings updated_at  
CREATE TRIGGER update_system_settings_updated_at
  BEFORE UPDATE ON system_settings
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();