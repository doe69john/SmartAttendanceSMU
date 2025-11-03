-- Fix security issues: Replace security definer view with regular view and add proper RLS policies

-- Drop the security definer view and recreate as regular view
DROP VIEW IF EXISTS attendance_analytics;

-- Create regular view without security definer
CREATE VIEW attendance_analytics AS
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