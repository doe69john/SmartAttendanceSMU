import { useState, useEffect } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { Textarea } from '@/components/ui/textarea';
import { 
  Plus,
  BookOpen,
  Users,
  Calendar,
  Clock,
  MapPin,
  Search,
  BarChart3,
  Play
} from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { useToast } from '@/hooks/use-toast';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import {
  ApiError,
  createCourse,
  createSection,
  fetchProfessorCourses,
  fetchProfessorSections,
  fetchSectionSessions,
  scheduleSession
} from '@/lib/api';
import { formatDateWithOffset } from '@/lib/date-utils';

interface Course {
  id: string;
  courseCode: string;
  courseTitle: string;
  description?: string;
  active: boolean;
}

interface Section {
  id: string;
  sectionCode: string;
  courseId: string;
  courseCode: string;
  courseTitle: string;
  courseDescription?: string;
  dayOfWeek: number;
  startTime?: string;
  endTime?: string;
  location?: string;
  maxStudents: number;
  enrolledCount?: number;
}

interface Session {
  id: string;
  sessionDate: string;
  startTime?: string;
  endTime?: string;
  status: 'scheduled' | 'active' | 'completed' | 'cancelled';
  location?: string;
  notes?: string;
  attendanceCount?: number;
  totalStudents?: number;
}

interface CourseFormValues {
  courseCode: string;
  courseTitle: string;
  description: string;
  [key: string]: unknown;
}

interface SectionFormValues {
  courseId: string;
  sectionCode: string;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  location: string;
  maxStudents: number;
}

interface SessionFormValues {
  sessionDate: string;
  startTime: string;
  location: string;
  notes: string;
  lateThresholdMinutes: number;
}

const ISO_WEEKDAYS: Record<number, string> = {
  1: 'Monday',
  2: 'Tuesday',
  3: 'Wednesday',
  4: 'Thursday',
  5: 'Friday',
  6: 'Saturday',
  7: 'Sunday',
};

const WEEKDAY_OPTIONS = [
  { value: '1', label: 'Monday' },
  { value: '2', label: 'Tuesday' },
  { value: '3', label: 'Wednesday' },
  { value: '4', label: 'Thursday' },
  { value: '5', label: 'Friday' },
  { value: '6', label: 'Saturday' },
  { value: '7', label: 'Sunday' },
];

