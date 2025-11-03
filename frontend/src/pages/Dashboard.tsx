import { Navigate } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { 
  Users, 
  BookOpen, 
  Calendar, 
  TrendingUp, 
  Clock,
  CheckCircle,
  XCircle,
  AlertCircle,
  Camera
} from 'lucide-react';
import { useState, useEffect, useCallback } from 'react';
import { fetchProfessorDashboard, fetchStudentDashboard, type ProfessorDashboardSummary } from '@/lib/api';

interface DashboardStats {
  totalSections: number;
  totalStudents: number;
  upcomingSessions: number;
  activeSessions: number;
}

export default function Dashboard() {
  const { profile, isStudent, isProfessor } = useAuth();
  const [stats, setStats] = useState<DashboardStats>({
    totalSections: 0,
    totalStudents: 0,
    upcomingSessions: 0,
    activeSessions: 0,
  });
  const [loading, setLoading] = useState(true);

  const fetchDashboardData = useCallback(async () => {
    if (!profile) return;

    try {
      if (isProfessor) {
        const data = await fetchProfessorDashboard() as ProfessorDashboardSummary;
        setStats({
          totalSections: data.total_sections ?? 0,
          totalStudents: data.total_students ?? 0,
          upcomingSessions: data.upcoming_sessions ?? 0,
          activeSessions: data.active_sessions ?? 0,
        });
      } else if (isStudent) {
        await fetchStudentDashboard();
        // Student dashboard data can be surfaced in dedicated UI blocks later.
      }
    } catch (error) {
      console.error('Error fetching dashboard data:', error);
    } finally {
      setLoading(false);
    }
  }, [profile, isProfessor, isStudent]);

  useEffect(() => {
    if (isProfessor) {
      setLoading(false);
      return;
    }

    void fetchDashboardData();
  }, [fetchDashboardData, isProfessor]);

  if (isProfessor) {
    return <Navigate to="/sections" replace />;
  }

  if (loading) {
    return (
      <div className="space-y-6">
        <div className="animate-pulse">
          <div className="h-8 bg-muted rounded w-1/3 mb-4"></div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="h-32 bg-muted rounded-lg"></div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gradient">
            Welcome back, {profile?.fullName}
          </h1>
          <p className="text-muted-foreground mt-1">
            Here's what's happening with your {isProfessor ? 'classes' : 'courses'} today.
          </p>
        </div>
        
        {isProfessor && (
          <Button className="btn-gradient">
            <Camera className="h-4 w-4 mr-2" />
            Start Live Session
          </Button>
        )}
      </div>

      {/* Stats Cards */}
      {isProfessor && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          <Card className="card-gradient">
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Total Sections</CardTitle>
              <BookOpen className="h-4 w-4 text-primary" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{stats.totalSections}</div>
              <p className="text-xs text-muted-foreground">
                Active sections you teach
              </p>
            </CardContent>
          </Card>

          <Card className="card-gradient">
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Total Students</CardTitle>
              <Users className="h-4 w-4 text-success" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{stats.totalStudents}</div>
              <p className="text-xs text-muted-foreground">
                Enrolled across all sections
              </p>
            </CardContent>
          </Card>

          <Card className="card-gradient">
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Upcoming Sessions</CardTitle>
              <Calendar className="h-4 w-4 text-warning" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{stats.upcomingSessions}</div>
              <p className="text-xs text-muted-foreground">
                Scheduled for today
              </p>
            </CardContent>
          </Card>

          <Card className="card-gradient">
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Active Sessions</CardTitle>
              <Clock className="h-4 w-4 text-destructive" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{stats.activeSessions}</div>
              <p className="text-xs text-muted-foreground">
                Currently running
              </p>
            </CardContent>
          </Card>
        </div>
      )}

      {/* Quick Actions */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card className="card-gradient">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <TrendingUp className="h-5 w-5 text-primary" />
              Quick Actions
            </CardTitle>
            <CardDescription>
              Common tasks and shortcuts
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {isStudent && (
              <>
                <Button variant="outline" className="w-full justify-start">
                  <Camera className="h-4 w-4 mr-2" />
                  Setup Face Recognition
                </Button>
                <Button variant="outline" className="w-full justify-start">
                  <BookOpen className="h-4 w-4 mr-2" />
                  View My Courses
                </Button>
                <Button variant="outline" className="w-full justify-start">
                  <CheckCircle className="h-4 w-4 mr-2" />
                  Check Attendance
                </Button>
              </>
            )}
            
            {isProfessor && (
              <>
                <Button variant="outline" className="w-full justify-start">
                  <Camera className="h-4 w-4 mr-2" />
                  Start Live Session
                </Button>
                <Button variant="outline" className="w-full justify-start">
                  <Users className="h-4 w-4 mr-2" />
                  Manage Students
                </Button>
                <Button variant="outline" className="w-full justify-start">
                  <Calendar className="h-4 w-4 mr-2" />
                  Schedule Session
                </Button>
              </>
            )}
            
            {isAdmin && (
              <>
                <Button variant="outline" className="w-full justify-start">
                  <BookOpen className="h-4 w-4 mr-2" />
                  Manage Courses
                </Button>
                <Button variant="outline" className="w-full justify-start">
                  <Users className="h-4 w-4 mr-2" />
                  Manage Users
                </Button>
                <Button variant="outline" className="w-full justify-start">
                  <TrendingUp className="h-4 w-4 mr-2" />
                  View Analytics
                </Button>
              </>
            )}
          </CardContent>
        </Card>

        <Card className="card-gradient">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <AlertCircle className="h-5 w-5 text-warning" />
              Recent Activity
            </CardTitle>
            <CardDescription>
              Latest updates and notifications
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <div className="flex items-center gap-3">
                <div className="w-2 h-2 bg-success rounded-full"></div>
                <div className="flex-1">
                  <p className="text-sm font-medium">System is online</p>
                  <p className="text-xs text-muted-foreground">All services running normally</p>
                </div>
                <Badge variant="outline" className="text-xs">
                  Now
                </Badge>
              </div>
              
              <div className="flex items-center gap-3">
                <div className="w-2 h-2 bg-primary rounded-full"></div>
                <div className="flex-1">
                  <p className="text-sm font-medium">Welcome to Smart Attendance</p>
                  <p className="text-xs text-muted-foreground">Get started with setting up your profile</p>
                </div>
                <Badge variant="outline" className="text-xs">
                  Today
                </Badge>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
