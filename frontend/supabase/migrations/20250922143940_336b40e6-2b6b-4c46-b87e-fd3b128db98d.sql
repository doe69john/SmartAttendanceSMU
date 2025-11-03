-- Create enum types for user roles and attendance status
CREATE TYPE public.user_role AS ENUM ('student', 'professor', 'admin');
CREATE TYPE public.attendance_status AS ENUM ('pending', 'present', 'absent', 'late');
CREATE TYPE public.session_status AS ENUM ('scheduled', 'active', 'completed');
CREATE TYPE public.marking_method AS ENUM ('auto', 'manual');

-- Create profiles table for user information
CREATE TABLE public.profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
    email TEXT NOT NULL UNIQUE,
    full_name TEXT NOT NULL,
    role user_role NOT NULL,
    staff_id TEXT, -- For professors and admins
    student_id TEXT UNIQUE, -- For students, at least 8 digits
    phone TEXT,
    avatar_url TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    
    -- Constraints
    CONSTRAINT valid_student_id CHECK (
        (role = 'student' AND student_id IS NOT NULL AND LENGTH(student_id) >= 8) OR
        (role != 'student' AND student_id IS NULL)
    ),
    CONSTRAINT valid_staff_id CHECK (
        (role IN ('professor', 'admin') AND staff_id IS NOT NULL) OR
        (role = 'student' AND staff_id IS NULL)
    ),
    CONSTRAINT valid_student_email CHECK (
        (role = 'student' AND email LIKE '%@smu.edu.eg') OR
        (role != 'student')
    )
);

-- Create courses table
CREATE TABLE public.courses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_code TEXT NOT NULL UNIQUE, -- e.g., CS102
    course_title TEXT NOT NULL, -- e.g., Programming Fundamentals 2
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- Create sections table
CREATE TABLE public.sections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id UUID REFERENCES public.courses(id) ON DELETE CASCADE NOT NULL,
    section_code TEXT NOT NULL, -- e.g., G1, G2, etc.
    professor_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    day_of_week INTEGER NOT NULL CHECK (day_of_week >= 0 AND day_of_week <= 6), -- 0=Sunday, 6=Saturday
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    location TEXT,
    max_students INTEGER DEFAULT 50,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    
    UNIQUE(course_id, section_code)
);

-- Create student enrollments table
CREATE TABLE public.student_enrollments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
    section_id UUID REFERENCES public.sections(id) ON DELETE CASCADE NOT NULL,
    enrolled_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    is_active BOOLEAN DEFAULT true,
    
    UNIQUE(student_id, section_id)
);

-- Create face data table for storing student facial embeddings
CREATE TABLE public.face_data (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
    image_url TEXT NOT NULL,
    embedding_vector JSONB, -- Store face embeddings as JSON
    confidence_score FLOAT,
    is_primary BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    
    UNIQUE(student_id, image_url)
);

-- Create attendance sessions table
CREATE TABLE public.attendance_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    section_id UUID REFERENCES public.sections(id) ON DELETE CASCADE NOT NULL,
    professor_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL NOT NULL,
    session_date DATE NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE,
    late_threshold_minutes INTEGER DEFAULT 15,
    status session_status DEFAULT 'scheduled',
    location TEXT,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- Create attendance records table
CREATE TABLE public.attendance_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID REFERENCES public.attendance_sessions(id) ON DELETE CASCADE NOT NULL,
    student_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
    status attendance_status DEFAULT 'pending',
    marked_at TIMESTAMP WITH TIME ZONE,
    marking_method marking_method DEFAULT 'manual',
    confidence_score FLOAT, -- For face recognition confidence
    notes TEXT,
    last_seen TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    
    UNIQUE(session_id, student_id)
);

-- Enable Row Level Security
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.courses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sections ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.student_enrollments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.face_data ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance_records ENABLE ROW LEVEL SECURITY;

-- Create RLS Policies

-- Profiles policies
CREATE POLICY "Users can view their own profile" ON public.profiles
    FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can update their own profile" ON public.profiles
    FOR UPDATE USING (auth.uid() = user_id);

CREATE POLICY "Professors can view student profiles in their sections" ON public.profiles
    FOR SELECT USING (
        role = 'student' AND EXISTS (
            SELECT 1 FROM public.sections s
            WHERE s.professor_id = (
                SELECT id FROM public.profiles WHERE user_id = auth.uid() AND role = 'professor'
            )
            AND EXISTS (
                SELECT 1 FROM public.student_enrollments se
                WHERE se.student_id = profiles.id AND se.section_id = s.id
            )
        )
    );

CREATE POLICY "Admins can view all profiles" ON public.profiles
    FOR ALL USING (
        EXISTS (
            SELECT 1 FROM public.profiles 
            WHERE user_id = auth.uid() AND role = 'admin'
        )
    );

-- Courses policies
CREATE POLICY "Everyone can view active courses" ON public.courses
    FOR SELECT USING (is_active = true);

CREATE POLICY "Admins can manage courses" ON public.courses
    FOR ALL USING (
        EXISTS (
            SELECT 1 FROM public.profiles 
            WHERE user_id = auth.uid() AND role = 'admin'
        )
    );

