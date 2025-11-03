-- First, disable RLS temporarily to drop all policies
ALTER TABLE public.profiles DISABLE ROW LEVEL SECURITY;

-- Create security definer functions to avoid infinite recursion
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN AS $$
BEGIN
  -- Check from auth.users metadata to avoid recursion
  RETURN COALESCE(
    (SELECT (raw_user_meta_data ->> 'role') = 'admin' FROM auth.users WHERE id = auth.uid()),
    false
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE SET search_path = public;

CREATE OR REPLACE FUNCTION public.is_professor()
RETURNS BOOLEAN AS $$
BEGIN
  -- Check from auth.users metadata to avoid recursion
  RETURN COALESCE(
    (SELECT (raw_user_meta_data ->> 'role') = 'professor' FROM auth.users WHERE id = auth.uid()),
    false
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE SET search_path = public;

-- Re-enable RLS
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- Create new clean policies using security definer functions
CREATE POLICY "Users can view their own profile" ON public.profiles
FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can insert their own profile" ON public.profiles
FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update their own profile" ON public.profiles
FOR UPDATE USING (auth.uid() = user_id);

CREATE POLICY "Admins can view all profiles" ON public.profiles
FOR SELECT USING (auth.uid() = user_id OR public.is_admin());

CREATE POLICY "Admins can manage all profiles" ON public.profiles
FOR ALL USING (public.is_admin());

CREATE POLICY "Professors can view student profiles" ON public.profiles
FOR SELECT USING (
  auth.uid() = user_id OR 
  (role = 'student' AND public.is_professor()) OR
  public.is_admin()
);