export const ProfessorCourses = () => {
  const { profile } = useAuth();
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  
  const [selectedSection, setSelectedSection] = useState<Section | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [showCreateCourse, setShowCreateCourse] = useState(false);
  const [showCreateSection, setShowCreateSection] = useState(false);
  const [showCreateSession, setShowCreateSession] = useState(false);

  const showApiError = (error: unknown, fallback: string) => {
    const description = error instanceof ApiError ? error.message : fallback;
    toast({
      title: "Error",
      description,
      variant: "destructive"
    });
  };

  // Fetch professor's courses and sections
  const { data: sections = [], isLoading } = useQuery({
    queryKey: ['professor-sections', profile?.id],
    queryFn: async () => {
      if (!profile?.id) return [];
      const payload = await fetchProfessorSections(profile.id) as Section[];
      return payload.map(section => ({
        ...section,
        enrolledCount: section.enrolledCount ?? 0,
      }));
    },
    enabled: !!profile?.id && profile?.role === 'professor'
  });

  // Fetch available courses for creating sections
  const { data: availableCourses = [] as Course[] } = useQuery({
    queryKey: ['available-courses', profile?.id],
    queryFn: async () => {
      if (!profile?.id) return [];
      const result = await fetchProfessorCourses(profile.id);
      return Array.isArray(result) ? result : [];
    },
    enabled: !!profile?.id
  });

  // Fetch sessions for selected section
  const { data: sessions = [] } = useQuery({
    queryKey: ['section-sessions', selectedSection?.id],
    queryFn: async () => {
      if (!selectedSection?.id) return [];
      const payload = await fetchSectionSessions(selectedSection.id) as Session[];
      return payload.map(session => ({
        ...session,
        totalStudents: session.totalStudents ?? selectedSection.enrolledCount ?? 0,
      }));
    },
    enabled: !!selectedSection?.id
  });

  // Create course mutation
  const createCourseMutation = useMutation({
    mutationFn: async (courseData: CourseFormValues) => {
      return createCourse(courseData);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['available-courses', profile?.id] });
      setShowCreateCourse(false);
      toast({
        title: "Course Created",
        description: "New course has been created successfully"
      });
    },
    onError: (error) => showApiError(error, 'Failed to create course')
  });

  // Create section mutation
  const createSectionMutation = useMutation({
    mutationFn: async (sectionData: SectionFormValues) => {
      return createSection({
        ...sectionData,
        dayOfWeek: parseInt(sectionData.dayOfWeek, 10),
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['professor-sections'] });
      setShowCreateSection(false);
      toast({
        title: "Section Created",
        description: "New section has been created successfully"
      });
    },
    onError: (error) => showApiError(error, 'Failed to create section')
  });

  // Create session mutation
  const createSessionMutation = useMutation({
    mutationFn: async (sessionData: SessionFormValues) => {
      if (!selectedSection?.id) {
        throw new Error('Section is required');
      }
      return scheduleSession(selectedSection.id, sessionData);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['section-sessions', selectedSection?.id] });
      setShowCreateSession(false);
      toast({
        title: "Session Scheduled",
        description: "New session has been scheduled successfully"
      });
    },
    onError: (error) => showApiError(error, 'Failed to schedule session')
  });

  const filteredSections = sections.filter(section =>
    section.courseCode.toLowerCase().includes(searchTerm.toLowerCase()) ||
    section.courseTitle.toLowerCase().includes(searchTerm.toLowerCase()) ||
    section.sectionCode.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const formatSectionTime = (time?: string | null) => {
    if (!time) return '';
    return time.length >= 5 ? time.slice(0, 5) : time;
  };

  const formatSessionTime = (value?: string | null) => {
    if (!value) return '';
    const date = new Date(value);
    return isNaN(date.getTime()) ? value : date.toLocaleTimeString();
  };

  const startLiveSession = (sessionId: string) => {
    navigate(`/live-session/${sessionId}`);
  };

  const viewSessionAnalytics = (sessionId: string) => {
    navigate(`/session-analytics/${sessionId}`);
  };

  if (profile?.role !== 'professor') {
    return (
      <div className="p-6">
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-8">
            <h3 className="text-lg font-semibold mb-2">Access Restricted</h3>
            <p className="text-muted-foreground text-center">
              This page is only available for professors.
            </p>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">My Courses</h1>
          <p className="text-muted-foreground">Manage your courses, sections, and sessions</p>
        </div>
        <div className="flex gap-2">
          <Dialog open={showCreateCourse} onOpenChange={setShowCreateCourse}>
            <DialogTrigger asChild>
              <Button variant="outline">
                <Plus className="w-4 h-4 mr-2" />
                New Course
              </Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Create New Course</DialogTitle>
                <DialogDescription>Add a new course to the system</DialogDescription>
              </DialogHeader>
              <CourseForm onSubmit={(data) => createCourseMutation.mutate(data)} />
            </DialogContent>
          </Dialog>
          
          <Dialog open={showCreateSection} onOpenChange={setShowCreateSection}>
            <DialogTrigger asChild>
              <Button>
                <Plus className="w-4 h-4 mr-2" />
                New Section
              </Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Create New Section</DialogTitle>
                <DialogDescription>Add a new section for an existing course</DialogDescription>
              </DialogHeader>
              <SectionForm 
                courses={availableCourses}
                onSubmit={(data) => createSectionMutation.mutate(data)} 
              />
            </DialogContent>
          </Dialog>
        </div>
      </div>

      <div className="flex gap-2">
        <Input
          placeholder="Search courses and sections..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          className="max-w-sm"
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2 space-y-4">
          <h2 className="text-xl font-semibold">My Sections</h2>
          {filteredSections.length === 0 ? (
            <Card>
              <CardContent className="flex flex-col items-center justify-center py-8">
                <BookOpen className="w-12 h-12 text-muted-foreground mb-4" />
                <h3 className="text-lg font-semibold mb-2">No Sections Found</h3>
                <p className="text-muted-foreground text-center">
                  Create your first section to start managing courses.
                </p>
              </CardContent>
            </Card>
          ) : (
            <div className="space-y-4">
              {filteredSections.map((section) => (
                <Card 
                  key={section.id}
                  className={`cursor-pointer transition-colors ${
                    selectedSection?.id === section.id ? 'border-primary' : ''
                  }`}
                  onClick={() => setSelectedSection(section)}
                >
                  <CardHeader>
                    <div className="flex items-center justify-between">
                      <div>
                        <CardTitle className="flex items-center gap-2">
                          <BookOpen className="w-5 h-5" />
                          {section.courseCode} - {section.sectionCode}
                        </CardTitle>
                        <CardDescription>{section.courseTitle}</CardDescription>
                      </div>
                      <Badge variant="outline">
                        <Users className="w-3 h-3 mr-1" />
                        {(section.enrolledCount ?? 0)}/{section.maxStudents ?? 0}
                      </Badge>
                    </div>
                  </CardHeader>
                  <CardContent>
                    <div className="flex items-center gap-4 text-sm text-muted-foreground">
                      <div className="flex items-center gap-1">
                        <Calendar className="w-4 h-4" />
                        {ISO_WEEKDAYS[section.dayOfWeek] ?? 'Unknown'}
                      </div>
                      <div className="flex items-center gap-1">
                        <Clock className="w-4 h-4" />
                        {formatSectionTime(section.startTime)}
                        {section.endTime ? ` - ${formatSectionTime(section.endTime)}` : ''}
                      </div>
                      {section.location && (
                        <div className="flex items-center gap-1">
                          <MapPin className="w-4 h-4" />
                          {section.location}
                        </div>
                      )}
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          )}
        </div>

        <div className="space-y-4">
          {selectedSection ? (
            <>
              <div className="flex items-center justify-between">
                <h2 className="text-xl font-semibold">Sessions</h2>
                <Dialog open={showCreateSession} onOpenChange={setShowCreateSession}>
                  <DialogTrigger asChild>
                    <Button size="sm">
                      <Plus className="w-4 h-4 mr-1" />
                      Session
                    </Button>
                  </DialogTrigger>
                  <DialogContent>
                    <DialogHeader>
                      <DialogTitle>Schedule New Session</DialogTitle>
                      <DialogDescription>
                        Create a new session for {selectedSection.courseCode} - {selectedSection.sectionCode}
                      </DialogDescription>
                    </DialogHeader>
                    <SessionForm onSubmit={(data) => createSessionMutation.mutate(data)} />
                  </DialogContent>
                </Dialog>
              </div>

              <div className="space-y-2 max-h-96 overflow-y-auto">
                {sessions.map((session) => (
                  <Card key={session.id} className="p-4">
                    <div className="space-y-2">
                      <div className="flex items-center justify-between">
                        <span className="font-medium">
                          {new Date(session.sessionDate).toLocaleDateString()}
                        </span>
                        <Badge variant={
                          session.status === 'completed' ? 'default' :
                          session.status === 'active' ? 'secondary' :
                          session.status === 'cancelled' ? 'destructive' : 'outline'
                        }>
                          {session.status}
                        </Badge>
                      </div>
                      
                      <div className="text-sm text-muted-foreground">
                        {formatSessionTime(session.startTime)}
                        {session.endTime && ` - ${formatSessionTime(session.endTime)}`}
                      </div>

                      {session.status === 'completed' && (
                        <div className="text-sm">
                          Attendance: {session.attendanceCount}/{session.totalStudents}
                        </div>
                      )}
                      
                      <div className="flex gap-1 pt-2">
                        {session.status === 'scheduled' && (
                          <Button 
                            size="sm" 
                            onClick={() => startLiveSession(session.id)}
                            className="flex-1"
                          >
                            <Play className="w-3 h-3 mr-1" />
                            Start
                          </Button>
                        )}
                        {session.status === 'active' && (
                          <Button 
                            size="sm" 
                            onClick={() => startLiveSession(session.id)}
                            className="flex-1"
                            variant="secondary"
                          >
                            <Play className="w-3 h-3 mr-1" />
                            Join Live
                          </Button>
                        )}
                        {session.status === 'completed' && (
                          <Button 
                            size="sm" 
                            variant="outline"
                            onClick={() => viewSessionAnalytics(session.id)}
                            className="flex-1"
                          >
                            <BarChart3 className="w-3 h-3 mr-1" />
                            Analytics
                          </Button>
                        )}
                      </div>
                    </div>
                  </Card>
                ))}
                
                {sessions.length === 0 && (
                  <div className="text-center py-8">
                    <Calendar className="w-8 h-8 text-muted-foreground mx-auto mb-2" />
                    <p className="text-sm text-muted-foreground">No sessions scheduled</p>
                  </div>
                )}
              </div>

            </>
          ) : (
            <div className="text-center py-8">
              <BookOpen className="w-8 h-8 text-muted-foreground mx-auto mb-2" />
              <p className="text-sm text-muted-foreground">Select a section to view sessions</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

// Component for creating new courses
const CourseForm = ({ onSubmit }: { onSubmit: (data: CourseFormValues) => void }) => {
  const [formData, setFormData] = useState<CourseFormValues>({
    courseCode: '',
    courseTitle: '',
    description: ''
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSubmit(formData);
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <Label htmlFor="course_code">Course Code</Label>
        <Input
          id="course_code"
          value={formData.courseCode}
          onChange={(e) => setFormData(prev => ({ ...prev, courseCode: e.target.value }))}
          placeholder="e.g., CS101"
          required
        />
      </div>

      <div>
        <Label htmlFor="course_title">Course Title</Label>
        <Input
          id="course_title"
          value={formData.courseTitle}
          onChange={(e) => setFormData(prev => ({ ...prev, courseTitle: e.target.value }))}
          placeholder="e.g., Introduction to Computer Science"
          required
        />
      </div>
      
      <div>
        <Label htmlFor="description">Description</Label>
        <Textarea
          id="description"
          value={formData.description}
          onChange={(e) => setFormData(prev => ({ ...prev, description: e.target.value }))}
          placeholder="Course description..."
        />
      </div>
      
      <Button type="submit" className="w-full">Create Course</Button>
    </form>
  );
};

// Component for creating new sections
const parseTimeToMinutes = (time: string) => {
  if (!time) {
    return Number.NaN;
  }
  const [hours, minutes] = time.split(':').map((value) => Number.parseInt(value, 10));
  if (Number.isNaN(hours) || Number.isNaN(minutes)) {
    return Number.NaN;
  }
  return hours * 60 + minutes;
};

const validateTimeRange = (start: string, end: string): string | null => {
  if (!start || !end) {
    return null;
  }
  const startMinutes = parseTimeToMinutes(start);
  const endMinutes = parseTimeToMinutes(end);
  if (Number.isNaN(startMinutes) || Number.isNaN(endMinutes)) {
    return 'Enter a valid start and end time.';
  }
  if (startMinutes >= endMinutes) {
    return 'Start time must be earlier than end time.';
  }
  return null;
};

const validateCapacity = (value: number): string | null => {
  if (!Number.isFinite(value)) {
    return 'Enter a valid capacity.';
  }
  if (value <= 0) {
    return 'Capacity must be at least 1 student.';
  }
  return null;
};

const SectionForm = ({ courses, onSubmit }: { courses: Course[]; onSubmit: (data: SectionFormValues) => void }) => {
  const { toast } = useToast();
  const [timeError, setTimeError] = useState<string | null>(null);
  const [capacityError, setCapacityError] = useState<string | null>(null);
  const [formData, setFormData] = useState<SectionFormValues>({
    courseId: '',
    sectionCode: '',
    dayOfWeek: '',
    startTime: '',
    endTime: '',
    location: '',
    maxStudents: 50
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const validationMessage = validateTimeRange(formData.startTime, formData.endTime);
    if (validationMessage) {
      setTimeError(validationMessage);
      toast({
        title: 'Invalid time range',
        description: validationMessage,
        variant: 'destructive',
      });
      return;
    }
    const capacityMessage = validateCapacity(formData.maxStudents);
    if (capacityMessage) {
      setCapacityError(capacityMessage);
      toast({
        title: 'Invalid capacity',
        description: capacityMessage,
        variant: 'destructive',
      });
      return;
    }
    setCapacityError(null);
    onSubmit(formData);
  };

  const handleTimeChange = (field: 'startTime' | 'endTime', value: string) => {
    setFormData((prev) => {
      const next = { ...prev, [field]: value } as SectionFormValues;
      setTimeError(validateTimeRange(next.startTime, next.endTime));
      return next;
    });
  };

  const handleCapacityChange = (value: string) => {
    setFormData((prev) => {
      const parsed = Number.parseInt(value, 10);
      const sanitized = Number.isNaN(parsed) ? Number.NaN : parsed;
      setCapacityError(validateCapacity(sanitized));
      return { ...prev, maxStudents: sanitized };
    });
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <Label htmlFor="course_id">Course</Label>
        <Select value={formData.courseId} onValueChange={(value) =>
          setFormData(prev => ({ ...prev, courseId: value }))
        }>
          <SelectTrigger>
            <SelectValue placeholder="Select a course" />
          </SelectTrigger>
          <SelectContent>
            {courses.map((course) => (
              <SelectItem key={course.id} value={course.id}>
                {course.courseCode} - {course.courseTitle}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div>
        <Label htmlFor="section_code">Section Code</Label>
        <Input
          id="section_code"
          value={formData.sectionCode}
          onChange={(e) => setFormData(prev => ({ ...prev, sectionCode: e.target.value }))}
          placeholder="e.g., A, B, 001"
          required
        />
      </div>

      <div>
        <Label htmlFor="day_of_week">Day of Week</Label>
        <Select value={formData.dayOfWeek} onValueChange={(value) =>
          setFormData(prev => ({ ...prev, dayOfWeek: value }))
        }>
          <SelectTrigger>
            <SelectValue placeholder="Select day" />
          </SelectTrigger>
          <SelectContent>
            {WEEKDAY_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      
      <div className="grid grid-cols-2 gap-4">
        <div>
          <Label htmlFor="start_time">Start Time</Label>
          <Input
            id="start_time"
            type="time"
            value={formData.startTime}
            onChange={(e) => handleTimeChange('startTime', e.target.value)}
            required
          />
        </div>
        <div>
          <Label htmlFor="end_time">End Time</Label>
          <Input
            id="end_time"
            type="time"
            value={formData.endTime}
            onChange={(e) => handleTimeChange('endTime', e.target.value)}
            required
          />
        </div>
      </div>

      {timeError && (
        <p className="text-sm text-destructive" role="alert">
          {timeError}
        </p>
      )}

      <div>
        <Label htmlFor="location">Location</Label>
        <Input
          id="location"
          value={formData.location}
          onChange={(e) => setFormData(prev => ({ ...prev, location: e.target.value }))}
          placeholder="Room number or location"
        />
      </div>
      
      <div>
        <Label htmlFor="max_students">Maximum Students</Label>
        <Input
          id="max_students"
          type="number"
          value={Number.isNaN(formData.maxStudents) ? '' : formData.maxStudents}
          onChange={(e) => handleCapacityChange(e.target.value)}
          min="1"
          max="200"
        />
      </div>

      {capacityError && (
        <p className="text-sm text-destructive" role="alert">
          {capacityError}
        </p>
      )}

      <Button type="submit" className="w-full">Create Section</Button>
    </form>
  );
};

// Component for creating new sessions
const SessionForm = ({ onSubmit }: { onSubmit: (data: SessionFormValues) => void }) => {
  const [formData, setFormData] = useState<SessionFormValues>({
    sessionDate: '',
    startTime: '',
    location: '',
    notes: '',
    lateThresholdMinutes: 15
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const normalizedStart = formData.startTime ? new Date(formData.startTime) : new Date();
    const isoStart = formatDateWithOffset(Number.isNaN(normalizedStart.getTime()) ? new Date() : normalizedStart);
    onSubmit({
      sessionDate: formData.sessionDate,
      startTime: isoStart,
      location: formData.location || undefined,
      notes: formData.notes || undefined,
      lateThresholdMinutes: formData.lateThresholdMinutes
    });
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <Label htmlFor="session_date">Session Date</Label>
        <Input
          id="session_date"
          type="date"
          value={formData.sessionDate}
          onChange={(e) => setFormData(prev => ({ ...prev, sessionDate: e.target.value }))}
          required
        />
      </div>

      <div>
        <Label htmlFor="start_time">Start Time</Label>
        <Input
          id="start_time"
          type="datetime-local"
          value={formData.startTime}
          onChange={(e) => setFormData(prev => ({ ...prev, startTime: e.target.value }))}
          required
        />
      </div>
      
      <div>
        <Label htmlFor="location">Location (Optional)</Label>
        <Input
          id="location"
          value={formData.location}
          onChange={(e) => setFormData(prev => ({ ...prev, location: e.target.value }))}
          placeholder="Override section location"
        />
      </div>
      
      <div>
        <Label htmlFor="late_threshold">Late Threshold (minutes)</Label>
        <Input
          id="late_threshold"
          type="number"
          value={formData.lateThresholdMinutes}
          onChange={(e) => setFormData(prev => ({
            ...prev,
            lateThresholdMinutes: parseInt(e.target.value, 10)
          }))}
          min="0"
          max="60"
        />
      </div>
      
      <div>
        <Label htmlFor="notes">Notes</Label>
        <Textarea
          id="notes"
          value={formData.notes}
          onChange={(e) => setFormData(prev => ({ ...prev, notes: e.target.value }))}
          placeholder="Session notes..."
        />
      </div>
      
      <Button type="submit" className="w-full">Schedule Session</Button>
    </form>
  );
};

export default ProfessorCourses;