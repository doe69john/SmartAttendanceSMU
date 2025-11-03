import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertTriangle, Camera, CheckCircle, X } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
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
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { useToast } from '@/hooks/use-toast';
import { useAuth } from '@/hooks/useAuth';
import { getDownloadUrl, getFileName, getPublicUrl, getStoragePath } from './faceImageResponse';
import {
  ApiError,
  analyzeCaptureFrame,
  createFaceData,
  deleteFaceData,
  deleteFaceImage,
  FaceDataDto,
  FaceImageUploadResponse,
  fetchFaceDataStatus,
  uploadFaceImage,
} from '@/lib/api';
import { FACE_DATA_STATUS_EVENT } from '@/lib/faceDataEvents';
import {
  faceDataImageCount,
  faceDataLatestStatus,
  faceDataUpdatedAt,
  hasStoredFaceData,
} from '@/lib/faceDataStatus';

interface CaptureStep {
  id: string;
  title: string;
  instruction: string;
  angle: 'front' | 'left' | 'right' | 'up' | 'down' | 'test';
  required: number;
  captured: number;
}

interface FaceEnrollmentStatus {
  hasFaceData: boolean;
  imageCount: number;
  latestStatus: string | null;
  updatedAt: string | null;
}

type Phase = 'intro' | 'capture' | 'processing' | 'success';
type ProcessingState = 'pending' | 'in_progress' | 'success' | 'error';

import type { FaceCaptureAnalysisResponse } from '@/lib/api';

type BoundingBox = { x: number; y: number; width: number; height: number };
type CapturedFrame = {
  blob: Blob;
  boundingBox?: BoundingBox;
  imageWidth: number;
  imageHeight: number;
  stepId: string;
  analysis?: Pick<FaceCaptureAnalysisResponse, 'brightness' | 'sharpness'>;
};

type ProcessingStageId = 'prepare' | 'upload' | 'record';

interface ProcessingStage {
  id: ProcessingStageId;
  label: string;
  status: ProcessingState;
  message?: string;
}

interface EnhancedFaceCaptureWizardProps {
  onComplete?: () => void;
}

const CAPTURE_STEPS: CaptureStep[] = [
  { id: 'front', title: 'Face Forward', instruction: 'Keep your head centered and look into the camera.', angle: 'front', required: 3, captured: 0 },
  { id: 'left', title: 'Turn Left', instruction: 'Rotate your head slightly to the left.', angle: 'left', required: 2, captured: 0 },
  { id: 'right', title: 'Turn Right', instruction: 'Rotate your head slightly to the right.', angle: 'right', required: 2, captured: 0 },
  { id: 'up', title: 'Tilt Up', instruction: 'Gently lift your chin upward.', angle: 'up', required: 2, captured: 0 },
  { id: 'down', title: 'Tilt Down', instruction: 'Angle your chin downward slightly.', angle: 'down', required: 1, captured: 0 },
  { id: 'test', title: 'Validation Images', instruction: 'Hold still for the final verification shots.', angle: 'front', required: 2, captured: 0 },
];

const TOTAL_REQUIRED = CAPTURE_STEPS.reduce((sum, step) => sum + step.required, 0);

const INITIAL_PROCESSING_STAGES: ProcessingStage[] = [
  { id: 'prepare', label: 'Preparing capture metadata', status: 'pending' },
  { id: 'upload', label: 'Uploading images to secure storage', status: 'pending' },
  { id: 'record', label: 'Saving capture metadata', status: 'pending' },
];

const STEP_INDEX: Record<Phase, number> = {
  intro: 1,
  capture: 2,
  processing: 3,
  success: 4,
};

const VALIDATION_THRESHOLDS = {
  minBrightness: 0.22,
  maxBrightness: 0.95,
  minSharpness: 18,
};

const blobToDataUrl = (blob: Blob) =>
  new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(typeof reader.result === 'string' ? reader.result : '');
    reader.onerror = () => reject(reader.error ?? new Error('Failed to read frame'));
    reader.readAsDataURL(blob);
  });

const selectPrimaryBoundingBox = (analysis: FaceCaptureAnalysisResponse) => {
  if (!analysis.boundingBoxes || analysis.boundingBoxes.length === 0) {
    return undefined;
  }
  const [first] = analysis.boundingBoxes;
  if (!first) {
    return undefined;
  }
  return {
    x: Math.max(0, Math.round(first.x)),
    y: Math.max(0, Math.round(first.y)),
    width: Math.max(0, Math.round(first.width)),
    height: Math.max(0, Math.round(first.height)),
  };
};

