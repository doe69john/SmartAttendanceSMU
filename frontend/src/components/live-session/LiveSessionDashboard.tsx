import { useState, useEffect, useRef, useCallback } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { 
  Play, 
  Pause, 
  Square, 
  Users, 
  Camera, 
  CheckCircle, 
  AlertCircle, 
  Clock,
  Search,
  User,
  Download
} from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { useToast } from '@/hooks/use-toast';
import {
  ApiError,
  fetchSession,
  fetchSessionStudents,
  fetchSessionAttendance,
  subscribeToSessionEvents,
  upsertAttendanceRecord,
  manageSession,
  type AttendanceEvent,
  type RecognitionEvent,
  type SessionActionEvent,
  type SessionAttendanceRecord,
  type Student as StudentDto,
} from '@/lib/api';

type AttendanceStatus = 'present' | 'absent' | 'late' | 'pending';
type SessionLifecycleStatus = 'scheduled' | 'active' | 'completed' | 'cancelled';

interface StudentViewModel {
  id: string;
  studentNumber?: string | null;
  fullName: string;
  avatarUrl?: string | null;
}

interface AttendanceRecordViewModel {
  id?: string;
  studentId: string;
  student: StudentViewModel;
  status: AttendanceStatus;
  confidenceScore?: number | null;
  markedAt?: string | null;
  lastSeen?: string | null;
  notes?: string | null;
}

interface SessionViewModel {
  id: string;
  sectionId: string;
  sessionDate: string;
  startTime?: string | null;
  endTime?: string | null;
  status: SessionLifecycleStatus;
  location?: string | null;
  notes?: string | null;
  lateThresholdMinutes: number;
}

type RecognitionLogAction = 'auto_marked' | 'manual_confirm' | 'ignored';

interface RecognitionLogEntry {
  id: number;
  timestamp: string;
  studentName: string;
  confidence: number;
  action: RecognitionLogAction;
}

interface RecognitionResultPayload {
  studentId: string;
  student: StudentViewModel;
  confidence: number;
  timestamp: string;
  capturedFace: string;
}

interface ConfirmationDialog {
  open: boolean;
  capturedFace?: string;
  confidence: number;
  suggestedStudents: StudentViewModel[];
  onConfirm: (studentId: string, notes?: string) => void;
  onDeny: () => void;
}

const normalizeSessionStatus = (status?: string | null): SessionLifecycleStatus => {
  switch ((status ?? '').toLowerCase()) {
    case 'active':
      return 'active';
    case 'completed':
      return 'completed';
    case 'cancelled':
      return 'cancelled';
    default:
      return 'scheduled';
  }
};

const normalizeAttendanceStatus = (status?: string | null): AttendanceStatus => {
  switch ((status ?? '').toLowerCase()) {
    case 'present':
      return 'present';
    case 'absent':
      return 'absent';
    case 'late':
      return 'late';
    default:
      return 'pending';
  }
};

