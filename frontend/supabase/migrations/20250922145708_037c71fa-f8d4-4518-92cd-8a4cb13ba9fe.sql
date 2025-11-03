-- Fix the infinite recursion in RLS policies by removing problematic policies and creating simpler ones

-- Drop the problematic policies that cause infinite recursion
DROP POLICY IF EXISTS "Professors can view student profiles in their sections" ON public.profiles;
DROP POLICY IF EXISTS "Admins can view all profiles" ON public.profiles;

-- Create simpler, non-recursive policies
CREATE POLICY "Professors can view all student profiles" ON public.profiles
    FOR SELECT USING (
        (auth.uid() = user_id) OR 
        (role = 'student' AND EXISTS (
            SELECT 1 FROM public.profiles p 
            WHERE p.user_id = auth.uid() AND p.role = 'professor'
        ))
    );

CREATE POLICY "Admins can view all profiles" ON public.profiles
    FOR SELECT USING (
        (auth.uid() = user_id) OR
        EXISTS (
            SELECT 1 FROM auth.users u 
            WHERE u.id = auth.uid() 
            AND u.raw_user_meta_data->>'role' = 'admin'
        )
    );

-- Allow profile creation for all authenticated users
CREATE POLICY "Users can insert their own profile" ON public.profiles
    FOR INSERT WITH CHECK (auth.uid() = user_id);

-- Allow admins to manage all profiles
CREATE POLICY "Admins can manage all profiles" ON public.profiles
    FOR ALL USING (
        EXISTS (
            SELECT 1 FROM auth.users u 
            WHERE u.id = auth.uid() 
            AND u.raw_user_meta_data->>'role' = 'admin'
        )
    );