const formatTimestamp = (value?: string | null) => {
  if (!value) {
    return '-';
  }
  try {
    const date = new Date(value);
    return new Intl.DateTimeFormat(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    }).format(date);
  } catch (error) {
    return value;
  }
};

const InfinityGuidanceOverlay = () => (
  <svg
    className="absolute inset-0 h-full w-full pointer-events-none"
    viewBox="0 0 800 400"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    xmlnsXlink="http://www.w3.org/1999/xlink"
  >
    <path
      d="M100 200C100 120 170 60 250 60C330 60 400 120 400 200C400 280 470 340 550 340C630 340 700 280 700 200C700 120 630 60 550 60C470 60 400 120 400 200C400 280 330 340 250 340C170 340 100 280 100 200Z"
      stroke="rgba(255,255,255,0.25)"
      strokeWidth="2"
      strokeDasharray="12 12"
    />
    <circle r="12" fill="#38bdf8">
      <animateMotion dur="6s" repeatCount="indefinite" rotate="auto">
        <mpath xlinkHref="#infinity-path" />
      </animateMotion>
    </circle>
    <defs>
      <path
        id="infinity-path"
        d="M100 200C100 120 170 60 250 60C330 60 400 120 400 200C400 280 470 340 550 340C630 340 700 280 700 200C700 120 630 60 550 60C470 60 400 120 400 200C400 280 330 340 250 340C170 340 100 280 100 200Z"
      />
    </defs>
  </svg>
);

const formatPercent = (value: number) => `${Math.round(value * 100)}%`;

type CaptureIntervalHandle = ReturnType<typeof window.setInterval> | null;

