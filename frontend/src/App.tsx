import { Toaster } from "@/components/ui/toaster";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { ErrorBoundary } from "@/components/ErrorBoundary";
import ProtectedRoute from "./components/auth/ProtectedRoute";
import AuthCallback from "./components/auth/AuthCallback";
import AuthPage from "./components/auth/AuthPage";
import MainLayout from "./components/layout/MainLayout";
import Index from "./pages/Index";
import Dashboard from "./pages/Dashboard";
import Courses from "./pages/Courses";
import Attendance from "./pages/Attendance";
import FaceSetup from "./pages/FaceSetup";
import LiveSession from "./pages/LiveSession";
import ProfessorCourses from "./pages/ProfessorCourses";
import ProfessorSections from "./pages/ProfessorSections";
import Reports from "./pages/Reports";
import AdminCourses from "./pages/admin/AdminCourses";
import AdminSections from "./pages/admin/AdminSections";
import AdminUsers from "./pages/admin/AdminUsers";
import AdminReports from "./pages/admin/AdminReports";
import NotFound from "./pages/NotFound";
import ResetPasswordRequest from "./pages/ResetPasswordRequest";
import ResetPasswordConfirm from "./pages/ResetPasswordConfirm";

const queryClient = new QueryClient();

const App = () => (
  <ErrorBoundary>
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <Toaster />
        <Sonner />
        <BrowserRouter>
          <Routes>
            <Route path="/auth" element={<AuthPage />} />
            <Route path="/auth/reset" element={<ResetPasswordRequest />} />
            <Route path="/auth/reset/confirm" element={<ResetPasswordConfirm />} />
            <Route element={<AuthCallback />}>
              <Route path="/" element={<MainLayout />}>
                <Route index element={<ProtectedRoute><Index /></ProtectedRoute>} />
                <Route path="courses" element={<ProtectedRoute requiredRole="student"><Courses /></ProtectedRoute>} />
                <Route path="attendance" element={<ProtectedRoute requiredRole="student"><Attendance /></ProtectedRoute>} />
                <Route path="face-setup" element={<ProtectedRoute requiredRole="student"><FaceSetup /></ProtectedRoute>} />
                <Route path="professor-courses" element={<ProtectedRoute requiredRole="professor"><ProfessorCourses /></ProtectedRoute>} />
                <Route path="live-session/:sessionId" element={<ProtectedRoute requiredRole="professor"><LiveSession /></ProtectedRoute>} />
                <Route path="sections" element={<ProtectedRoute requiredRole="professor"><ProfessorSections /></ProtectedRoute>} />
                <Route path="live-session" element={<ProtectedRoute requiredRole="professor"><LiveSession /></ProtectedRoute>} />
                <Route path="reports" element={<ProtectedRoute requiredRole="professor"><Reports /></ProtectedRoute>} />
                <Route path="admin-courses" element={<ProtectedRoute requiredRole="admin"><AdminCourses /></ProtectedRoute>} />
                <Route path="admin-sections" element={<ProtectedRoute requiredRole="admin"><AdminSections /></ProtectedRoute>} />
                <Route path="admin-users" element={<ProtectedRoute requiredRole="admin"><AdminUsers /></ProtectedRoute>} />
                <Route path="admin-reports" element={<ProtectedRoute requiredRole="admin"><AdminReports /></ProtectedRoute>} />
              </Route>
              <Route path="*" element={<ProtectedRoute><NotFound /></ProtectedRoute>} />
            </Route>
          </Routes>
        </BrowserRouter>
      </TooltipProvider>
    </QueryClientProvider>
  </ErrorBoundary>
);

export default App;
