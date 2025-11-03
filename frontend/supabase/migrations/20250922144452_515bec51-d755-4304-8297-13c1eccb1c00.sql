-- Fix the function search path security issue
ALTER FUNCTION public.handle_new_user() SET search_path = public;