export const EnhancedFaceCaptureWizard = ({
  onComplete,
}: EnhancedFaceCaptureWizardProps) => {
  const navigate = useNavigate();
  const { toast } = useToast();
  const { profile } = useAuth();

  const studentId = profile?.userId ?? null;

  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const captureIntervalRef = useRef<CaptureIntervalHandle>(null);
  const capturedImagesRef = useRef<CapturedFrame[]>([]);
  const captureInProgressRef = useRef(false);
  const processingRef = useRef(false);

  const [phase, setPhase] = useState<Phase>('intro');
  const [steps, setSteps] = useState<CaptureStep[]>(() => CAPTURE_STEPS.map(step => ({ ...step })));
  const [capturedImages, setCapturedImages] = useState<CapturedFrame[]>([]);
  const [uploadedImages, setUploadedImages] = useState<FaceImageUploadResponse[]>([]);
  const [createdFaceData, setCreatedFaceData] = useState<FaceDataDto[]>([]);
  const [captureStatus, setCaptureStatus] = useState('Preparing camera...');
  const [captureFlash, setCaptureFlash] = useState(false);
  const [cameraReady, setCameraReady] = useState(false);
  const [videoDimensions, setVideoDimensions] = useState({ width: 640, height: 480 });
  const [processingStages, setProcessingStages] = useState<ProcessingStage[]>(INITIAL_PROCESSING_STAGES);
  const [processingError, setProcessingError] = useState<string | null>(null);
  const [showGuidance, setShowGuidance] = useState(false);
  const [guidanceConfirmed, setGuidanceConfirmed] = useState(false);
  const [exitConfirmOpen, setExitConfirmOpen] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [statusLoading, setStatusLoading] = useState(false);
  const [status, setStatus] = useState<FaceEnrollmentStatus | null>(null);
  const [processingBusy, setProcessingBusy] = useState(false);
  const captureFlashTimeoutRef = useRef<number | null>(null);

  const totalCaptured = useMemo(() => steps.reduce((sum, step) => sum + step.captured, 0), [steps]);
  const progressValue = Math.min(100, (totalCaptured / TOTAL_REQUIRED) * 100);
  const hasExistingData = Boolean(status?.hasFaceData);

  useEffect(() => {
    capturedImagesRef.current = capturedImages;
  }, [capturedImages]);

  const validateFrame = useCallback(async (frame: CapturedFrame) => {
    try {
      const dataUrl = await blobToDataUrl(frame.blob);
      const analysis = await analyzeCaptureFrame(dataUrl);
      if (!analysis) {
        return { ok: false as const, reason: 'Validation service unavailable. Please try again.' };
      }

      const issues: string[] = [];
      const brightness = typeof analysis.brightness === 'number' ? analysis.brightness : 0;
      const sharpness = typeof analysis.sharpness === 'number' ? analysis.sharpness : 0;

      if (analysis.faceCount !== 1) {
        issues.push(
          analysis.message ||
          (analysis.faceCount === 0 ? 'No face detected' : 'More than one face detected.'),
        );
      }
      if (brightness < VALIDATION_THRESHOLDS.minBrightness) {
        issues.push('Lighting too dim');
      } else if (brightness > VALIDATION_THRESHOLDS.maxBrightness) {
        issues.push('Lighting too bright');
      }
      if (sharpness < VALIDATION_THRESHOLDS.minSharpness) {
        issues.push('Image looks blurry');
      }

      const boundingBox = selectPrimaryBoundingBox(analysis);
      if (!boundingBox) {
        issues.push('Unable to isolate the face');
      }

      if (issues.length > 0) {
        return { ok: false as const, reason: issues.join(', ') };
      }

      return {
        ok: true as const,
        boundingBox: boundingBox!,
        analysis: {
          brightness: Math.min(1, Math.max(0, brightness)),
          sharpness,
        },
      };
    } catch (error) {
      console.warn('Face validation failed', error);
      return { ok: false as const, reason: 'Unable to validate capture. Retrying...' };
    }
  }, []);

  const cleanupUploadedFiles = useCallback(async (files: FaceImageUploadResponse[]) => {
    if (!studentId || !files.length) {
      return;
    }
    const filenames = files
      .map(file => getFileName(file) ?? getStoragePath(file)?.split('/').pop() ?? null)
      .filter((name): name is string => Boolean(name));
    await Promise.all(
      filenames.map(fileName =>
        deleteFaceImage(studentId, fileName).catch(error => {
          console.warn('Failed to delete captured image', fileName, error);
        }),
      ),
    );
  }, [studentId]);

  const fetchStatus = useCallback(async () => {
    if (!studentId) {
      setStatus(null);
      window.dispatchEvent(
        new CustomEvent(FACE_DATA_STATUS_EVENT, {
          detail: {
            hasFaceData: false,
            has_face_data: false,
            imageCount: 0,
            studentId: null,
          },
        }),
      );
      return;
    }
    setStatusLoading(true);
    try {
      const payload = await fetchFaceDataStatus(studentId);
      const nextHasFaceData = hasStoredFaceData(payload);
      const nextImageCount = faceDataImageCount(payload);
      const nextLatestStatus = faceDataLatestStatus(payload);
      const nextUpdatedAt = faceDataUpdatedAt(payload);
      setStatus({
        hasFaceData: nextHasFaceData,
        imageCount: nextImageCount,
        latestStatus: nextLatestStatus,
        updatedAt: nextUpdatedAt,
      });
      window.dispatchEvent(
        new CustomEvent(FACE_DATA_STATUS_EVENT, {
          detail: {
            hasFaceData: nextHasFaceData,
            has_face_data: nextHasFaceData,
            imageCount: nextImageCount,
            studentId,
          },
        }),
      );
    } catch (error) {
      console.warn('Failed to load face data status', error);
    } finally {
      setStatusLoading(false);
    }
  }, [studentId]);

  useEffect(() => {
    fetchStatus().catch(() => {});
  }, [fetchStatus]);

  useEffect(() => {
    return () => {
      if (captureFlashTimeoutRef.current) {
        window.clearTimeout(captureFlashTimeoutRef.current);
      }
    };
  }, []);

  const stopCamera = useCallback(() => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach(track => track.stop());
      streamRef.current = null;
    }
    setCameraReady(false);
  }, []);

  const stopCaptureLoop = useCallback(() => {
    if (captureIntervalRef.current) {
      window.clearInterval(captureIntervalRef.current);
      captureIntervalRef.current = null;
    }
    captureInProgressRef.current = false;
  }, []);

  const resetCaptureState = useCallback(() => {
    stopCaptureLoop();
    stopCamera();
    captureInProgressRef.current = false;
    if (captureFlashTimeoutRef.current) {
      window.clearTimeout(captureFlashTimeoutRef.current);
      captureFlashTimeoutRef.current = null;
    }
    setCaptureFlash(false);
    setCaptureStatus('Preparing camera...');
    setCapturedImages([]);
    setUploadedImages([]);
    setCreatedFaceData([]);
    setSteps(CAPTURE_STEPS.map(step => ({ ...step })));
    setProcessingStages(INITIAL_PROCESSING_STAGES);
    setProcessingError(null);
    setProcessingBusy(false);
    setGuidanceConfirmed(false);
    setShowGuidance(false);
  }, [stopCamera, stopCaptureLoop]);

  const handleExitFlow = useCallback(async () => {
    stopCaptureLoop();
    stopCamera();
    setExitConfirmOpen(false);
    resetCaptureState();
    setPhase('intro');
    navigate('/', { replace: true });
  }, [navigate, resetCaptureState, stopCamera, stopCaptureLoop]);

  const handleDeleteData = useCallback(async () => {
    if (!studentId) {
      return;
    }
    try {
      await deleteFaceData({ studentId });
      toast({
        title: 'Face data deleted',
        description: 'You can re-run the setup whenever you are ready.',
      });
      setDeleteConfirmOpen(false);
      await fetchStatus();
    } catch (error) {
      console.error('Failed to delete face data', error);
      toast({
        title: 'Delete failed',
        description: error instanceof ApiError ? error.message : 'Unable to delete your face data. Please try again.',
        variant: 'destructive',
      });
    }
  }, [fetchStatus, studentId, toast]);

  const drawFrameToCanvas = useCallback(() => {
    if (!canvasRef.current || !videoRef.current) {
      return null;
    }
    const video = videoRef.current;
    const width = video.videoWidth || videoDimensions.width;
    const height = video.videoHeight || videoDimensions.height;
    if (!width || !height) {
      return null;
    }
    if (width !== videoDimensions.width || height !== videoDimensions.height) {
      setVideoDimensions({ width, height });
    }
    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return null;
    }
    canvas.width = width;
    canvas.height = height;
    ctx.drawImage(video, 0, 0, width, height);
    return canvas;
  }, [videoDimensions.height, videoDimensions.width]);

  const captureImage = useCallback(async () => {
    if (capturedImagesRef.current.length >= TOTAL_REQUIRED) {
      return;
    }

    const canvas = drawFrameToCanvas();
    if (!canvas) {
      return;
    }
    const targetStepId =
      steps.find(step => step.captured < step.required)?.id ?? CAPTURE_STEPS[CAPTURE_STEPS.length - 1]?.id ?? 'front';

    const baseFrame: CapturedFrame = {
      blob: await new Promise<Blob>((resolve, reject) => {
        canvas.toBlob(blob => {
          if (!blob) {
            reject(new Error('Unable to read camera frame.'));
            return;
          }
          resolve(blob);
        }, 'image/jpeg', 0.92);
      }),
      boundingBox: undefined,
      imageWidth: canvas.width,
      imageHeight: canvas.height,
      stepId: targetStepId,
    };

    const validation = await validateFrame(baseFrame);
    if (!validation.ok) {
      setCaptureStatus(`Retake needed: ${validation.reason}`);
      return;
    }

    const acceptedFrame: CapturedFrame = {
      ...baseFrame,
      boundingBox: validation.boundingBox,
      analysis: validation.analysis,
    };

    let nextCount = 0;
    setCapturedImages(prev => {
      if (prev.length >= TOTAL_REQUIRED) {
        return prev;
      }
      nextCount = prev.length + 1;
      return [...prev, acceptedFrame];
    });

    if (nextCount > 0) {
      setCaptureStatus(`Captured image ${nextCount}/${TOTAL_REQUIRED}`);
      setCaptureFlash(true);
      console.debug('[face-capture] captured image', nextCount);
      if (captureFlashTimeoutRef.current) {
        window.clearTimeout(captureFlashTimeoutRef.current);
      }
      captureFlashTimeoutRef.current = window.setTimeout(() => {
        setCaptureFlash(false);
      }, 700);
    }

    setSteps(prev => {
      const updated = [...prev];
      const stepIndex = updated.findIndex(step => step.captured < step.required);
      if (stepIndex === -1) {
        return updated;
      }
      const step = updated[stepIndex];
      updated[stepIndex] = { ...step, captured: step.captured + 1 };
      return updated;
    });
  }, [drawFrameToCanvas, steps, validateFrame]);

  const attemptAutoCapture = useCallback(async () => {
    if (!cameraReady || captureInProgressRef.current) {
      return;
    }
    if (capturedImagesRef.current.length >= TOTAL_REQUIRED) {
      return;
    }
    captureInProgressRef.current = true;
    try {
      setCaptureStatus(`Capturing image ${capturedImagesRef.current.length + 1}/${TOTAL_REQUIRED}...`);
      await captureImage();
    } catch (error) {
      console.error('Frame capture failed', error);
      setCaptureStatus('Unable to capture image. Retrying...');
    } finally {
      captureInProgressRef.current = false;
    }
  }, [cameraReady, captureImage]);

  const startAutoCapture = useCallback(() => {
    if (captureIntervalRef.current) {
      return;
    }
    setCaptureStatus('Capturing images... Hold steady.');
    captureIntervalRef.current = window.setInterval(() => {
      void attemptAutoCapture();
    }, 1200);
    void attemptAutoCapture();
  }, [attemptAutoCapture]);

  useEffect(() => {
    if (phase !== 'capture') {
      stopCaptureLoop();
      stopCamera();
      return;
    }

    let cancelled = false;

    const startCamera = async () => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: {
            width: { ideal: 1920 },
            height: { ideal: 1080 },
            frameRate: { ideal: 30, max: 60 },
            facingMode: 'user',
          },
        });
        if (cancelled) {
          stream.getTracks().forEach(track => track.stop());
          return;
        }
        streamRef.current = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          videoRef.current.onloadedmetadata = () => {
            if (!videoRef.current) {
              return;
            }
            const width = videoRef.current.videoWidth || 1280;
            const height = videoRef.current.videoHeight || 720;
            setVideoDimensions({ width, height });
            setCameraReady(true);
            setCaptureStatus('Camera ready. Hold steady for automatic captures.');
          };
        }
      } catch (error) {
        console.error('Camera access error', error);
        toast({
          title: 'Camera permission needed',
          description: 'Please allow camera access to continue with face setup.',
          variant: 'destructive',
        });
        setPhase('intro');
      }
    };

    startCamera();

    return () => {
      cancelled = true;
      stopCaptureLoop();
      stopCamera();
    };
  }, [phase, stopCamera, stopCaptureLoop, toast]);

  useEffect(() => {
    if (phase !== 'capture') {
      return;
    }
    if (capturedImages.length >= TOTAL_REQUIRED) {
      setCaptureStatus('Processing captured images...');
      stopCaptureLoop();
      stopCamera();
      setProcessingStages(INITIAL_PROCESSING_STAGES);
      setProcessingError(null);
      setPhase('processing');
    }
  }, [capturedImages.length, phase, stopCamera, stopCaptureLoop]);

  const processAllImages = useCallback(async () => {
    if (!studentId) {
      toast({
        title: 'Session expired',
        description: 'Please sign in again before enrolling your face.',
        variant: 'destructive',
      });
      setPhase('intro');
      return;
    }

    const frames = capturedImagesRef.current;
    if (!frames.length) {
      setPhase('capture');
      return;
    }

    setProcessingStages(prev => prev.map(stage =>
      stage.id === 'prepare'
        ? { ...stage, status: 'in_progress', message: 'Preparing capture metadata' }
        : stage,
    ));
    setProcessingError(null);

    const validatedFrames = frames;
    capturedImagesRef.current = validatedFrames;

    let uploadedFiles: FaceImageUploadResponse[] = [];

    try {
      const baseTimestamp = Date.now();
      uploadedFiles = [];
      const filesToUpload = validatedFrames.map((frame, index) => {
        const fileName = `capture_${baseTimestamp}_${index}.jpg`;
        const file = new File([frame.blob], fileName, { type: frame.blob.type || 'image/jpeg' });
        return { file, frame };
      });

      setProcessingStages(prev => prev.map(stage =>
        stage.id === 'prepare'
          ? { ...stage, status: 'success', message: undefined }
          : stage.id === 'upload'
            ? { ...stage, status: 'in_progress', message: undefined }
            : stage,
      ));

      for (const item of filesToUpload) {
        const uploadResponse = await uploadFaceImage(studentId, item.file, {
          frameWidth: item.frame.imageWidth,
          frameHeight: item.frame.imageHeight,
          boundingBox: item.frame.boundingBox,
        });
        uploadedFiles.push(uploadResponse);
      }

      setProcessingStages(prev => prev.map(stage =>
        stage.id === 'upload'
          ? { ...stage, status: 'success' }
          : stage.id === 'record'
            ? { ...stage, status: 'in_progress' }
            : stage,
      ));

      const storagePaths = uploadedFiles
        .map(file => getStoragePath(file) ?? getFileName(file) ?? '')
        .filter((path): path is string => Boolean(path));

      const lightingAverage =
        validatedFrames.reduce((sum, frame) => sum + (frame.analysis?.brightness ?? 0), 0) /
        Math.max(validatedFrames.length, 1);
      const sharpnessAverage =
        validatedFrames.reduce((sum, frame) => sum + (frame.analysis?.sharpness ?? 0), 0) /
        Math.max(validatedFrames.length, 1);
      const normalizedLighting = Math.min(1, Math.max(0, lightingAverage));
      const normalizedSharpness = Math.min(1, Math.max(0, sharpnessAverage / 60));
      const quality = {
        lighting: Number.isFinite(normalizedLighting) ? Number(normalizedLighting.toFixed(3)) : 0,
        sharpness: Number.isFinite(normalizedSharpness) ? Number(normalizedSharpness.toFixed(3)) : 0,
        pose: 0.8,
      };
      const detectedFaces: Array<{ confidence?: number }> = validatedFrames.map(() => ({ confidence: 1 }));
      const captureTimestamp = new Date().toISOString();
      const sharedMetadata = {
        storage_paths: storagePaths,
        capture_timestamp: captureTimestamp,
        quality_metrics: quality,
        total_images: uploadedFiles.length,
      };

      const createdRecords: FaceDataDto[] = [];
      for (const [index, uploaded] of uploadedFiles.entries()) {
        const imageUrl =
          getDownloadUrl(uploaded) ?? getPublicUrl(uploaded) ?? getStoragePath(uploaded) ?? undefined;
        const metadata = {
          ...sharedMetadata,
          storage_path: getStoragePath(uploaded) ?? undefined,
          file_name: getFileName(uploaded) ?? undefined,
          download_url: getDownloadUrl(uploaded) ?? undefined,
          public_url: getPublicUrl(uploaded) ?? undefined,
          capture_index: index,
          analysis: {
            brightness: validatedFrames[index]?.analysis?.brightness,
            sharpness: validatedFrames[index]?.analysis?.sharpness,
            bounding_box: validatedFrames[index]?.boundingBox,
          },
        };
        try {
          const record = await createFaceData(studentId, {
            image_url: imageUrl,
            quality_score: (quality.lighting + quality.sharpness + quality.pose) / 3,
            confidence_score: detectedFaces[index]?.confidence ?? detectedFaces[0]?.confidence ?? 0,
            is_primary: index === 0,
            processing_status: 'completed',
            metadata,
          });
          if (record) {
            createdRecords.push(record);
          }
        } catch (error) {
          if (error instanceof ApiError && (error.status === 403 || error.status === 404)) {
            continue;
          }
          throw error;
        }
      }

      setCreatedFaceData(createdRecords);

      setProcessingStages(prev => prev.map(stage =>
        stage.id === 'record'
          ? { ...stage, status: 'success' }
          : stage,
      ));

      setUploadedImages(uploadedFiles);
      setCapturedImages([]);
      stopCaptureLoop();
      stopCamera();
      setPhase('success');
      const successMessage = 'Your captures have been processed and stored securely.';
      toast({
        title: 'Face setup complete!',
        description: successMessage,
      });
      await fetchStatus();
      if (onComplete) {
        onComplete();
      }
    } catch (error) {
      console.error('Processing error', error);
      await cleanupUploadedFiles(uploadedFiles);
      setProcessingStages(prev => prev.map(stage =>
        stage.status === 'success' ? stage : { ...stage, status: 'error' },
      ));
      setProcessingError('We were unable to process your captures. Please try again.');
      toast({
        title: 'Processing failed',
        description: 'We were unable to process your captures. Please try again.',
        variant: 'destructive',
      });
      setPhase('processing');
    }
  }, [
    cleanupUploadedFiles,
    fetchStatus,
    studentId,
    stopCamera,
    stopCaptureLoop,
    toast,
    onComplete,
  ]);

  useEffect(() => {
    if (phase !== 'processing' || processingRef.current) {
      return;
    }
    processingRef.current = true;
    setProcessingBusy(true);

    const run = async () => {
      try {
        await processAllImages();
      } finally {
        processingRef.current = false;
        setProcessingBusy(false);
      }
    };

    void run();
  }, [phase, processAllImages]);

  const handleGuidanceConfirmation = useCallback(() => {
    setShowGuidance(false);
    setGuidanceConfirmed(true);
    setCaptureStatus('Preparing camera...');
  }, []);

  useEffect(() => {
    if (phase !== 'capture' || !guidanceConfirmed || !cameraReady) {
      return;
    }
    startAutoCapture();
  }, [cameraReady, guidanceConfirmed, phase, startAutoCapture]);

