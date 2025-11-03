-- Create security definer functions to avoid infinite recursion
CREATE OR REPLACE FUNCTION public.get_current_user_role()
RETURNS TEXT AS $$
BEGIN
  -- First check if user exists in profiles table
  RETURN (SELECT role::text FROM public.profiles WHERE user_id = auth.uid() LIMIT 1);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE SET search_path = public;

CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN AS $$
BEGIN
  -- Check from auth.users metadata first, then profiles table
  RETURN COALESCE(
    (SELECT (raw_user_meta_data ->> 'role') = 'admin' FROM auth.users WHERE id = auth.uid()),
    (SELECT role = 'admin' FROM public.profiles WHERE user_id = auth.uid()),
    false
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE SET search_path = public;

CREATE OR REPLACE FUNCTION public.is_professor()
RETURNS BOOLEAN AS $$
BEGIN
  -- Check from auth.users metadata first, then profiles table
  RETURN COALESCE(
    (SELECT (raw_user_meta_data ->> 'role') = 'professor' FROM auth.users WHERE id = auth.uid()),
    (SELECT role = 'professor' FROM public.profiles WHERE user_id = auth.uid()),
    false
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE SET search_path = public;

-- Drop existing problematic policies
DROP POLICY IF EXISTS "Admins can manage all profiles" ON public.profiles;
DROP POLICY IF EXISTS "Admins can view all profiles" ON public.profiles;
DROP POLICY IF EXISTS "Professors can view all student profiles" ON public.profiles;
DROP POLICY IF EXISTS "Users can view their own profile" ON public.profiles;
DROP POLICY IF EXISTS "Users can insert their own profile" ON public.profiles;
DROP POLICY IF EXISTS "Users can update their own profile" ON public.profiles;

-- Create new policies using security definer functions
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