-- Sections policies
CREATE POLICY "Students can view sections they're enrolled in" ON public.sections
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM public.student_enrollments se
            JOIN public.profiles p ON se.student_id = p.id
            WHERE p.user_id = auth.uid() AND se.section_id = sections.id
        )
    );

CREATE POLICY "Professors can view and manage their sections" ON public.sections
    FOR ALL USING (
        professor_id = (
            SELECT id FROM public.profiles WHERE user_id = auth.uid() AND role = 'professor'
        )
    );

CREATE POLICY "Admins can manage all sections" ON public.sections
    FOR ALL USING (
        EXISTS (
            SELECT 1 FROM public.profiles 
            WHERE user_id = auth.uid() AND role = 'admin'
        )
    );

-- Student enrollments policies
CREATE POLICY "Students can view their own enrollments" ON public.student_enrollments
    FOR SELECT USING (
        student_id = (
            SELECT id FROM public.profiles WHERE user_id = auth.uid()
        )
    );

CREATE POLICY "Professors can view enrollments in their sections" ON public.student_enrollments
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM public.sections s
            WHERE s.id = section_id AND s.professor_id = (
                SELECT id FROM public.profiles WHERE user_id = auth.uid() AND role = 'professor'
            )
        )
    );

CREATE POLICY "Admins can manage all enrollments" ON public.student_enrollments
    FOR ALL USING (
        EXISTS (
            SELECT 1 FROM public.profiles 
            WHERE user_id = auth.uid() AND role = 'admin'
        )
    );

-- Face data policies
CREATE POLICY "Students can manage their own face data" ON public.face_data
    FOR ALL USING (
        student_id = (
            SELECT id FROM public.profiles WHERE user_id = auth.uid()
        )
    );

CREATE POLICY "Professors can view face data of enrolled students" ON public.face_data
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM public.student_enrollments se
            JOIN public.sections s ON se.section_id = s.id
            WHERE se.student_id = face_data.student_id 
            AND s.professor_id = (
                SELECT id FROM public.profiles WHERE user_id = auth.uid() AND role = 'professor'
            )
        )
    );

-- Attendance sessions policies
CREATE POLICY "Professors can manage their session attendance" ON public.attendance_sessions
    FOR ALL USING (
        professor_id = (
            SELECT id FROM public.profiles WHERE user_id = auth.uid() AND role = 'professor'
        )
    );

CREATE POLICY "Students can view sessions for their enrolled sections" ON public.attendance_sessions
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM public.student_enrollments se
            JOIN public.profiles p ON se.student_id = p.id
            WHERE p.user_id = auth.uid() AND se.section_id = attendance_sessions.section_id
        )
    );

-- Attendance records policies
CREATE POLICY "Students can view their own attendance records" ON public.attendance_records
    FOR SELECT USING (
        student_id = (
            SELECT id FROM public.profiles WHERE user_id = auth.uid()
        )
    );

CREATE POLICY "Professors can manage attendance records for their sessions" ON public.attendance_records
    FOR ALL USING (
        EXISTS (
            SELECT 1 FROM public.attendance_sessions asess
            WHERE asess.id = session_id AND asess.professor_id = (
                SELECT id FROM public.profiles WHERE user_id = auth.uid() AND role = 'professor'
            )
        )
    );

-- Create triggers for updated_at timestamps
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_profiles_updated_at
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_courses_updated_at
    BEFORE UPDATE ON public.courses
    FOR EACH ROW
    EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_sections_updated_at
    BEFORE UPDATE ON public.sections
    FOR EACH ROW
    EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_attendance_sessions_updated_at
    BEFORE UPDATE ON public.attendance_sessions
    FOR EACH ROW
    EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_attendance_records_updated_at
    BEFORE UPDATE ON public.attendance_records
    FOR EACH ROW
    EXECUTE FUNCTION public.update_updated_at_column();

-- Create function to handle new user registration
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
BEGIN
    INSERT INTO public.profiles (user_id, email, full_name, role, staff_id, student_id)
    VALUES (
        NEW.id,
        NEW.email,
        COALESCE(NEW.raw_user_meta_data->>'full_name', NEW.email),
        (NEW.raw_user_meta_data->>'role')::user_role,
        NEW.raw_user_meta_data->>'staff_id',
        NEW.raw_user_meta_data->>'student_id'
    );
    RETURN NEW;
END;
$$;

-- Create trigger for new user registration
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- Create indexes for better performance
CREATE INDEX idx_profiles_user_id ON public.profiles(user_id);
CREATE INDEX idx_profiles_role ON public.profiles(role);
CREATE INDEX idx_profiles_student_id ON public.profiles(student_id);
CREATE INDEX idx_sections_professor_id ON public.sections(professor_id);
CREATE INDEX idx_sections_course_id ON public.sections(course_id);
CREATE INDEX idx_student_enrollments_student_id ON public.student_enrollments(student_id);
CREATE INDEX idx_student_enrollments_section_id ON public.student_enrollments(section_id);
CREATE INDEX idx_face_data_student_id ON public.face_data(student_id);
CREATE INDEX idx_attendance_sessions_section_id ON public.attendance_sessions(section_id);
CREATE INDEX idx_attendance_sessions_date ON public.attendance_sessions(session_date);
CREATE INDEX idx_attendance_records_session_id ON public.attendance_records(session_id);
CREATE INDEX idx_attendance_records_student_id ON public.attendance_records(student_id);