const renderStepBadge = (currentPhase: Phase) => (
    <div className="flex items-center gap-2 text-xs uppercase tracking-wide text-muted-foreground">
      <span>Step {STEP_INDEX[currentPhase]}</span>
      <span className="text-muted-foreground/70">of 4</span>
    </div>
  );

  const renderHistoryCard = () => {
    if (!status?.hasFaceData) {
      return null;
    }
    return (
      <div className="w-full max-w-2xl rounded-2xl border border-border bg-card/60 p-6 shadow-sm">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground/80">Your photos</p>
            <h3 className="text-lg font-semibold">{status.latestStatus ?? 'Ready'}</h3>
            <p className="text-sm text-muted-foreground">Updated {formatTimestamp(status.updatedAt)}</p>
          </div>
          <div className="flex flex-col items-start gap-2 sm:flex-row sm:items-center">
            <div className="rounded-full bg-muted px-3 py-1 text-xs font-medium text-muted-foreground">
              {status.imageCount} photos stored
            </div>
            <Button size="sm" variant="outline" onClick={() => setDeleteConfirmOpen(true)}>
              Manage photos
            </Button>
          </div>
        </div>
      </div>
    );
  };

const renderIntroScreen = () => (
    <div className="min-h-screen w-full bg-background text-foreground flex flex-col">
      <div className="flex items-center justify-between px-6 py-5">
        {renderStepBadge('intro')}
        <Button variant="ghost" onClick={() => setExitConfirmOpen(true)}>
          <X className="mr-2 h-4 w-4" />
          Exit
        </Button>
      </div>

      <div className="flex-1 flex flex-col items-center gap-8 px-4 py-6">
        {renderHistoryCard()}
        <div className="w-full max-w-xl space-y-6 rounded-3xl border border-border bg-card p-8 shadow-lg">
          <div className="flex items-start gap-4">
            <div className="rounded-full bg-primary/10 p-3 text-primary">
              <Camera className="h-6 w-6" />
            </div>
            <div>
              <h1 className="text-2xl font-semibold">Enroll your face</h1>
              <p className="mt-2 text-sm text-muted-foreground">
                A quick guided capture keeps you eligible for automatic attendance.
              </p>
            </div>
          </div>

          <div className="rounded-2xl border border-border/60 bg-muted/60 p-6">
            <h3 className="text-sm font-semibold text-muted-foreground/80 uppercase tracking-wide">What to expect</h3>
            <ul className="mt-3 space-y-2 text-sm text-muted-foreground">
              <li>We'll walk you through a handful of short photo prompts.</li>
              <li>The camera captures automatically when your face is centered.</li>
              <li>You can retry or delete everything at any time.</li>
            </ul>
          </div>

          <Button
            size="lg"
            className="w-full"
            disabled={hasExistingData || statusLoading}
            onClick={() => {
              resetCaptureState();
              setPhase('capture');
              setShowGuidance(true);
            }}
          >
            {hasExistingData ? 'Face data already enrolled' : 'Start capture'}
          </Button>

          {hasExistingData && (
            <p className="text-center text-sm text-muted-foreground">
              Remove your existing photos to record a new set.
            </p>
          )}
        </div>
      </div>
    </div>
  );

