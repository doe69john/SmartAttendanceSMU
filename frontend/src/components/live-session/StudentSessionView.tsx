import { useState, useRef, useEffect, useCallback } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { Camera, CameraOff, CheckCircle, Clock, AlertCircle } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { useToast } from '@/hooks/use-toast';
import {
  fetchSession,
  fetchSessionAttendance,
  recognizeFace,
  subscribeToSessionEvents,
  type AttendanceEvent,
  type SessionActionEvent,
  type SessionAttendanceRecord,
  type SessionDetails,
} from '@/lib/api';

interface StudentSessionViewProps {
  sessionId: string;
  onAttendanceMarked?: (status: string) => void;
}

type AttendanceStatus = 'pending' | 'present' | 'late' | 'absent';
type RecognitionState = 'idle' | 'recognizing' | 'success' | 'failed';

const normalizeAttendanceStatus = (status?: string | null): AttendanceStatus => {
  switch ((status ?? '').toLowerCase()) {
    case 'present':
      return 'present';
    case 'late':
      return 'late';
    case 'absent':
      return 'absent';
    default:
      return 'pending';
  }
};

export const StudentSessionView: React.FC<StudentSessionViewProps> = ({
  sessionId,
  onAttendanceMarked,
}) => {
  const { profile } = useAuth();
  const { toast } = useToast();
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const recognitionIntervalRef = useRef<number | null>(null);
  const unsubscribeRef = useRef<(() => void) | null>(null);

  const [sessionData, setSessionData] = useState<SessionDetails | null>(null);
  const [record, setRecord] = useState<SessionAttendanceRecord | null>(null);
  const recordRef = useRef<SessionAttendanceRecord | null>(null);
  const [cameraEnabled, setCameraEnabled] = useState(false);
  const [recognitionStatus, setRecognitionStatus] = useState<RecognitionState>('idle');
  const [lastRecognitionTime, setLastRecognitionTime] = useState<Date | null>(null);
  const [recognitionAttempts, setRecognitionAttempts] = useState(0);
  const [confidence, setConfidence] = useState(0);

  useEffect(() => {
    recordRef.current = record;
  }, [record]);

  const handleAttendanceUpdate = useCallback((payload: AttendanceEvent) => {
    const studentId = payload.student_id;
    if (!studentId || studentId !== profile?.id) {
      return;
    }
    setRecord(prev => ({
      id: prev?.id,
      sessionId: prev?.sessionId ?? sessionId,
      studentId,
      status: payload.status ?? prev?.status,
      confidenceScore: payload.confidence ?? prev?.confidenceScore,
      markedAt: payload.timestamp ?? prev?.markedAt,
      lastSeen: prev?.lastSeen,
      notes: prev?.notes,
      student: prev?.student,
    }));
    const normalizedStatus = normalizeAttendanceStatus(payload.status);
    setConfidence(current => payload.confidence ?? current);
    setRecognitionStatus(normalizedStatus === 'present' ? 'success' : 'idle');
    onAttendanceMarked?.(normalizedStatus);
    toast({
      title: 'Attendance Updated',
      description: `You have been marked as ${normalizedStatus}`,
    });
  }, [onAttendanceMarked, profile?.id, sessionId, toast]);

  const handleSessionActionUpdate = useCallback((payload: SessionActionEvent) => {
    if (!payload.status) {
      return;
    }
    setSessionData(prev => (prev ? { ...prev, status: payload.status } : prev));
  }, []);

  useEffect(() => {
    if (cameraEnabled) {
      startCamera();
      startRecognitionLoop();
    } else {
      stopCamera();
      stopRecognitionLoop();
    }
  }, [cameraEnabled]);

  const cleanup = () => {
    stopCamera();
    stopRecognitionLoop();
    if (unsubscribeRef.current) {
      unsubscribeRef.current();
      unsubscribeRef.current = null;
    }
  };

  const loadSessionData = useCallback(async () => {
    try {
      const session = await fetchSession(sessionId);
      setSessionData(session);
      const attendance = await fetchSessionAttendance(sessionId);
      const myRecord = attendance.find(entry => entry.studentId === profile?.id);
      if (myRecord) {
        setRecord(myRecord);
        setConfidence(myRecord.confidenceScore ?? 0);
        const status = normalizeAttendanceStatus(myRecord.status);
        setRecognitionStatus(status === 'present' ? 'success' : 'idle');
        onAttendanceMarked?.(status);
      } else {
        setRecord(null);
        setConfidence(0);
        setRecognitionStatus('idle');
      }
    } catch (error) {
      console.error('Failed to load session data', error);
      toast({
        title: 'Error',
        description: 'Could not load session data',
        variant: 'destructive',
      });
    }
  }, [sessionId, profile?.id, onAttendanceMarked, toast]);

  const attachEvents = useCallback(() => {
    unsubscribeRef.current?.();
    unsubscribeRef.current = subscribeToSessionEvents(sessionId, {
      onAttendance: handleAttendanceUpdate,
      onSessionAction: handleSessionActionUpdate,
      onError: () => console.warn('Session events stream disconnected for student view'),
    });
  }, [sessionId, handleAttendanceUpdate, handleSessionActionUpdate]);

  useEffect(() => {
    loadSessionData();
    attachEvents();
    return () => {
      cleanup();
    };
  }, [sessionId, loadSessionData, attachEvents]);

  const startCamera = async () => {
    if (!navigator.mediaDevices?.getUserMedia) {
      toast({
        title: 'Camera Unsupported',
        description: 'This browser does not support camera access.',
        variant: 'destructive',
      });
      setCameraEnabled(false);
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: true });
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play();
      }
    } catch (error) {
      console.error('Unable to start camera', error);
      toast({
        title: 'Camera Error',
        description: 'We could not access your camera.',
        variant: 'destructive',
      });
      setCameraEnabled(false);
    }
  };

  const stopCamera = () => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }
  };

  const startRecognitionLoop = () => {
    recognitionIntervalRef.current = window.setInterval(() => {
      if (normalizeAttendanceStatus(record?.status) === 'pending') {
        performRecognition();
      }
    }, 3000);
  };

  const stopRecognitionLoop = () => {
    if (recognitionIntervalRef.current) {
      clearInterval(recognitionIntervalRef.current);
      recognitionIntervalRef.current = null;
    }
  };

  const performRecognition = async () => {
    if (!videoRef.current || !canvasRef.current || recognitionStatus === 'recognizing') {
      return;
    }
    setRecognitionStatus('recognizing');
    setRecognitionAttempts((attempts) => attempts + 1);

    try {
      const canvas = canvasRef.current;
      const video = videoRef.current;
      const ctx = canvas.getContext('2d');
      if (!ctx) {
        throw new Error('Canvas unavailable');
      }
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      ctx.drawImage(video, 0, 0);
      const imageData = canvas.toDataURL('image/jpeg', 0.85);
      const base64Data = imageData.split(',')[1];
      const result = await recognizeFace({
        sessionId,
        imageData: base64Data,
        confidenceThreshold: 0.7,
      });
      setLastRecognitionTime(new Date());
      setConfidence(result.confidence);

      if (result.success && profile?.id) {
        const markedAt = new Date().toISOString();
        setRecognitionStatus('success');
        setRecord(prev => ({
          id: prev?.id,
          sessionId: prev?.sessionId ?? sessionId,
          studentId: profile.id,
          status: 'present',
          confidenceScore: result.confidence,
          markedAt,
          lastSeen: prev?.lastSeen,
          notes: prev?.notes,
          student: prev?.student,
        }));
        onAttendanceMarked?.('present');
        toast({
          title: 'Recognition Successful',
          description: `Attendance marked as present (${Math.round(result.confidence * 100)}% confidence)`,
        });
      } else if (result.requiresManualConfirmation) {
        setRecognitionStatus('failed');
        toast({
          title: 'Manual Confirmation Required',
          description: 'Confidence was too low; your professor will review manually.',
          variant: 'destructive',
        });
      } else {
        setRecognitionStatus('failed');
      }
    } catch (error) {
      console.error('Recognition error', error);
      setRecognitionStatus('failed');
      toast({
        title: 'Recognition Error',
        description: 'Failed to recognise your face. Please try again.',
        variant: 'destructive',
      });
    }
  };


  const toggleCamera = () => {
    setCameraEnabled((enabled) => !enabled);
  };

  const getRecognitionIcon = () => {
    switch (recognitionStatus) {
      case 'recognizing':
        return <Clock className="w-4 h-4 animate-spin" />;
      case 'success':
        return <CheckCircle className="w-4 h-4 text-green-500" />;
      case 'failed':
        return <AlertCircle className="w-4 h-4 text-red-500" />;
      default:
        return <Camera className="w-4 h-4" />;
    }
  };

  if (!sessionData) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-primary"></div>
      </div>
    );
  }

  const attendanceStatus = normalizeAttendanceStatus(record?.status);

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 py-8 px-4">
      <div className="max-w-4xl mx-auto space-y-6">
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle className="text-2xl">Live Session</CardTitle>
                <p className="text-muted-foreground text-sm">
                  {new Date(sessionData.sessionDate ?? sessionData.startTime).toLocaleString()}
                </p>
              </div>
              <div className="text-right">
                <Badge className="mb-2 capitalize">{attendanceStatus}</Badge>
                {confidence > 0 && (
                  <p className="text-sm text-muted-foreground">
                    Confidence: {Math.round(confidence * 100)}%
                  </p>
                )}
              </div>
            </div>
          </CardHeader>
        </Card>

        <div className="grid gap-6 md:grid-cols-[2fr,1fr]">
          <div className="space-y-4">
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle className="text-lg">Camera</CardTitle>
                  <div className="space-x-2">
                    <Button onClick={toggleCamera} variant={cameraEnabled ? 'outline' : 'default'}>
                      {cameraEnabled ? <CameraOff className="mr-2 h-4 w-4" /> : <Camera className="mr-2 h-4 w-4" />}
                      {cameraEnabled ? 'Stop Camera' : 'Start Camera'}
                    </Button>
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <div className="relative aspect-video rounded-xl overflow-hidden bg-black">
                  {cameraEnabled ? (
                    <>
                      <video ref={videoRef} className="w-full h-full object-cover" muted playsInline />
                      <div className="absolute top-4 right-4 bg-black/60 text-white px-3 py-1 rounded-full text-sm flex items-center space-x-2">
                        {getRecognitionIcon()}
                        <span className="capitalize">{recognitionStatus}</span>
                      </div>
                      {recognitionStatus === 'recognizing' && (
                        <div className="absolute inset-0 bg-blue-500/20 flex items-center justify-center">
                          <div className="bg-blue-500 text-white px-4 py-2 rounded-lg font-medium">
                            Recognizing...
                          </div>
                        </div>
                      )}
                      {recognitionStatus === 'success' && (
                        <div className="absolute inset-0 bg-green-500/20 flex items-center justify-center">
                          <div className="bg-green-500 text-white px-4 py-2 rounded-lg font-medium">
                            Recognition Successful!
                          </div>
                        </div>
                      )}
                    </>
                  ) : (
                    <div className="flex items-center justify-center h-full text-white">
                      <div className="text-center">
                        <Camera className="w-16 h-16 mx-auto mb-4 opacity-50" />
                        <p>Camera is disabled</p>
                        <p className="text-sm opacity-75">Click "Start Camera" to enable face recognition</p>
                      </div>
                    </div>
                  )}
                </div>
                <canvas ref={canvasRef} className="hidden" />
              </CardContent>
            </Card>
          </div>

          <div className="space-y-4">
            <Card>
              <CardHeader>
                <CardTitle className="text-lg flex items-center">
                  {getRecognitionIcon()}
                  <span className="ml-2">Recognition Status</span>
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div>
                  <div className="flex justify-between text-sm mb-1">
                    <span>Status</span>
                    <span className="capitalize">{recognitionStatus}</span>
                  </div>
                  <div className="flex justify-between text-sm mb-1">
                    <span>Attempts</span>
                    <span>{recognitionAttempts}</span>
                  </div>
                  {lastRecognitionTime && (
                    <div className="flex justify-between text-sm">
                      <span>Last Attempt</span>
                      <span>{lastRecognitionTime.toLocaleTimeString()}</span>
                    </div>
                  )}
                </div>
                {confidence > 0 && (
                  <div>
                    <div className="flex justify-between text-sm mb-2">
                      <span>Confidence</span>
                      <span>{Math.round(confidence * 100)}%</span>
                    </div>
                    <Progress value={confidence * 100} className="h-2" />
                  </div>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-lg">Instructions</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {attendanceStatus === 'pending' ? (
                  <>
                    <div className="flex items-start space-x-2">
                      <div className="w-2 h-2 bg-blue-500 rounded-full mt-2"></div>
                      <p className="text-sm">Enable your camera to start face recognition.</p>
                    </div>
                    <div className="flex items-start space-x-2">
                      <div className="w-2 h-2 bg-blue-500 rounded-full mt-2"></div>
                      <p className="text-sm">Look directly at the camera in good lighting.</p>
                    </div>
                    <div className="flex items-start space-x-2">
                      <div className="w-2 h-2 bg-blue-500 rounded-full mt-2"></div>
                      <p className="text-sm">Stay within the frame until recognition completes.</p>
                    </div>
                  </>
                ) : (
                  <div className="text-center py-4">
                    <CheckCircle className="w-12 h-12 text-green-500 mx-auto mb-2" />
                    <p className="text-sm font-medium">Attendance Marked</p>
                    <p className="text-xs text-muted-foreground">You can safely close this window.</p>
                  </div>
                )}
              </CardContent>
            </Card>
          </div>
        </div>
      </div>
    </div>
  );
};

