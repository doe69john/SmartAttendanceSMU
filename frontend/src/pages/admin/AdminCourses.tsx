import { useDeferredValue, useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { format } from 'date-fns';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Textarea } from '@/components/ui/textarea';
import { useToast } from '@/hooks/use-toast';
import {
  deleteCourse,
  fetchAdminCourseSections,
  fetchAdminCourseStudents,
  fetchAdminCourses,
  updateCourse,
  type AdminCourseSection,
  type AdminCourseStudent,
  type AdminCourseSummary,
} from '@/lib/api';
import {
  AlertTriangle,
  BookOpen,
  Calendar,
  Clock,
  MapPin,
  Loader2,
  Pencil,
  Search,
  TrendingUp,
  Trash2,
  User,
  Users,
} from 'lucide-react';

const WEEKDAY_LABELS: Record<number, string> = {
  0: 'Sunday',
  1: 'Monday',
  2: 'Tuesday',
  3: 'Wednesday',
  4: 'Thursday',
  5: 'Friday',
  6: 'Saturday',
  7: 'Sunday',
};

type CourseEditForm = {
  courseCode: string;
  courseTitle: string;
  description: string;
};

const formatTime = (value?: string | null) => {
  if (!value) {
    return '—';
  }
  const [hours, minutes] = value.split(':').map((segment) => Number.parseInt(segment, 10));
  if (Number.isNaN(hours) || Number.isNaN(minutes)) {
    return value;
  }
  const reference = new Date();
  reference.setHours(hours, minutes, 0, 0);
  return format(reference, 'h:mm a');
};

const formatPercent = (value?: number | null) => {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '—';
  }
  return `${Math.round(value * 100)}%`;
};

const formatPluralLabel = (count: number, singular: string, plural?: string) => {
  const normalized = Number.isFinite(count) ? count : 0;
  if (normalized === 1) {
    return `1 ${singular}`;
  }
  return `${normalized} ${plural ?? `${singular}s`}`;
};

const getWeekdayLabel = (value?: number | null) => {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return 'Day not set';
  }
  const rounded = Math.trunc(value);
  if (rounded >= 1 && rounded <= 7) {
    return WEEKDAY_LABELS[rounded];
  }
  const wrapped = ((rounded % 7) + 7) % 7;
  const key = wrapped === 0 ? 0 : wrapped;
  return WEEKDAY_LABELS[key] ?? 'Day not set';
};

const formatTimeRange = (start?: string | null, end?: string | null) => {
  if (!start && !end) {
    return 'Time not set';
  }
  if (!end) {
    return formatTime(start);
  }
  return `${formatTime(start)} – ${formatTime(end)}`;
};