const renderCaptureScreen = () => {
    return (
      <div className="relative min-h-screen w-full overflow-hidden bg-black text-foreground">
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted
          className="absolute inset-0 h-full w-full object-contain"
        />
        <div className="absolute inset-0 z-10 pointer-events-none">
          <InfinityGuidanceOverlay />
        </div>

        <div className="absolute top-0 left-0 right-0 z-20 flex justify-center px-4 py-6">
          <div className="flex w-full max-w-4xl items-center justify-between gap-6 rounded-2xl border border-border/70 bg-card/95 px-6 py-4 shadow-lg backdrop-blur">
            {renderStepBadge('capture')}
            <div className="flex flex-col items-end gap-2">
              <div className="flex flex-col items-end gap-2 sm:flex-row sm:items-center sm:gap-4">
                <Progress value={progressValue} className="h-2 w-48 bg-muted" />
                <Badge
                  variant="outline"
                  className={`${captureFlash ? 'border-transparent bg-success/90 text-success-foreground animate-pulse' : 'border-border text-foreground'}`}
                >
                  {Math.min(totalCaptured, TOTAL_REQUIRED)}/{TOTAL_REQUIRED}
                </Badge>
                <Button variant="secondary" size="sm" onClick={() => setExitConfirmOpen(true)}>
                  <X className="mr-2 h-4 w-4" />
                  Exit
                </Button>
              </div>
              <span className="text-sm text-muted-foreground">{captureStatus}</span>
            </div>
          </div>
        </div>

        <canvas ref={canvasRef} className="hidden" />

        <Dialog open={showGuidance} onOpenChange={setShowGuidance}>
          <DialogContent
            className="max-w-md"
            onInteractOutside={event => event.preventDefault()}
            onEscapeKeyDown={event => event.preventDefault()}
          >
            <DialogHeader>
              <DialogTitle>Follow the dot</DialogTitle>
              <DialogDescription>
                You'll see a dot moving in a figure-eight path. Tilt your head slightly in the dot's direction without turning too far.
              </DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button onClick={handleGuidanceConfirmation}>OK</Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
    );
  };