export const LiveSessionDashboard = ({ sessionId }: { sessionId: string }) => {
  const { profile } = useAuth();
  const { toast } = useToast();

  const [session, setSession] = useState<SessionViewModel | null>(null);
  const [students, setStudents] = useState<StudentViewModel[]>([]);
  const [attendance, setAttendance] = useState<AttendanceRecordViewModel[]>([]);
  const [sessionStatus, setSessionStatus] = useState<SessionLifecycleStatus>('scheduled');
  const [recognitionLogs, setRecognitionLogs] = useState<RecognitionLogEntry[]>([]);
  const [confirmationDialog, setConfirmationDialog] = useState<ConfirmationDialog>({
    open: false,
    confidence: 0,
    suggestedStudents: [],
    onConfirm: () => {},
    onDeny: () => {}
  });
  
  const [searchTerm, setSearchTerm] = useState('');
  const [filterStatus, setFilterStatus] = useState<string>('all');
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const recognitionWorkerRef = useRef<Worker | null>(null);

  const HIGH_CONFIDENCE_THRESHOLD = 0.85;
  const LOW_CONFIDENCE_THRESHOLD = 0.60;

  const studentsRef = useRef<StudentViewModel[]>([]);

  const showApiError = useCallback((error: unknown, fallback: string) => {
    const message = error instanceof ApiError ? error.message : fallback;
    toast({
      title: 'Error',
      description: message,
      variant: 'destructive'
    });
  }, [toast]);

  const mapStudentDto = useCallback((dto: StudentDto): StudentViewModel => ({
    id: dto.id,
    studentNumber: dto.studentNumber ?? null,
    fullName: dto.fullName ?? 'Unknown Student',
    avatarUrl: dto.avatarUrl ?? null,
  }), []);

  const loadSessionData = useCallback(async () => {
    try {
      const data = await fetchSession(sessionId);
      const mapped: SessionViewModel = {
        id: data.id,
        sectionId: data.sectionId,
        sessionDate: data.sessionDate,
        startTime: data.startTime ?? null,
        endTime: data.endTime ?? null,
        status: normalizeSessionStatus(data.status),
        location: data.location ?? null,
        notes: data.notes ?? null,
        lateThresholdMinutes: data.lateThresholdMinutes ?? 15,
      };
      setSession(mapped);
      const normalizedStatus = mapped.status === 'active' ? 'active' : mapped.status === 'completed' ? 'completed' : 'scheduled';
      setSessionStatus(normalizedStatus);
    } catch (error) {
      console.error('Failed to load session:', error);
      showApiError(error, 'Failed to load session data');
    }
  }, [sessionId, showApiError]);

  const loadStudents = useCallback(async () => {
    try {
      const data = await fetchSessionStudents(sessionId);
      const mapped = data.map(mapStudentDto);
      studentsRef.current = mapped;
      setStudents(mapped);
      return mapped;
    } catch (error) {
      console.error('Failed to load students:', error);
      showApiError(error, 'Failed to load session roster');
      return [];
    }
  }, [sessionId, mapStudentDto, showApiError]);

  const loadAttendance = useCallback(async (studentList?: StudentViewModel[]) => {
    try {
      const data = await fetchSessionAttendance(sessionId);
      const baseStudents = studentList ?? studentsRef.current;
      const studentMap = new Map(baseStudents.map(student => [student.id, student]));
      const records: AttendanceRecordViewModel[] = data.map(record => {
        const student = record.student ? mapStudentDto(record.student) : studentMap.get(record.studentId) ?? {
          id: record.studentId,
          fullName: 'Unknown Student',
        };
        studentMap.set(student.id, student);
        return {
          id: record.id,
          studentId: record.studentId,
          student,
          status: normalizeAttendanceStatus(record.status),
          confidenceScore: record.confidenceScore ?? null,
          markedAt: record.markedAt ?? null,
          lastSeen: record.lastSeen ?? null,
          notes: record.notes ?? null,
        };
      });
      baseStudents.forEach(student => {
        if (!records.some(record => record.studentId === student.id)) {
          records.push({
            studentId: student.id,
            student,
            status: 'pending',
          });
        }
      });
      const updatedStudents = Array.from(studentMap.values());
      studentsRef.current = updatedStudents;
      setStudents(updatedStudents);
      setAttendance(records);
    } catch (error) {
      console.error('Failed to load attendance:', error);
      showApiError(error, 'Failed to load attendance records');
    }
  }, [sessionId, mapStudentDto, showApiError]);

  const handleAttendanceEvent = useCallback((payload: AttendanceEvent) => {
    const studentId = payload.student_id;
    if (!studentId) {
      return;
    }
    const status = normalizeAttendanceStatus(payload.status);
    setAttendance(prev => {
      let updated = false;
      const next = prev.map(record => {
        if (record.studentId === studentId) {
          updated = true;
          return {
            ...record,
            status,
            confidenceScore: payload.confidence ?? record.confidenceScore ?? null,
            markedAt: payload.timestamp ?? record.markedAt ?? null,
          };
        }
        return record;
      });
      if (!updated) {
        let student = studentsRef.current.find(s => s.id === studentId);
        if (!student) {
          student = { id: studentId, fullName: 'Unknown Student' };
          studentsRef.current = [...studentsRef.current, student];
          setStudents(studentsRef.current);
        }
        next.push({
          studentId,
          student,
          status,
          confidenceScore: payload.confidence ?? null,
          markedAt: payload.timestamp ?? null,
        });
      }
      return next;
    });
  }, []);

  const handleSessionAction = useCallback((payload: SessionActionEvent) => {
    setSessionStatus(normalizeSessionStatus(payload.status));
    loadSessionData();
    loadAttendance();
  }, [loadAttendance, loadSessionData]);

  const handleRecognitionEvent = useCallback((payload: RecognitionEvent) => {
    const studentId = payload.student_id;
    const student = studentId ? studentsRef.current.find(s => s.id === studentId) : undefined;
    const action: RecognitionLogAction = payload.success
      ? 'auto_marked'
      : payload.requires_manual_confirmation
        ? 'manual_confirm'
        : 'ignored';
    setRecognitionLogs(prev => [
      {
        id: Date.now(),
        timestamp: payload.timestamp ?? new Date().toISOString(),
        studentName: student?.fullName ?? 'Unknown Student',
        confidence: payload.confidence ?? 0,
        action,
      },
      ...prev.slice(0, 49)
    ]);
  }, []);

  const setupRealtimeUpdates = useCallback(() => {
    return subscribeToSessionEvents(sessionId, {
      onAttendance: handleAttendanceEvent,
      onSessionAction: handleSessionAction,
      onRecognition: handleRecognitionEvent,
      onError: () => console.warn('Session events stream disconnected'),
    });
  }, [sessionId, handleAttendanceEvent, handleRecognitionEvent, handleSessionAction]);

  useEffect(() => {
    let unsubscribe: (() => void) | undefined;
    const initialize = async () => {
      await loadSessionData();
      const roster = await loadStudents();
      await loadAttendance(roster);
      unsubscribe = setupRealtimeUpdates();
    };
    initialize();
    return () => {
      unsubscribe?.();
      stopFaceRecognition();
    };
  }, [sessionId, loadSessionData, loadStudents, loadAttendance, setupRealtimeUpdates]);

  const startSession = async () => {
    try {
      await manageSession(sessionId, 'start', profile?.id);
      setSessionStatus('active');
      await loadAttendance();
      await startFaceRecognition();
      toast({
        title: 'Session Started',
        description: 'Live attendance tracking is now active'
      });
    } catch (error) {
      console.error('Failed to start session:', error);
      showApiError(error, 'Failed to start session');
    }
  };

  const pauseSession = async () => {
    try {
      await manageSession(sessionId, 'pause', profile?.id);
      setSessionStatus('scheduled');
      stopFaceRecognition();
      toast({
        title: 'Session Paused',
        description: 'Attendance tracking paused'
      });
    } catch (error) {
      console.error('Failed to pause session:', error);
      showApiError(error, 'Failed to pause session');
    }
  };

  const endSession = async () => {
    try {
      await manageSession(sessionId, 'stop', profile?.id);
      setSessionStatus('completed');
      stopFaceRecognition();
      await loadAttendance();
      toast({
        title: 'Session Ended',
        description: 'Attendance has been finalized'
      });
    } catch (error) {
      console.error('Failed to end session:', error);
      showApiError(error, 'Failed to end session');
    }
  };

  const startFaceRecognition = async () => {
    try {
      // Start camera
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { width: 640, height: 480 }
      });
      
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
      }
      
      // Start recognition loop
      startRecognitionLoop();
      
    } catch (error) {
      console.error('Failed to start camera:', error);
      toast({
        title: "Camera Error",
        description: "Failed to access camera",
        variant: "destructive"
      });
    }
  };

  const stopFaceRecognition = () => {
    if (videoRef.current?.srcObject) {
      const stream = videoRef.current.srcObject as MediaStream;
      stream.getTracks().forEach(track => track.stop());
      videoRef.current.srcObject = null;
    }
    
    if (recognitionWorkerRef.current) {
      recognitionWorkerRef.current.terminate();
      recognitionWorkerRef.current = null;
    }
  };

  const startRecognitionLoop = () => {
    const recognizeLoop = () => {
      if (sessionStatus !== 'active') return;
      
      processFrameForRecognition();
      setTimeout(recognizeLoop, 2000); // Process every 2 seconds
    };
    
    recognizeLoop();
  };

  const processFrameForRecognition = async () => {
    if (!videoRef.current || !canvasRef.current) return;
    
    const video = videoRef.current;
    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    
    if (!ctx) return;
    
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    ctx.drawImage(video, 0, 0);
    
    // Simulate face recognition (replace with actual implementation)
    const recognitionResult = await simulateRecognition(canvas);

    if (recognitionResult) {
      handleRecognitionResult(recognitionResult);
    }
  };

  const simulateRecognition = async (canvas: HTMLCanvasElement): Promise<RecognitionResultPayload | null> => {
    // Simulate recognition processing
    await new Promise(resolve => setTimeout(resolve, 100));

    // Simulate random recognition result for demo
    if (Math.random() > 0.7 && students.length > 0) {
      const randomStudent = students[Math.floor(Math.random() * students.length)];
      const confidence = 0.3 + Math.random() * 0.7; // 0.3 to 1.0
      
        return {
          studentId: randomStudent.id,
          student: randomStudent,
          confidence,
          timestamp: new Date().toISOString(),
          capturedFace: canvas.toDataURL('image/jpeg', 0.7)
        };
    }

    return null;
  };

  const handleRecognitionResult = async (result: RecognitionResultPayload) => {
    const { studentId, student, confidence, capturedFace, timestamp } = result;

    // Add to recognition logs
    const logEntry: RecognitionLogEntry = {
      id: Date.now(),
      timestamp,
      studentName: student.fullName,
      confidence,
      action: (confidence >= HIGH_CONFIDENCE_THRESHOLD
        ? 'auto_marked'
        : confidence >= LOW_CONFIDENCE_THRESHOLD
          ? 'manual_confirm'
          : 'ignored') as RecognitionLogAction
    };
    
    setRecognitionLogs(prev => [logEntry, ...prev.slice(0, 49)]); // Keep last 50 logs
    
    if (confidence >= HIGH_CONFIDENCE_THRESHOLD) {
      // High confidence - automatic marking
      await markAttendance(studentId, 'present', confidence, 'Automatic recognition');
    } else if (confidence >= LOW_CONFIDENCE_THRESHOLD) {
      // Low confidence - manual confirmation required
      showConfirmationDialog(capturedFace, confidence, [student]);
    }
    // Below low threshold - ignored
  };

  const showConfirmationDialog = (capturedFace: string, confidence: number, suggestedStudents: StudentViewModel[]) => {
    setConfirmationDialog({
      open: true,
      capturedFace,
      confidence,
      suggestedStudents,
      onConfirm: async (studentId: string, notes?: string) => {
        await markAttendance(studentId, 'present', confidence, notes || 'Manual confirmation');
        setConfirmationDialog(prev => ({ ...prev, open: false }));
      },
      onDeny: () => {
        setConfirmationDialog(prev => ({ ...prev, open: false }));
      }
    });
  };

  const markAttendance = async (studentId: string, status: AttendanceStatus, confidence?: number, notes?: string) => {
    try {
      await upsertAttendanceRecord({
        sessionId,
        studentId,
        status,
        confidenceScore: confidence,
        markingMethod: confidence ? 'auto' : 'manual',
        notes,
      });

      setAttendance(prev => prev.map(att =>
        att.studentId === studentId
          ? {
              ...att,
              status,
              confidenceScore: confidence ?? att.confidenceScore,
              markedAt: new Date().toISOString(),
              notes,
            }
          : att
      ));
    } catch (error) {
      console.error('Failed to mark attendance:', error);
      showApiError(error, 'Failed to mark attendance');
    }
  };

  const exportAttendance = async () => {
    try {
      const csvContent = generateCSV();
      const blob = new Blob([csvContent], { type: 'text/csv' });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `attendance_${sessionId}_${new Date().toISOString().split('T')[0]}.csv`;
      a.click();
      window.URL.revokeObjectURL(url);
      
      toast({
        title: "Export Complete",
        description: "Attendance data exported successfully"
      });
    } catch (error) {
      console.error('Export failed:', error);
    }
  };

  const generateCSV = () => {
    const headers = ['Student ID', 'Name', 'Status', 'Confidence', 'Marked At', 'Method', 'Notes'];
    const rows = attendance.map(att => [
      att.student.studentNumber || '',
      att.student.fullName,
      att.status || 'pending',
      typeof att.confidenceScore === 'number' ? `${Math.round(att.confidenceScore * 100)}%` : '',
      att.markedAt ? new Date(att.markedAt).toLocaleString() : '',
      typeof att.confidenceScore === 'number' ? 'Face Recognition' : 'Manual',
      att.notes || ''
    ]);
    
    return [headers, ...rows].map(row => 
      row.map(cell => `"${cell}"`).join(',')
    ).join('\n');
  };

  const filteredAttendance = attendance.filter(att => {
    const matchesSearch = att.student.fullName.toLowerCase().includes(searchTerm.toLowerCase()) ||
                         (att.student.studentNumber && att.student.studentNumber.toLowerCase().includes(searchTerm.toLowerCase()));
    const matchesFilter = filterStatus === 'all' || att.status === filterStatus;
    return matchesSearch && matchesFilter;
  });

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'present': return <CheckCircle className="w-4 h-4 text-green-600" />;
      case 'late': return <Clock className="w-4 h-4 text-yellow-600" />;
      case 'absent': return <AlertCircle className="w-4 h-4 text-red-600" />;
      default: return <User className="w-4 h-4 text-gray-400" />;
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'present': return 'default';
      case 'late': return 'secondary';
      case 'absent': return 'destructive';
      default: return 'outline';
    }
  };

  const attendanceStats = {
    present: attendance.filter(att => att.status === 'present').length,
    late: attendance.filter(att => att.status === 'late').length,
    absent: attendance.filter(att => att.status === 'absent').length,
    pending: attendance.filter(att => att.status === 'pending').length
  };

  return (
    <div className="p-6 space-y-6">
      {/* Session Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Live Session</h1>
          <p className="text-muted-foreground">
            {session?.sessionDate} - {session?.location}
          </p>
        </div>
        <div className="flex gap-2">
          {sessionStatus === 'scheduled' && (
            <Button onClick={startSession} className="bg-green-600 hover:bg-green-700">
              <Play className="w-4 h-4 mr-2" />
              Start Session
            </Button>
          )}
          {sessionStatus === 'active' && (
            <>
              <Button onClick={pauseSession} variant="outline">
                <Pause className="w-4 h-4 mr-2" />
                Pause
              </Button>
              <Button onClick={endSession} variant="destructive">
                <Square className="w-4 h-4 mr-2" />
                End Session
              </Button>
            </>
          )}
          <Button onClick={exportAttendance} variant="outline">
            <Download className="w-4 h-4 mr-2" />
            Export
          </Button>
        </div>
      </div>

      {/* Session Status & Stats */}
      <div className="grid gap-4 md:grid-cols-4">
        <Card>
          <CardContent className="p-4">
            <div className="flex items-center gap-2">
              <div className={`w-3 h-3 rounded-full ${
                sessionStatus === 'active' ? 'bg-green-500 animate-pulse' : 'bg-gray-400'
              }`} />
              <span className="font-semibold">
                {sessionStatus === 'active' ? 'Live' : sessionStatus === 'completed' ? 'Completed' : 'Scheduled'}
              </span>
            </div>
          </CardContent>
        </Card>
        
        <Card>
          <CardContent className="p-4">
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted-foreground">Present</span>
              <Badge variant="default">{attendanceStats.present}</Badge>
            </div>
          </CardContent>
        </Card>
        
        <Card>
          <CardContent className="p-4">
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted-foreground">Late</span>
              <Badge variant="secondary">{attendanceStats.late}</Badge>
            </div>
          </CardContent>
        </Card>
        
        <Card>
          <CardContent className="p-4">
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted-foreground">Absent</span>
              <Badge variant="destructive">{attendanceStats.absent}</Badge>
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Camera Feed & Recognition */}
        <div className="lg:col-span-2 space-y-6">
          {sessionStatus === 'active' && (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Camera className="w-5 h-5" />
                  Live Recognition Feed
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="relative aspect-video rounded-lg overflow-hidden bg-muted">
                  <video
                    ref={videoRef}
                    autoPlay
                    muted
                    className="w-full h-full object-cover"
                  />
                  <canvas
                    ref={canvasRef}
                    className="absolute inset-0 w-full h-full"
                    style={{ display: 'none' }}
                  />
                </div>
              </CardContent>
            </Card>
          )}

          {/* Attendance List */}
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Users className="w-5 h-5" />
                Attendance ({filteredAttendance.length} students)
              </CardTitle>
              <div className="flex gap-2">
                <Input
                  placeholder="Search students..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="max-w-sm"
                />
                <Select value={filterStatus} onValueChange={setFilterStatus}>
                  <SelectTrigger className="w-32">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All</SelectItem>
                    <SelectItem value="present">Present</SelectItem>
                    <SelectItem value="late">Late</SelectItem>
                    <SelectItem value="absent">Absent</SelectItem>
                    <SelectItem value="pending">Pending</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </CardHeader>
            <CardContent>
              <div className="space-y-2 max-h-96 overflow-y-auto">
                {filteredAttendance.map((att) => (
                  <div
                    key={att.studentId}
                    className="flex items-center justify-between p-3 rounded-lg border"
                  >
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-full bg-muted flex items-center justify-center">
                        {att.student.avatarUrl ? (
                          <img
                            src={att.student.avatarUrl}
                            alt={att.student.fullName}
                            className="w-full h-full rounded-full object-cover"
                          />
                        ) : (
                          <User className="w-4 h-4" />
                        )}
                      </div>
                      <div>
                        <p className="font-medium">{att.student.fullName}</p>
                        <p className="text-xs text-muted-foreground">
                          {att.student.studentNumber ?? 'N/A'}
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-2">
                      {typeof att.confidenceScore === 'number' && (
                        <span className="text-xs text-muted-foreground">
                          {Math.round(att.confidenceScore * 100)}%
                        </span>
                      )}
                      <Badge variant={getStatusColor(att.status || 'pending')}>
                        <span className="flex items-center gap-1">
                          {getStatusIcon(att.status || 'pending')}
                          {att.status || 'pending'}
                        </span>
                      </Badge>
                      {sessionStatus === 'active' && (
                        <Select
                          value={att.status || 'pending'}
                          onValueChange={(value) => markAttendance(att.studentId, value as AttendanceStatus)}
                        >
                          <SelectTrigger className="w-24 h-8">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="present">Present</SelectItem>
                            <SelectItem value="late">Late</SelectItem>
                            <SelectItem value="absent">Absent</SelectItem>
                          </SelectContent>
                        </Select>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Recognition Logs */}
        <div>
          <Card>
            <CardHeader>
              <CardTitle className="text-lg">Recognition Log</CardTitle>
              <CardDescription>Real-time recognition events</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="space-y-2 max-h-96 overflow-y-auto">
                {recognitionLogs.map((log) => (
                  <div
                    key={log.id}
                    className="p-2 rounded-lg border text-sm"
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-medium">{log.studentName}</span>
                      <Badge
                        variant={
                          log.action === 'auto_marked' ? 'default' :
                          log.action === 'manual_confirm' ? 'secondary' : 'outline'
                        }
                      >
                        {Math.round(log.confidence * 100)}%
                      </Badge>
                    </div>
                    <div className="flex items-center justify-between text-xs text-muted-foreground">
                      <span>{new Date(log.timestamp).toLocaleTimeString()}</span>
                      <span>{log.action.replace('_', ' ')}</span>
                    </div>
                  </div>
                ))}
                {recognitionLogs.length === 0 && (
                  <p className="text-sm text-muted-foreground text-center py-4">
                    No recognition events yet
                  </p>
                )}
              </div>
            </CardContent>
          </Card>
        </div>
      </div>

      {/* Manual Confirmation Dialog */}
      <Dialog open={confirmationDialog.open} onOpenChange={(open) => 
        setConfirmationDialog(prev => ({ ...prev, open }))
      }>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Confirm Student Identity</DialogTitle>
            <DialogDescription>
              Low confidence recognition detected. Please confirm the student's identity.
            </DialogDescription>
          </DialogHeader>
          
          <div className="space-y-4">
            {confirmationDialog.capturedFace && (
              <div className="aspect-square w-32 mx-auto rounded-lg overflow-hidden">
                <img
                  src={confirmationDialog.capturedFace}
                  alt="Captured face"
                  className="w-full h-full object-cover"
                />
              </div>
            )}
            
            <div className="space-y-2">
              <div className="flex justify-between text-sm">
                <span>Confidence:</span>
                <Badge variant="secondary">
                  {Math.round(confirmationDialog.confidence * 100)}%
                </Badge>
              </div>
              <Progress value={confirmationDialog.confidence * 100} />
            </div>
            
            {confirmationDialog.suggestedStudents.length > 0 && (
              <div className="space-y-2">
                <p className="text-sm font-medium">Suggested Student:</p>
                <div className="p-2 rounded border">
                  <p className="font-medium">{confirmationDialog.suggestedStudents[0].fullName}</p>
                  <p className="text-xs text-muted-foreground">
                    {confirmationDialog.suggestedStudents[0].studentNumber ?? 'N/A'}
                  </p>
                </div>
              </div>
            )}
            
            <Select onValueChange={(studentId) => {
              const student = students.find(s => s.id === studentId);
              if (student) {
                confirmationDialog.onConfirm(studentId, 'Manual confirmation after low confidence detection');
              }
            }}>
              <SelectTrigger>
                <SelectValue placeholder="Or select different student..." />
              </SelectTrigger>
              <SelectContent>
                {students.map((student) => (
                  <SelectItem key={student.id} value={student.id}>
                    {student.fullName} ({student.studentNumber ?? 'N/A'})
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            
            <div className="flex gap-2 pt-4">
              <Button
                onClick={() => {
                  if (confirmationDialog.suggestedStudents.length > 0) {
                    confirmationDialog.onConfirm(
                      confirmationDialog.suggestedStudents[0].id,
                      'Confirmed suggested student'
                    );
                  }
                }}
                className="flex-1"
                disabled={confirmationDialog.suggestedStudents.length === 0}
              >
                Confirm
              </Button>
              <Button
                variant="outline"
                onClick={confirmationDialog.onDeny}
                className="flex-1"
              >
                Deny
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
};