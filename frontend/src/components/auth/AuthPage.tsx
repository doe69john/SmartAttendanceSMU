import { useEffect, useState } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useAuth } from '@/hooks/useAuth';
import { useToast } from '@/hooks/use-toast';
import { GraduationCap, Users, Shield, Eye, EyeOff, UserCheck } from 'lucide-react';
import { Link, Navigate } from 'react-router-dom';
import { ApiError, fetchApplicationSettings, validateAdminPasscode, validateStaffPasscode } from '@/lib/api';

export default function AuthPage() {
  const { signIn, signUp, session, loading } = useAuth();
  const { toast } = useToast();
  const [isSignUp, setIsSignUp] = useState(false);
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    confirmPassword: '',
    fullName: '',
    role: '',
    staffId: '',
    studentId: '',
    phone: '',
    professorPasscode: '',
    adminPasscode: '',
  });
  const [showPassword, setShowPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [requiresProfessorPasscode, setRequiresProfessorPasscode] = useState(false);
  const [requiresAdminPasscode, setRequiresAdminPasscode] = useState(false);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const settings = await fetchApplicationSettings();
        if (!active) {
          return;
        }
        setRequiresProfessorPasscode(Boolean(settings?.requiresStaffPasscode));
        setRequiresAdminPasscode(Boolean(settings?.requiresAdminPasscode));
      } catch (error) {
        console.error('Failed to load application settings', error);
        if (active) {
          setRequiresProfessorPasscode(false);
          setRequiresAdminPasscode(false);
        }
      }
    })();

    return () => {
      active = false;
    };
  }, []);

  // Redirect if already authenticated
  if (session && !loading) {
    return <Navigate to="/" replace />;
  }

  const handleInputChange = (field: string, value: string) => {
    setFormData(prev => ({ ...prev, [field]: value }));
  };

  const validateForm = () => {
    if (!formData.email || !formData.password) {
      return 'Email and password are required';
    }
    
    if (isSignUp) {
      if (!formData.fullName || !formData.role) {
        return 'Full name and role are required';
      }
      
      if (formData.password !== formData.confirmPassword) {
        return 'Passwords do not match';
      }
      
      if (formData.password.length < 6) {
        return 'Password must be at least 6 characters';
      }
      
      if (formData.role === 'student') {
        if (!formData.email.includes('.smu.edu.sg')) {
          return 'Student email must be from SMU domain (*.smu.edu.sg)';
        }
        
        if (!formData.studentId || formData.studentId.length < 8) {
          return 'Student ID must be at least 8 digits';
        }
        
        if (!/^\d+$/.test(formData.studentId)) {
          return 'Student ID must contain only numbers';
        }
      }
      
      if ((formData.role === 'professor' || formData.role === 'admin') && !formData.staffId) {
        return 'Staff ID is required for professors and admins';
      }

      if (requiresProfessorPasscode && formData.role === 'professor' && !formData.professorPasscode) {
        return 'Professor passcode is required for professors.';
      }

      if (requiresAdminPasscode && formData.role === 'admin' && !formData.adminPasscode) {
        return 'Admin passcode is required for admins.';
      }
    }

    return null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    const error = validateForm();
    if (error) {
      toast({
        title: "Validation Error",
        description: error,
        variant: "destructive",
      });
      return;
    }
    
    try {
      setIsSubmitting(true);

      if (isSignUp && requiresProfessorPasscode && formData.role === 'professor') {
        try {
          const result = await validateStaffPasscode(formData.professorPasscode);
          if (!result.valid) {
            toast({
              title: 'Professor Passcode Required',
              description: 'The provided professor passcode is invalid. Please contact your administrator.',
              variant: 'destructive',
            });
            return;
          }
        } catch (error) {
          const message =
            error instanceof ApiError
              ? error.message
              : error instanceof Error
                ? error.message
                : 'Unable to validate professor passcode';
          toast({
            title: 'Validation Error',
            description: message,
            variant: 'destructive',
          });
          return;
        }
      }

      if (isSignUp && requiresAdminPasscode && formData.role === 'admin') {
        try {
          const result = await validateAdminPasscode(formData.adminPasscode);
          if (!result.valid) {
            toast({
              title: 'Admin Passcode Required',
              description: 'The provided admin passcode is invalid. Please contact your system administrator.',
              variant: 'destructive',
            });
            return;
          }
        } catch (error) {
          const message =
            error instanceof ApiError
              ? error.message
              : error instanceof Error
                ? error.message
                : 'Unable to validate admin passcode';
          toast({
            title: 'Validation Error',
            description: message,
            variant: 'destructive',
          });
          return;
        }
      }

      if (isSignUp) {
        const userData = {
          full_name: formData.fullName,
          role: formData.role,
          staff_id: formData.staffId || null,
          student_id: formData.studentId || null,
          phone: formData.phone || null,
        };
        
        await signUp(formData.email, formData.password, userData);
      } else {
        await signIn(formData.email, formData.password);
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary/10 via-background to-secondary/10">
        <div className="animate-glow">
          <GraduationCap className="h-12 w-12 text-primary animate-float" />
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary/10 via-background to-secondary/10 p-4">
      <Card className="w-full max-w-md card-gradient shadow-elegant">
        <CardHeader className="text-center">
          <div className="flex justify-center mb-4">
            <div className="p-3 rounded-full bg-primary/10">
              <UserCheck className="h-8 w-8 text-primary" />
            </div>
          </div>
          <CardTitle className="text-2xl font-bold text-gradient">
            Smart Attendance System
          </CardTitle>
          <CardDescription>
            {isSignUp ? 'Create your account' : 'Sign in to your account'}
          </CardDescription>
        </CardHeader>
        
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4" autoComplete="off">
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email" autoComplete="off"
                placeholder="Enter your email"
                value={formData.email}
                onChange={(e) => handleInputChange('email', e.target.value)}
                required
              />
            </div>
            
            <div className="space-y-2">
              <Label htmlFor="password">Password</Label>
              <div className="relative">
                <Input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="off"
                  placeholder="Enter your password"
                  value={formData.password}
                  onChange={(e) => handleInputChange('password', e.target.value)}
                  required
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="absolute right-2 top-1/2 -translate-y-1/2 h-7 w-7 p-0"
                  onClick={() => setShowPassword(!showPassword)}
                >
                  {showPassword ? (
                    <EyeOff className="h-4 w-4" />
                  ) : (
                    <Eye className="h-4 w-4" />
                  )}
                </Button>
              </div>
            </div>

            {!isSignUp && (
              <div className="flex justify-end -mt-2">
                <Button variant="link" size="sm" asChild className="px-0 h-auto">
                  <Link to="/auth/reset">Forgot password?</Link>
                </Button>
              </div>
            )}

            {isSignUp && (
              <>
                <div className="space-y-2">
                  <Label htmlFor="confirmPassword">Confirm Password</Label>
                  <Input
                    id="confirmPassword"
                    type="password" autoComplete="off"
                    placeholder="Confirm your password"
                    value={formData.confirmPassword}
                    onChange={(e) => handleInputChange('confirmPassword', e.target.value)}
                    required
                  />
                </div>
                
                <div className="space-y-2">
                  <Label htmlFor="fullName">Full Name</Label>
                  <Input
                    id="fullName"
                    type="text" autoComplete="off"
                    placeholder="Enter your full name"
                    value={formData.fullName}
                    onChange={(e) => handleInputChange('fullName', e.target.value)}
                    required
                  />
                </div>
                
                <div className="space-y-2">
                  <Label htmlFor="role">Role</Label>
                  <Select value={formData.role || undefined} onValueChange={(value) => handleInputChange('role', value)}>
                    <SelectTrigger>
                      <SelectValue placeholder="Select your role" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="student">
                        <div className="flex items-center gap-2">
                          <GraduationCap className="h-4 w-4" />
                          Student
                        </div>
                      </SelectItem>
                      <SelectItem value="professor">
                        <div className="flex items-center gap-2">
                          <Users className="h-4 w-4" />
                          Professor
                        </div>
                      </SelectItem>
                      <SelectItem value="admin">
                        <div className="flex items-center gap-2">
                          <Shield className="h-4 w-4" />
                          Admin
                        </div>
                      </SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                
                {formData.role === 'student' && (
                  <div className="space-y-2">
                    <Label htmlFor="studentId">Student ID (at least 8 digits)</Label>
                    <Input
                      id="studentId"
                      type="text" autoComplete="off"
                      placeholder="Enter your student ID (numbers only)"
                      value={formData.studentId}
                      onChange={(e) => {
                        // Only allow numbers
                        const value = e.target.value.replace(/\D/g, '');
                        handleInputChange('studentId', value);
                      }}
                      maxLength={12}
                      required
                    />
                  </div>
                )}
                
                {(formData.role === 'professor' || formData.role === 'admin') && (
                  <div className="space-y-2">
                    <Label htmlFor="staffId">Staff ID</Label>
                    <Input
                      id="staffId"
                      type="text" autoComplete="off"
                      placeholder="Enter your staff ID"
                      value={formData.staffId}
                      onChange={(e) => handleInputChange('staffId', e.target.value)}
                      required
                    />
                  </div>
                )}

                {requiresProfessorPasscode && formData.role === 'professor' && (
                  <div className="space-y-2">
                    <Label htmlFor="professorPasscode">Professor Passcode</Label>
                    <Input
                      id="professorPasscode"
                      type="password"
                      autoComplete="off"
                      placeholder="Enter the professor passcode"
                      value={formData.professorPasscode}
                      onChange={(e) => handleInputChange('professorPasscode', e.target.value)}
                      required
                    />
                  </div>
                )}

                {requiresAdminPasscode && formData.role === 'admin' && (
                  <div className="space-y-2">
                    <Label htmlFor="adminPasscode">Admin Passcode</Label>
                    <Input
                      id="adminPasscode"
                      type="password"
                      autoComplete="off"
                      placeholder="Enter the admin passcode"
                      value={formData.adminPasscode}
                      onChange={(e) => handleInputChange('adminPasscode', e.target.value)}
                      required
                    />
                  </div>
                )}
                
                <div className="space-y-2">
                  <Label htmlFor="phone">Phone (optional)</Label>
                  <Input
                    id="phone"
                    type="tel"
                    autoComplete="off"
                    placeholder="Enter your phone number"
                    value={formData.phone}
                    onChange={(e) => handleInputChange('phone', e.target.value)}
                  />
                </div>
              </>
            )}
            
            <Button 
              type="submit" 
              className="w-full btn-gradient"
              disabled={isSubmitting}
            >
              {isSubmitting 
                ? (isSignUp ? 'Creating Account...' : 'Signing In...') 
                : (isSignUp ? 'Create Account' : 'Sign In')
              }
            </Button>
            
            <div className="text-center">
              <Button
                type="button"
                variant="link"
                onClick={() => setIsSignUp(!isSignUp)}
                className="text-sm"
              >
                {isSignUp 
                  ? 'Already have an account? Sign in' 
                  : "Don't have an account? Sign up"
                }
              </Button>
            </div>
            
            {formData.role === 'professor' && isSignUp && requiresProfessorPasscode && (
              <div className="text-xs text-muted-foreground text-center bg-primary/10 p-3 rounded-lg border border-primary/20">
                <div className="font-medium text-primary mb-1">Professor Account Notice</div>
                Use the professor passcode provided by your institution administrator.
              </div>
            )}

            {formData.role === 'admin' && isSignUp && requiresAdminPasscode && (
              <div className="text-xs text-muted-foreground text-center bg-primary/10 p-3 rounded-lg border border-primary/20">
                <div className="font-medium text-primary mb-1">Admin Account Notice</div>
                Use the admin passcode provided by your system administrator.
              </div>
            )}
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