const renderProcessingScreen = () => (
    <div className="min-h-screen w-full bg-background text-foreground flex flex-col">
      <div className="flex items-center justify-between px-6 py-5">
        {renderStepBadge('processing')}
        <Button variant="ghost" onClick={() => setExitConfirmOpen(true)}>
          <X className="mr-2 h-4 w-4" />
          Exit
        </Button>
      </div>

      <div className="flex-1 flex flex-col items-center justify-center px-4">
        <div className="w-full max-w-xl rounded-3xl border border-border bg-card p-10 shadow-xl">
          <h2 className="text-xl font-semibold mb-4">Processing your captures</h2>
          <ul className="space-y-4">
            {processingStages.map(stage => (
              <li key={stage.id} className="flex items-start gap-3">
                <div className="mt-1">
                  {stage.status === 'success' && <CheckCircle className="h-5 w-5 text-success" />}
                  {stage.status === 'in_progress' && <span className="block h-5 w-5 rounded-full border-2 border-muted-foreground border-t-transparent animate-spin" />}
                  {stage.status === 'pending' && <span className="block h-5 w-5 rounded-full border border-dashed border-muted" />}
                  {stage.status === 'error' && <AlertTriangle className="h-5 w-5 text-destructive" />}
                </div>
                <div>
                  <p className="font-medium">{stage.label}</p>
                  {stage.message && <p className="mt-1 text-sm text-muted-foreground">{stage.message}</p>}
                </div>
              </li>
            ))}
          </ul>

          {processingError && (
            <div className="mt-6 rounded-2xl border border-destructive/30 bg-destructive/10 p-4 text-sm text-destructive">
              {processingError}
            </div>
          )}

          <div className="mt-8 flex flex-col gap-3 sm:flex-row sm:justify-end">
            <Button
              variant="secondary"
              onClick={() => {
                resetCaptureState();
                setPhase('capture');
                setShowGuidance(true);
              }}
            >
              Try capture again
            </Button>
            <Button
              onClick={() => {
                setProcessingStages(INITIAL_PROCESSING_STAGES.map((stage, index) => (
                  index === 0 ? { ...stage, status: 'in_progress' } : stage
                )));
                setProcessingError(null);
                processingRef.current = false;
                setPhase('processing');
              }}
              disabled={processingBusy}
            >
              Retry
            </Button>
          </div>
        </div>
      </div>
    </div>
  );

