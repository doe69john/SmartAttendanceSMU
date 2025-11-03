-- Fix RLS recursion between profiles, student_enrollments, and face_data
-- Creates helper functions that bypass RLS recursion and rewrites policies

-- Drop existing policies that will be replaced
DROP POLICY IF EXISTS "Professors can view student profiles in their sections" ON public.profiles;
DROP POLICY IF EXISTS "Admins can view all profiles" ON public.profiles;

DROP POLICY IF EXISTS "Students can view their own enrollments" ON public.student_enrollments;
DROP POLICY IF EXISTS "Professors can view enrollments in their sections" ON public.student_enrollments;
DROP POLICY IF EXISTS "Admins can manage all enrollments" ON public.student_enrollments;

DROP POLICY IF EXISTS "Students can manage their own face data" ON public.face_data;
DROP POLICY IF EXISTS "Professors can view face data of enrolled students" ON public.face_data;

-- Helper functions executed as the table owner to avoid recursive policy evaluation
CREATE OR REPLACE FUNCTION public.current_user_profile_id()
RETURNS uuid
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT id
  FROM public.profiles
  WHERE user_id = auth.uid()
  LIMIT 1;
$$;

CREATE OR REPLACE FUNCTION public.current_user_has_role(target_role public.user_role)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.profiles
    WHERE user_id = auth.uid()
      AND role = target_role
  );
$$;

CREATE OR REPLACE FUNCTION public.current_professor_profile_id()
RETURNS uuid
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT id
  FROM public.profiles
  WHERE user_id = auth.uid()
    AND role = 'professor'
  LIMIT 1;
$$;

CREATE OR REPLACE FUNCTION public.is_professor_section(section_uuid uuid)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.sections s
    WHERE s.id = section_uuid
      AND s.professor_id = public.current_professor_profile_id()
  );
$$;

CREATE OR REPLACE FUNCTION public.is_professor_for_student(student_uuid uuid)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.student_enrollments se
    JOIN public.sections s ON s.id = se.section_id
    WHERE se.student_id = student_uuid
      AND s.professor_id = public.current_professor_profile_id()
  );
$$;

GRANT EXECUTE ON FUNCTION public.current_user_profile_id() TO authenticated, anon, service_role;
GRANT EXECUTE ON FUNCTION public.current_user_has_role(public.user_role) TO authenticated, anon, service_role;
GRANT EXECUTE ON FUNCTION public.current_professor_profile_id() TO authenticated, anon, service_role;
GRANT EXECUTE ON FUNCTION public.is_professor_section(uuid) TO authenticated, anon, service_role;
GRANT EXECUTE ON FUNCTION public.is_professor_for_student(uuid) TO authenticated, anon, service_role;

-- Recreate policies using helper functions to avoid recursive lookups
CREATE POLICY "Admins can view all profiles" ON public.profiles
    FOR ALL USING (public.current_user_has_role('admin'));

CREATE POLICY "Professors can view student profiles in their sections" ON public.profiles
    FOR SELECT USING (
        role = 'student' AND public.is_professor_for_student(profiles.id)
    );

CREATE POLICY "Students can view their own enrollments" ON public.student_enrollments
    FOR SELECT USING (
        student_id = public.current_user_profile_id()
    );

CREATE POLICY "Professors can view enrollments in their sections" ON public.student_enrollments
    FOR SELECT USING (
        public.is_professor_section(section_id)
    );

CREATE POLICY "Admins can manage all enrollments" ON public.student_enrollments
    FOR ALL USING (public.current_user_has_role('admin'));

CREATE POLICY "Students can manage their own face data" ON public.face_data
    FOR ALL USING (
        student_id = public.current_user_profile_id()
    );

CREATE POLICY "Professors can view face data of enrolled students" ON public.face_data
    FOR SELECT USING (
        public.is_professor_for_student(face_data.student_id)
    );