export default function AdminCourses() {
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const [courseSearch, setCourseSearch] = useState('');
  const deferredCourseSearch = useDeferredValue(courseSearch);
  const normalizedCourseSearch = deferredCourseSearch.trim();

  const [selectedCourseId, setSelectedCourseId] = useState<string | null>(null);
  const [isSheetOpen, setIsSheetOpen] = useState(false);
  const [activeTab, setActiveTab] = useState<'sections' | 'students'>('sections');
  const [sectionSearch, setSectionSearch] = useState('');
  const [studentSearch, setStudentSearch] = useState('');
  const deferredSectionSearch = useDeferredValue(sectionSearch);
  const deferredStudentSearch = useDeferredValue(studentSearch);

  const [courseToEdit, setCourseToEdit] = useState<AdminCourseSummary | null>(null);
  const [editForm, setEditForm] = useState<CourseEditForm>({
    courseCode: '',
    courseTitle: '',
    description: '',
  });
  const [courseToDelete, setCourseToDelete] = useState<AdminCourseSummary | null>(null);

  const {
    data: courses = [],
    isLoading: coursesLoading,
    isFetching: coursesFetching,
    isError: coursesError,
  } = useQuery({
    queryKey: ['admin-courses', normalizedCourseSearch],
    queryFn: () =>
      fetchAdminCourses(normalizedCourseSearch ? { query: normalizedCourseSearch } : {}),
    staleTime: 30_000,
  });

  const selectedCourse = useMemo<AdminCourseSummary | null>(() => {
    if (!selectedCourseId) {
      return null;
    }
    return courses.find((course) => course.courseId === selectedCourseId) ?? null;
  }, [courses, selectedCourseId]);

  const normalizedSectionSearch = deferredSectionSearch.trim();
  const normalizedStudentSearch = deferredStudentSearch.trim();

  const {
    data: courseSections = [],
    isLoading: sectionsLoading,
    isFetching: sectionsFetching,
    isError: sectionsError,
  } = useQuery({
    queryKey: ['admin-course-sections', selectedCourse?.courseId, normalizedSectionSearch],
    queryFn: () =>
      fetchAdminCourseSections(
        selectedCourse!.courseId!,
        normalizedSectionSearch ? { query: normalizedSectionSearch } : {},
      ),
    enabled: Boolean(selectedCourse?.courseId),
    staleTime: 30_000,
  });

  const {
    data: courseStudents = [],
    isLoading: studentsLoading,
    isFetching: studentsFetching,
    isError: studentsError,
  } = useQuery({
    queryKey: ['admin-course-students', selectedCourse?.courseId, normalizedStudentSearch],
    queryFn: () =>
      fetchAdminCourseStudents(
        selectedCourse!.courseId!,
        normalizedStudentSearch ? { query: normalizedStudentSearch } : {},
      ),
    enabled: Boolean(selectedCourse?.courseId),
    staleTime: 30_000,
  });

  useEffect(() => {
    if (!isSheetOpen || !selectedCourseId) {
      return;
    }
    setSectionSearch('');
    setStudentSearch('');
    setActiveTab('sections');
  }, [isSheetOpen, selectedCourseId]);

  useEffect(() => {
    if (!isSheetOpen) {
      return;
    }
    if (!selectedCourseId || !selectedCourse) {
      setIsSheetOpen(false);
      setSelectedCourseId(null);
    }
  }, [courses, isSheetOpen, selectedCourse, selectedCourseId]);

  useEffect(() => {
    if (!courseToEdit) {
      return;
    }
    setEditForm({
      courseCode: courseToEdit.courseCode ?? '',
      courseTitle: courseToEdit.courseTitle ?? '',
      description: courseToEdit.description ?? '',
    });
  }, [courseToEdit]);

  const updateCourseMutation = useMutation({
    mutationFn: async (values: CourseEditForm) => {
      if (!courseToEdit?.courseId) {
        throw new Error('Course identifier is missing.');
      }
      return updateCourse(courseToEdit.courseId, {
        courseCode: values.courseCode.trim(),
        courseTitle: values.courseTitle.trim(),
        description: values.description.trim() || undefined,
      });
    },
    onSuccess: () => {
      toast({ title: 'Course updated', description: 'The course details have been saved.' });
      setCourseToEdit(null);
      queryClient.invalidateQueries({ queryKey: ['admin-courses'] });
    },
    onError: (error) => {
      const message = error instanceof Error ? error.message : 'Unable to update course.';
      toast({ title: 'Update failed', description: message, variant: 'destructive' });
    },
  });

  const deleteCourseMutation = useMutation({
    mutationFn: async (courseId: string) => {
      await deleteCourse(courseId);
    },
    onSuccess: (_, courseId) => {
      toast({
        title: 'Course deleted',
        description: 'The course and its related sections have been permanently removed.',
      });
      if (selectedCourseId === courseId) {
        setIsSheetOpen(false);
        setSelectedCourseId(null);
      }
      setCourseToDelete(null);
      queryClient.invalidateQueries({ queryKey: ['admin-courses'] });
      queryClient.invalidateQueries({ queryKey: ['admin-course-sections'] });
      queryClient.invalidateQueries({ queryKey: ['admin-course-students'] });
    },
    onError: (error) => {
      const message = error instanceof Error ? error.message : 'Unable to delete course.';
      toast({ title: 'Delete failed', description: message, variant: 'destructive' });
    },
  });

  const handleCourseClick = (course: AdminCourseSummary) => {
    if (!course.courseId) {
      toast({
        title: 'Missing course identifier',
        description: 'Unable to open course details without a valid identifier.',
        variant: 'destructive',
      });
      return;
    }
    setSelectedCourseId(course.courseId);
    setIsSheetOpen(true);
  };

  const handleEditSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    updateCourseMutation.mutate(editForm);
  };

  const isCoursesBusy = coursesLoading || coursesFetching;
  const isSectionsBusy = sectionsLoading || sectionsFetching;
  const isStudentsBusy = studentsLoading || studentsFetching;

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Course Administration</h1>
          <p className="text-muted-foreground">
            Review course performance, manage metadata, and inspect enrollment health.
          </p>
        </div>
      </div>

      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative w-full sm:max-w-md">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={courseSearch}
            onChange={(event) => setCourseSearch(event.target.value)}
            placeholder="Search courses by code or title"
            className="pl-9"
            aria-label="Search courses"
          />
        </div>
        {isCoursesBusy && (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" />
            Syncing latest course data…
          </div>
        )}
      </div>

      {coursesError ? (
        <Card className="border-destructive/50 bg-destructive/5">
          <CardHeader className="flex flex-row items-center gap-3">
            <AlertTriangle className="h-5 w-5 text-destructive" />
            <div>
              <CardTitle className="text-destructive">Failed to load courses</CardTitle>
              <CardDescription className="text-destructive/80">
                Please try again shortly.
              </CardDescription>
            </div>
          </CardHeader>
        </Card>
      ) : courses.length === 0 && !isCoursesBusy ? (
        <Card className="border-dashed">
          <CardContent className="flex flex-col items-center justify-center gap-3 py-12 text-center">
            <BookOpen className="h-10 w-10 text-muted-foreground" />
            <div className="space-y-1">
              <h3 className="text-lg font-semibold">No courses found</h3>
              <p className="text-sm text-muted-foreground">
                Once courses are created they will appear here with live enrollment metrics.
              </p>
            </div>
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2 xl:grid-cols-3">
          {courses.map((course, index) => {
            const isActive = course.active ?? true;
            return (
              <Card
                key={course.courseId ?? `${course.courseCode ?? 'course'}-${index}`}
                className="cursor-pointer transition hover:border-primary/60"
                onClick={() => handleCourseClick(course)}
              >
                <CardHeader className="space-y-1">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <CardTitle className="flex flex-wrap items-center gap-2">
                        <span>{course.courseCode ?? 'Unnamed course'}</span>
                        <Badge variant={isActive ? 'default' : 'secondary'}>
                          {isActive ? 'Active' : 'Inactive'}
                        </Badge>
                      </CardTitle>
                      <CardDescription className="text-sm text-muted-foreground">
                        {course.courseTitle ?? 'No course title provided'}
                      </CardDescription>
                    </div>
                    <div className="flex gap-2">
                      <Button
                        variant="outline"
                        size="icon"
                        onClick={(event) => {
                          event.stopPropagation();
                          if (!course.courseId) {
                            toast({
                              title: 'Missing course identifier',
                              description: 'Unable to edit this course without a valid identifier.',
                              variant: 'destructive',
                            });
                            return;
                          }
                          setCourseToEdit(course);
                        }}
                        aria-label="Edit course"
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="outline"
                        size="icon"
                        className="text-destructive hover:text-destructive"
                        onClick={(event) => {
                          event.stopPropagation();
                          if (!course.courseId) {
                            toast({
                              title: 'Missing course identifier',
                              description: 'Unable to delete this course without a valid identifier.',
                              variant: 'destructive',
                            });
                            return;
                          }
                          setCourseToDelete(course);
                        }}
                        aria-label="Delete course"
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>
                </CardHeader>
                <CardContent className="space-y-3">
                  <p className="line-clamp-2 text-sm text-muted-foreground">
                    {course.description?.trim() || 'No description provided.'}
                  </p>
                  <div className="flex flex-wrap gap-4 text-sm text-muted-foreground">
                    <div className="flex items-center gap-2">
                      <BookOpen className="h-4 w-4" />
                      <span>{formatPluralLabel(course.sectionCount ?? 0, 'section')}</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <Users className="h-4 w-4" />
                      <span>{formatPluralLabel(course.studentCount ?? 0, 'student')}</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <User className="h-4 w-4" />
                      <span>{formatPluralLabel(course.professorCount ?? 0, 'professor')}</span>
                    </div>
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}

      <Sheet
        open={isSheetOpen && Boolean(selectedCourse)}
        onOpenChange={(open) => {
          setIsSheetOpen(open);
          if (!open) {
            setSelectedCourseId(null);
          }
        }}
      >
        <SheetContent
          side="top"
          className="flex h-full w-full max-h-screen flex-col overflow-hidden p-0 sm:rounded-t-lg"
        >
          <div className="space-y-4 border-b p-6">
            <SheetHeader className="space-y-3 text-left">
              <SheetTitle className="flex flex-wrap items-center gap-3">
                {selectedCourse?.courseCode}
                {selectedCourse && (
                  <Badge variant={selectedCourse.active ?? true ? 'default' : 'secondary'}>
                    {selectedCourse.active ?? true ? 'Active' : 'Inactive'}
                  </Badge>
                )}
              </SheetTitle>
              <SheetDescription className="text-base text-muted-foreground">
                {selectedCourse?.courseTitle}
              </SheetDescription>
            </SheetHeader>
            <div className="grid gap-3 sm:grid-cols-3">
              <div className="rounded-lg border p-4">
                <div className="text-xs font-medium uppercase text-muted-foreground">Sections</div>
                <div className="text-2xl font-semibold">{selectedCourse?.sectionCount ?? 0}</div>
              </div>
              <div className="rounded-lg border p-4">
                <div className="text-xs font-medium uppercase text-muted-foreground">Students</div>
                <div className="text-2xl font-semibold">{selectedCourse?.studentCount ?? 0}</div>
              </div>
              <div className="rounded-lg border p-4">
                <div className="text-xs font-medium uppercase text-muted-foreground">Professors</div>
                <div className="text-2xl font-semibold">{selectedCourse?.professorCount ?? 0}</div>
              </div>
            </div>
          </div>

          <Tabs
            value={activeTab}
            onValueChange={(value) => setActiveTab(value as 'sections' | 'students')}
            className="flex h-full flex-1 flex-col overflow-hidden"
          >
            <div className="flex flex-col gap-4 border-b px-6 py-4 sm:flex-row sm:items-center sm:justify-between">
              <TabsList className="grid w-full gap-2 sm:inline-flex sm:w-auto">
                <TabsTrigger value="sections" className="flex items-center gap-2">
                  <BookOpen className="h-4 w-4" /> Sections
                </TabsTrigger>
                <TabsTrigger value="students" className="flex items-center gap-2">
                  <Users className="h-4 w-4" /> Students
                </TabsTrigger>
              </TabsList>

              {activeTab === 'sections' ? (
                <div className="relative w-full sm:max-w-xs">
                  <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    value={sectionSearch}
                    onChange={(event) => setSectionSearch(event.target.value)}
                    placeholder="Search sections"
                    className="pl-9"
                    aria-label="Search sections"
                    disabled={!selectedCourse}
                  />
                </div>
              ) : (
                <div className="relative w-full sm:max-w-xs">
                  <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    value={studentSearch}
                    onChange={(event) => setStudentSearch(event.target.value)}
                    placeholder="Search students"
                    className="pl-9"
                    aria-label="Search students"
                    disabled={!selectedCourse}
                  />
                </div>
              )}
            </div>

            <div className="flex-1 overflow-hidden">
              <TabsContent value="sections" className="h-full overflow-hidden">
                <ScrollArea className="h-full px-6 pb-10">
                  {sectionsError ? (
                    <div className="flex flex-col items-center justify-center gap-2 rounded-lg border border-destructive/40 bg-destructive/5 p-6 text-center text-sm text-destructive">
                      <AlertTriangle className="h-5 w-5" />
                      Unable to load sections for this course.
                    </div>
                  ) : isSectionsBusy ? (
                    <div className="flex flex-col items-center justify-center gap-2 py-12 text-muted-foreground">
                      <Loader2 className="h-5 w-5 animate-spin" />
                      Loading sections…
                    </div>
                  ) : courseSections.length === 0 ? (
                    <div className="flex flex-col items-center justify-center gap-2 py-12 text-muted-foreground">
                      <BookOpen className="h-6 w-6" />
                      No sections found for this course.
                    </div>
                  ) : (
                    <div className="space-y-4 pb-2">
                      {courseSections.map((section: AdminCourseSection) => (
                        <div
                          key={section.sectionId ?? section.sectionCode}
                          className="rounded-lg border p-4 shadow-sm"
                        >
                          <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                            <div className="space-y-1">
                              <div className="flex flex-wrap items-center gap-2 text-lg font-semibold">
                                {section.sectionCode ?? 'Uncoded section'}
                                <Badge variant={(section.active ?? true) ? 'default' : 'secondary'}>
                                  {(section.active ?? true) ? 'Active' : 'Inactive'}
                                </Badge>
                              </div>
                              <div className="text-sm text-muted-foreground">
                                {section.professorName?.trim() || 'Professor not assigned'}
                              </div>
                            </div>
                            <div className="flex flex-col items-start gap-2 text-sm text-muted-foreground md:items-end">
                              <div className="flex items-center gap-2">
                                <Calendar className="h-4 w-4" />
                                <span>{getWeekdayLabel(section.dayOfWeek)}</span>
                              </div>
                              {(section.startTime || section.endTime) && (
                                <div className="flex items-center gap-2">
                                  <Clock className="h-4 w-4" />
                                  <span>{formatTimeRange(section.startTime, section.endTime)}</span>
                                </div>
                              )}
                            </div>
                          </div>
                          <div className="mt-4 grid gap-3 text-sm text-muted-foreground sm:grid-cols-2 lg:grid-cols-4">
                            <div className="flex items-center gap-2">
                              <Users className="h-4 w-4" />
                              <span>{formatPluralLabel(section.studentCount ?? 0, 'student')}</span>
                            </div>
                            <div className="flex items-center gap-2">
                              <BookOpen className="h-4 w-4" />
                              <span>{formatPluralLabel(section.sessionCount ?? 0, 'session')}</span>
                            </div>
                            <div className="flex items-center gap-2">
                              <TrendingUp className="h-4 w-4" />
                              <span>{formatPercent(section.attendanceRate)}</span>
                            </div>
                            <div className="flex items-center gap-2">
                              <MapPin className="h-4 w-4" />
                              <span className="truncate">
                                {section.location?.trim() || 'Location not set'}
                              </span>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </ScrollArea>
              </TabsContent>
            <TabsContent value="students" className="h-full overflow-hidden">
              <ScrollArea className="h-full px-6 pb-10">
                {studentsError ? (
                  <div className="flex flex-col items-center justify-center gap-2 rounded-lg border border-destructive/40 bg-destructive/5 p-6 text-center text-sm text-destructive">
                    <AlertTriangle className="h-5 w-5" />
                    Unable to load students for this course.
                  </div>
                ) : isStudentsBusy ? (
                  <div className="flex flex-col items-center justify-center gap-2 py-12 text-muted-foreground">
                    <Loader2 className="h-5 w-5 animate-spin" />
                    Loading students…
                  </div>
                ) : courseStudents.length === 0 ? (
                  <div className="flex flex-col items-center justify-center gap-2 py-12 text-muted-foreground">
                    <Users className="h-6 w-6" />
                    No active students found for this course.
                  </div>
                ) : (
                  <div className="space-y-4 pb-2">
                    {courseStudents.map((student: AdminCourseStudent) => (
                      <div key={`${student.studentId}-${student.sectionId}`} className="rounded-lg border p-4 shadow-sm">
                        <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                          <div className="space-y-1">
                            <div className="text-lg font-semibold">{student.fullName ?? 'Unnamed student'}</div>
                            <div className="text-sm text-muted-foreground">
                              {student.email ?? 'No email recorded'}
                            </div>
                          </div>
                          <Badge variant="outline">Section {student.sectionCode ?? '—'}</Badge>
                        </div>
                        <div className="mt-4 grid gap-3 text-sm text-muted-foreground sm:grid-cols-2 lg:grid-cols-4">
                          <div className="flex items-center gap-2">
                            <User className="h-4 w-4" />
                            <span>{student.studentNumber ?? 'Student number unavailable'}</span>
                          </div>
                          <div className="flex items-center gap-2">
                            <TrendingUp className="h-4 w-4" />
                            <span>{formatPercent(student.attendanceRate)}</span>
                          </div>
                          <div className="flex items-center gap-2">
                            <Calendar className="h-4 w-4" />
                            <span>{student.recordedSessions ?? 0}/{student.totalSessions ?? 0} recorded sessions</span>
                          </div>
                          <div className="flex items-center gap-2">
                            <BookOpen className="h-4 w-4" />
                            <span>{formatPluralLabel(student.totalSessions ?? 0, 'scheduled session')}</span>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </ScrollArea>
            </TabsContent>
          </div>
          </Tabs>
        </SheetContent>
      </Sheet>

      <Dialog
        open={Boolean(courseToEdit)}
        onOpenChange={(open) => {
          if (!open) {
            setCourseToEdit(null);
          }
        }}
      >
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>Edit course</DialogTitle>
            <DialogDescription>
              Update the course metadata. Changes are immediately reflected across the platform.
            </DialogDescription>
          </DialogHeader>
          <form className="space-y-4" onSubmit={handleEditSubmit}>
            <div className="space-y-2">
              <Label htmlFor="courseCode">Course code</Label>
              <Input
                id="courseCode"
                value={editForm.courseCode}
                onChange={(event) =>
                  setEditForm((prev) => ({ ...prev, courseCode: event.target.value }))
                }
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="courseTitle">Course title</Label>
              <Input
                id="courseTitle"
                value={editForm.courseTitle}
                onChange={(event) =>
                  setEditForm((prev) => ({ ...prev, courseTitle: event.target.value }))
                }
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="courseDescription">Description</Label>
              <Textarea
                id="courseDescription"
                value={editForm.description}
                onChange={(event) =>
                  setEditForm((prev) => ({ ...prev, description: event.target.value }))
                }
                placeholder="Provide context that helps administrators and professors understand this course."
                rows={4}
              />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setCourseToEdit(null)}>
                Cancel
              </Button>
              <Button type="submit" disabled={updateCourseMutation.isPending}>
                {updateCourseMutation.isPending ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" /> Saving…
                  </>
                ) : (
                  'Save changes'
                )}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <AlertDialog
        open={Boolean(courseToDelete)}
        onOpenChange={(open) => {
          if (!open) {
            setCourseToDelete(null);
          }
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete this course?</AlertDialogTitle>
            <AlertDialogDescription>
              This action permanently removes the course, its sections, sessions, and enrollments. This cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleteCourseMutation.isPending}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => {
                if (!courseToDelete?.courseId) {
                  toast({
                    title: 'Missing course identifier',
                    description: 'Unable to delete this course without a valid identifier.',
                    variant: 'destructive',
                  });
                  return;
                }
                deleteCourseMutation.mutate(courseToDelete.courseId);
              }}
              disabled={deleteCourseMutation.isPending}
            >
              {deleteCourseMutation.isPending ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" /> Deleting…
                </>
              ) : (
                'Delete course'
              )}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