const renderSuccessScreen = () => (
    <div className="min-h-screen w-full bg-background text-foreground flex flex-col">
      <div className="flex items-center justify-between px-6 py-5">
        {renderStepBadge('success')}
        <Button variant="ghost" onClick={() => setExitConfirmOpen(true)}>
          <X className="mr-2 h-4 w-4" />
          Exit
        </Button>
      </div>
      <div className="flex-1 flex flex-col items-center justify-center px-4">
        <div className="max-w-lg w-full rounded-3xl border border-success/40 bg-success/10 p-10 text-center shadow-xl">
          <CheckCircle className="mx-auto mb-4 h-16 w-16 text-success" />
          <h2 className="text-2xl font-semibold mb-2">Face setup complete</h2>
          <p className="text-sm text-success mb-6">
            Your captures are safely stored and ready for future attendance sessions.
          </p>
          <div className="rounded-2xl border border-border bg-card p-6 mb-6 text-left">
            <h3 className="font-semibold mb-2">Capture summary</h3>
            <ul className="text-sm text-muted-foreground space-y-1 list-disc list-inside">
              <li>{uploadedImages.length} secure images uploaded</li>
              <li>{createdFaceData.length || uploadedImages.length} metadata entries synchronised</li>
            </ul>
          </div>
          <Button size="lg" className="w-full" onClick={() => setPhase('intro')}>
            Finish
          </Button>
        </div>
      </div>
    </div>
  );

  if (!studentId) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background text-foreground">
        <div className="text-center space-y-3">
          <h2 className="text-lg font-semibold">Sign in required</h2>
          <p className="text-sm text-muted-foreground">Please sign in to capture your face data.</p>
          <Button onClick={() => navigate('/auth')} variant="secondary">
            Go to sign in
          </Button>
        </div>
      </div>
    );
  }

  return (
    <>
      {phase === 'intro' && renderIntroScreen()}
      {phase === 'capture' && renderCaptureScreen()}
      {phase === 'processing' && renderProcessingScreen()}
      {phase === 'success' && renderSuccessScreen()}

      <AlertDialog open={exitConfirmOpen} onOpenChange={setExitConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Leave setup?</AlertDialogTitle>
            <AlertDialogDescription>
              Your progress will be discarded. You can restart the setup anytime.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Stay</AlertDialogCancel>
            <AlertDialogAction onClick={() => void handleExitFlow()} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">
              Leave setup
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={deleteConfirmOpen} onOpenChange={setDeleteConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete your face data?</AlertDialogTitle>
            <AlertDialogDescription>
              You&apos;ll need to capture new photos before automatic attendance works again.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Keep data</AlertDialogCancel>
            <AlertDialogAction onClick={() => void handleDeleteData()} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">
              Delete data
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
};
