-- Fix the email domain constraint to use smu.edu.sg and allow subdomains
ALTER TABLE public.profiles 
DROP CONSTRAINT IF EXISTS valid_student_email;

ALTER TABLE public.profiles 
ADD CONSTRAINT valid_student_email CHECK (
    (role = 'student' AND email LIKE '%.smu.edu.sg') OR
    (role != 'student')
);