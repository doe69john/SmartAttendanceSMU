import { useState, useEffect, useCallback } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Search, Plus, Edit, Trash2, Users, Calendar } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  ApiError,
  createCourse,
  deleteCourse,
  fetchCourses,
  fetchProfessors,
  fetchSections,
  updateCourse,
} from '@/lib/api';
import type { CourseSummary, ProfessorDirectoryEntry, SectionSummary } from '@/lib/api';

const getErrorMessage = (error: unknown, fallback: string) =>
  error instanceof ApiError ? error.message : fallback;

type Section = SectionSummary;

type ProfessorSummary = ProfessorDirectoryEntry;

type Course = CourseSummary & {
  createdAt?: string;
  sections: Section[];
};

interface CourseFormData {
  courseCode: string;
  courseTitle: string;
  description: string;
  active: boolean;
}

const DAYS_OF_WEEK = [
  'Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'
];

export const EnhancedCourseManagement = () => {
  const { toast } = useToast();
  const [courses, setCourses] = useState<Course[]>([]);
  const [, setProfessors] = useState<ProfessorSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
  const [editingCourse, setEditingCourse] = useState<Course | null>(null);
  const [courseForm, setCourseForm] = useState<CourseFormData>({
    courseCode: '',
    courseTitle: '',
    description: '',
    active: true
  });

  const loadAdminData = useCallback(async () => {
    try {
      setLoading(true);

      let professorDirectory: ProfessorSummary[] = [];
      try {
        professorDirectory = await fetchProfessors();
      } catch (error) {
        console.error('Error loading professors:', error);
        toast({
          title: "Warning",
          description: getErrorMessage(error, 'Failed to load professors'),
          variant: "destructive",
        });
      }
      setProfessors(professorDirectory ?? []);

      let sectionSummaries: Section[] = [];
      try {
        sectionSummaries = await fetchSections();
      } catch (error) {
        console.error('Error loading sections:', error);
        toast({
          title: "Warning",
          description: getErrorMessage(error, 'Failed to load sections'),
          variant: "destructive",
        });
      }

      const sectionsByCourse = new Map<string, Section[]>();
      sectionSummaries.forEach(section => {
        if (!section.courseId) {
          return;
        }
        if (!sectionsByCourse.has(section.courseId)) {
          sectionsByCourse.set(section.courseId, []);
        }
        sectionsByCourse.get(section.courseId)!.push(section);
      });

      const courseSummaries = await fetchCourses();
      const normalizedCourses: Course[] = courseSummaries.map(course => ({
        ...course,
        createdAt: undefined,
        sections: sectionsByCourse.get(course.id) ?? [],
      }));

      setCourses(normalizedCourses);
    } catch (error) {
      console.error('Error loading courses:', error);
      toast({
        title: "Error",
        description: getErrorMessage(error, 'Failed to load courses'),
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    loadAdminData();
  }, [loadAdminData]);

  const handleCreateCourse = async () => {
    try {
      await createCourse({
        courseCode: courseForm.courseCode,
        courseTitle: courseForm.courseTitle,
        description: courseForm.description,
        active: courseForm.active,
      });

      toast({
        title: "Success",
        description: "Course created successfully",
      });

      setIsCreateDialogOpen(false);
      resetForm();
      loadAdminData();
    } catch (error) {
      console.error('Error creating course:', error);
      toast({
        title: "Error",
        description: getErrorMessage(error, 'Failed to create course'),
        variant: "destructive",
      });
    }
  };

  const handleUpdateCourse = async () => {
    if (!editingCourse) return;

    try {
      await updateCourse(editingCourse.id, {
        courseCode: courseForm.courseCode,
        courseTitle: courseForm.courseTitle,
        description: courseForm.description,
        active: courseForm.active,
      });

      toast({
        title: "Success",
        description: "Course updated successfully",
      });

      setIsEditDialogOpen(false);
      setEditingCourse(null);
      resetForm();
      loadAdminData();
    } catch (error) {
      console.error('Error updating course:', error);
      toast({
        title: "Error",
        description: getErrorMessage(error, 'Failed to update course'),
        variant: "destructive",
      });
    }
  };

  const handleDeleteCourse = async (courseId: string) => {
    try {
      await deleteCourse(courseId);

      toast({
        title: "Success",
        description: "Course deleted successfully",
      });

      loadAdminData();
    } catch (error) {
      console.error('Error deleting course:', error);
      toast({
        title: "Error",
        description: getErrorMessage(error, 'Failed to delete course'),
        variant: "destructive",
      });
    }
  };

  const resetForm = () => {
    setCourseForm({
      courseCode: '',
      courseTitle: '',
      description: '',
      active: true,
    });
  };

  const openEditDialog = (course: Course) => {
    setEditingCourse(course);
    setCourseForm({
      courseCode: course.courseCode,
      courseTitle: course.courseTitle,
      description: course.description ?? '',
      active: course.active,
    });
    setIsEditDialogOpen(true);
  };

  const filteredCourses = courses.filter(course =>
    course.courseCode.toLowerCase().includes(searchTerm.toLowerCase()) ||
    course.courseTitle.toLowerCase().includes(searchTerm.toLowerCase())
  );

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-primary"></div>
      </div>
    );
  }

  return (
    <div className="container mx-auto py-6 space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold">Course Management</h1>
        <Dialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen}>
          <DialogTrigger asChild>
            <Button onClick={resetForm}>
              <Plus className="w-4 h-4 mr-2" />
              Add Course
            </Button>
          </DialogTrigger>
          <DialogContent className="sm:max-w-[425px]">
            <DialogHeader>
              <DialogTitle>Create New Course</DialogTitle>
              <DialogDescription>
                Add a new course to the system. You can add sections later.
              </DialogDescription>
            </DialogHeader>
            <div className="grid gap-4 py-4">
              <div className="grid grid-cols-4 items-center gap-4">
                <Label htmlFor="course_code" className="text-right">
                  Course Code
                </Label>
                <Input
                  id="course_code"
                  value={courseForm.courseCode}
                  onChange={(e) => setCourseForm(prev => ({...prev, courseCode: e.target.value}))}
                  className="col-span-3"
                  placeholder="CS101"
                />
              </div>
              <div className="grid grid-cols-4 items-center gap-4">
                <Label htmlFor="course_title" className="text-right">
                  Course Title
                </Label>
                <Input
                  id="course_title"
                  value={courseForm.courseTitle}
                  onChange={(e) => setCourseForm(prev => ({...prev, courseTitle: e.target.value}))}
                  className="col-span-3"
                  placeholder="Introduction to Computer Science"
                />
              </div>
              <div className="grid grid-cols-4 items-center gap-4">
                <Label htmlFor="description" className="text-right">
                  Description
                </Label>
                <Textarea
                  id="description"
                  value={courseForm.description}
                  onChange={(e) => setCourseForm(prev => ({...prev, description: e.target.value}))}
                  className="col-span-3"
                  placeholder="Course description..."
                />
              </div>
              <div className="grid grid-cols-4 items-center gap-4">
                <Label htmlFor="is_active" className="text-right">
                  Status
                </Label>
                <Select
                  value={courseForm.active.toString()}
                  onValueChange={(value) => setCourseForm(prev => ({...prev, active: value === 'true'}))}
                >
                  <SelectTrigger className="col-span-3">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="true">Active</SelectItem>
                    <SelectItem value="false">Inactive</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
            <DialogFooter>
              <Button type="submit" onClick={handleCreateCourse}>
                Create Course
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>

      {/* Search */}
      <Card>
        <CardContent className="pt-6">
          <div className="relative">
            <Search className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Search courses by code or title..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="pl-10"
            />
          </div>
        </CardContent>
      </Card>

      {/* Courses Table */}
      <Card>
        <CardHeader>
          <CardTitle>Courses ({filteredCourses.length})</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Course Code</TableHead>
                <TableHead>Course Title</TableHead>
                <TableHead>Sections</TableHead>
                <TableHead>Total Students</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Created</TableHead>
                <TableHead>Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredCourses.map((course) => (
                <TableRow key={course.id}>
                  <TableCell className="font-mono font-medium">
                    {course.courseCode}
                  </TableCell>
                  <TableCell>{course.courseTitle}</TableCell>
                  <TableCell>
                    <div className="flex items-center space-x-2">
                      <Calendar className="w-4 h-4" />
                      <span>{course.sections.length}</span>
                    </div>
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center space-x-2">
                      <Users className="w-4 h-4" />
                      <span>
                        {course.sections.reduce((total, section) =>
                          total + (section.enrolledCount ?? 0), 0
                        )}
                      </span>
                    </div>
                  </TableCell>
                  <TableCell>
                    <Badge variant={course.active ? "default" : "secondary"}>
                      {course.active ? "Active" : "Inactive"}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {course.createdAt ? new Date(course.createdAt).toLocaleDateString() : 'N/A'}
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center space-x-2">
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => openEditDialog(course)}
                      >
                        <Edit className="w-4 h-4" />
                      </Button>
                      <Button
                        size="sm"
                        variant="destructive"
                        onClick={() => handleDeleteCourse(course.id)}
                      >
                        <Trash2 className="w-4 h-4" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>

          {filteredCourses.length === 0 && (
            <div className="text-center py-8 text-muted-foreground">
              No courses found matching your search.
            </div>
          )}
        </CardContent>
      </Card>

      {/* Edit Dialog */}
      <Dialog open={isEditDialogOpen} onOpenChange={setIsEditDialogOpen}>
        <DialogContent className="sm:max-w-[425px]">
          <DialogHeader>
            <DialogTitle>Edit Course</DialogTitle>
            <DialogDescription>
              Update course information.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="edit_course_code" className="text-right">
                Course Code
              </Label>
              <Input
                id="edit_course_code"
                value={courseForm.courseCode}
                onChange={(e) => setCourseForm(prev => ({...prev, courseCode: e.target.value}))}
                className="col-span-3"
              />
            </div>
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="edit_course_title" className="text-right">
                Course Title
              </Label>
              <Input
                id="edit_course_title"
                value={courseForm.courseTitle}
                onChange={(e) => setCourseForm(prev => ({...prev, courseTitle: e.target.value}))}
                className="col-span-3"
              />
            </div>
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="edit_description" className="text-right">
                Description
              </Label>
              <Textarea
                id="edit_description"
                value={courseForm.description}
                onChange={(e) => setCourseForm(prev => ({...prev, description: e.target.value}))}
                className="col-span-3"
              />
            </div>
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="edit_is_active" className="text-right">
                Status
              </Label>
              <Select
                value={courseForm.active.toString()}
                onValueChange={(value) => setCourseForm(prev => ({...prev, active: value === 'true'}))}
              >
                <SelectTrigger className="col-span-3">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="true">Active</SelectItem>
                  <SelectItem value="false">Inactive</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button type="submit" onClick={handleUpdateCourse}>
              Update Course
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};