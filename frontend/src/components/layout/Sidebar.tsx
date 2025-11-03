import { NavLink } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';
import {
  Users,
  BookOpen,
  Camera,
  BarChart3,
  LogOut,
  Menu,
  X,
  UserCheck,
  ClipboardList,
  School,
  FileText,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/hooks/useAuth';
import { useFaceDataCheck } from '@/hooks/useFaceDataCheck';
import { cn } from '@/lib/utils';

interface SidebarProps {
  menuOpen: boolean;
  onToggle: () => void;
}

interface MenuItem {
  title: string;
  url: string;
  icon: LucideIcon;
  highlightWhenMissingFaceData?: boolean;
}

export default function Sidebar({ menuOpen, onToggle }: SidebarProps) {
  const { profile, signOut, isStudent, isProfessor, isAdmin } = useAuth();
  const { hasFaceData, checkingFaceData } = useFaceDataCheck();

  const showFaceCaptureAlert = isStudent && !checkingFaceData && !hasFaceData;

  const studentItems: MenuItem[] = [
    { title: 'My Courses', url: '/courses', icon: BookOpen },
    { title: 'Attendance', url: '/attendance', icon: ClipboardList },
    { title: 'Face Setup', url: '/face-setup', icon: Camera, highlightWhenMissingFaceData: true },
  ];

  const professorItems: MenuItem[] = [
    { title: 'My Sections', url: '/sections', icon: School },
    { title: 'Live Session', url: '/live-session', icon: Camera },
    { title: 'Reports', url: '/reports', icon: FileText },
  ];

  const adminItems: MenuItem[] = [
    { title: 'Courses', url: '/admin-courses', icon: BookOpen },
    { title: 'Sections', url: '/admin-sections', icon: School },
    { title: 'Users', url: '/admin-users', icon: Users },
    { title: 'Reports', url: '/admin-reports', icon: BarChart3 },
  ];

  const getMenuItems = () => {
    if (isStudent) return studentItems;
    if (isProfessor) return professorItems;
    if (isAdmin) return adminItems;
    return [];
  };

  const menuItems = getMenuItems();

  const handleNavClick = () => {
    if (menuOpen) {
      onToggle();
    }
  };

  return (
    <header className="sticky top-0 z-40 w-full border-b border-border bg-card/95 backdrop-blur supports-[backdrop-filter]:bg-card/75">
      <div className="container mx-auto flex h-16 items-center justify-between gap-4 px-4">
        <div className="flex items-center gap-3">
          <div className="rounded-lg bg-primary/10 p-2">
            <UserCheck className="h-6 w-6 text-primary" />
          </div>
          <div className="flex flex-col">
            <span className="text-lg font-semibold leading-tight text-gradient">
              Smart Attendance
            </span>
            <span className="text-xs text-muted-foreground capitalize">
              {profile?.role || 'User'}
            </span>
          </div>
        </div>

        <nav className="hidden items-center gap-1 md:flex">
          {menuItems.map(item => {
            const shouldHighlight = showFaceCaptureAlert && item.highlightWhenMissingFaceData;

            return (
              <NavLink
                key={item.url}
                to={item.url}
                onClick={handleNavClick}
                className={({ isActive }) =>
                  cn(
                    'inline-flex items-center gap-2 rounded-full px-4 py-2 text-sm font-medium transition-colors',
                    isActive
                      ? 'bg-primary text-primary-foreground shadow-sm'
                      : 'text-muted-foreground hover:bg-accent/70 hover:text-accent-foreground',
                    shouldHighlight && !isActive && 'text-destructive'
                  )
                }
              >
                <item.icon className="h-4 w-4" />
                <span>{item.title}</span>
              </NavLink>
            );
          })}
        </nav>

        <div className="flex items-center gap-2">
          {profile && (
            <div className="hidden min-w-0 flex-col text-right md:flex">
              <span className="truncate text-sm font-medium text-foreground">{profile.fullName}</span>
              <span className="truncate text-xs text-muted-foreground">{profile.email}</span>
            </div>
          )}
          <Button
            variant="ghost"
            onClick={signOut}
            className="hidden items-center gap-2 text-destructive hover:bg-destructive/10 hover:text-destructive md:inline-flex"
          >
            <LogOut className="h-4 w-4" />
            Sign Out
          </Button>
          <Button
            variant="ghost"
            size="icon"
            onClick={onToggle}
            className="md:hidden"
            aria-label={menuOpen ? 'Close navigation menu' : 'Open navigation menu'}
          >
            {menuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </Button>
        </div>
      </div>

      <div className={cn('border-t border-border bg-card md:hidden', menuOpen ? 'block' : 'hidden')}>
        <nav className="flex flex-col gap-1 px-4 py-3">
          {menuItems.map(item => {
            const shouldHighlight = showFaceCaptureAlert && item.highlightWhenMissingFaceData;

            return (
              <NavLink
                key={item.url}
                to={item.url}
                onClick={handleNavClick}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                    isActive
                      ? 'bg-primary text-primary-foreground shadow-sm'
                      : 'text-muted-foreground hover:bg-accent/70 hover:text-accent-foreground',
                    shouldHighlight && !isActive && 'text-destructive'
                  )
                }
              >
                <item.icon className="h-4 w-4" />
                <span>{item.title}</span>
              </NavLink>
            );
          })}
        </nav>

        <div className="border-t border-border px-4 py-3">
          {profile && (
            <div className="mb-3 rounded-lg bg-muted/60 px-3 py-2 text-sm">
              <p className="truncate font-medium">{profile.fullName}</p>
              <p className="truncate text-xs text-muted-foreground">{profile.email}</p>
            </div>
          )}
          <Button
            variant="ghost"
            onClick={() => {
              if (menuOpen) {
                onToggle();
              }
              signOut();
            }}
            className="w-full justify-start gap-2 text-destructive hover:bg-destructive/10 hover:text-destructive"
          >
            <LogOut className="h-4 w-4" />
            Sign Out
          </Button>
        </div>
      </div>
    </header>
  );
}
