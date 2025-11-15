// Production configuration constants
export const APP_CONFIG = {
  name: 'Smart Attendance System',
  version: '1.0.0',
  university: 'Singapore Management University',
  supportEmail: 'support@smu.edu.sg',
  locale: 'en-SG',
  timeZone: 'Asia/Singapore',
  
  // Student validation
  allowedEmailDomains: ['.smu.edu.sg', '@smu.edu.sg'],
  minStudentIdLength: 8,
  maxStudentIdLength: 12,
  
  // Session configuration
  lateThresholdMinutes: 15,
  sessionCooldownSeconds: 30,
  
  // Face recognition
  recognitionThreshold: 0.7,
  minFaceImages: 5,
  maxFaceImages: 20,
  
  // UI Configuration
  itemsPerPage: 20,
  maxFileSize: 5 * 1024 * 1024, // 5MB
  supportedImageFormats: ['jpg', 'jpeg', 'png'],
} as const;

export const ROUTES = {
  // Public routes
  HOME: '/',
  AUTH: '/auth',
  
  // Student routes
  STUDENT_DASHBOARD: '/dashboard',
  STUDENT_COURSES: '/courses',
  STUDENT_ATTENDANCE: '/attendance',
  STUDENT_FACE_SETUP: '/face-setup',
  
  // Professor routes
  PROFESSOR_SECTIONS: '/sections',
  PROFESSOR_LIVE_SESSION: '/live-session',
  PROFESSOR_REPORTS: '/reports',
  // Backwards compatibility for older navigation helpers
  PROFESSOR_STUDENTS: '/reports',
  
  // Admin routes
  ADMIN_COURSES: '/admin-courses',
  ADMIN_SECTIONS: '/admin-sections',
  ADMIN_PROFESSORS: '/admin-professors',
  ADMIN_STUDENTS: '/admin-students',
  ADMIN_REPORTS: '/admin-reports',
} as const;

export const USER_ROLES = {
  STUDENT: 'student',
  PROFESSOR: 'professor',
  ADMIN: 'admin',
} as const;

export const ATTENDANCE_STATUS = {
  PENDING: 'pending',
  PRESENT: 'present',
  ABSENT: 'absent',
  LATE: 'late',
} as const;

export const SESSION_STATUS = {
  SCHEDULED: 'scheduled',
  ACTIVE: 'active',
  COMPLETED: 'completed',
} as const;

export const MARKING_METHOD = {
  AUTO: 'auto',
  MANUAL: 'manual',
} as const;
