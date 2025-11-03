import { useEffect, useState } from "react";
import { useAuth } from '@/hooks/useAuth';
import { ApiError, fetchFaceDataStatus } from '@/lib/api';
import { FACE_DATA_STATUS_EVENT } from '@/lib/faceDataEvents';
import { hasStoredFaceData } from '@/lib/faceDataStatus';

type FaceDataStatusDetail = {
  hasFaceData?: boolean;
  has_face_data?: boolean;
  imageCount?: number;
  studentId?: string | null;
};

export const useFaceDataCheck = () => {
  const { profile } = useAuth();
  const [hasFaceData, setHasFaceData] = useState(false);
  const [checkingFaceData, setCheckingFaceData] = useState(true);
  const role = profile?.role;
  const userId = profile?.userId;

  useEffect(() => {
    const handleStatusUpdate = (event: Event) => {
      const { detail } = event as CustomEvent<FaceDataStatusDetail>;
      if (!detail) {
        return;
      }

      if (detail.studentId && userId && detail.studentId !== userId) {
        return;
      }

      const nextHasFaceData = detail.hasFaceData ?? detail.has_face_data;
      if (typeof nextHasFaceData !== 'undefined') {
        setHasFaceData(Boolean(nextHasFaceData));
        setCheckingFaceData(false);
      }
    };

    window.addEventListener(FACE_DATA_STATUS_EVENT, handleStatusUpdate);
    return () => {
      window.removeEventListener(FACE_DATA_STATUS_EVENT, handleStatusUpdate);
    };
  }, [userId]);

  useEffect(() => {
    let cancelled = false;

    const checkFaceData = async () => {
      if (!role || role !== 'student') {
        if (!cancelled) {
          setHasFaceData(true);
          setCheckingFaceData(false);
        }
        return;
      }

      if (!userId) {
        console.warn('Face data check skipped—missing profile.userId');
        if (!cancelled) {
          setHasFaceData(false);
          setCheckingFaceData(false);
        }
        return;
      }

      try {
        const status = await fetchFaceDataStatus(userId);
        if (!cancelled) {
          setHasFaceData(hasStoredFaceData(status));
        }
      } catch (error) {
        const statusCode = error instanceof ApiError ? error.status : undefined;
        if (statusCode === 403) {
          console.warn('Face data status is not accessible for this user.');
        } else {
          console.error('Face data check failed:', error);
        }
        if (!cancelled) {
          setHasFaceData(false);
        }
      } finally {
        if (!cancelled) {
          setCheckingFaceData(false);
        }
      }
    };

    void checkFaceData();

    return () => {
      cancelled = true;
    };
  }, [role, userId]);

  return { hasFaceData, checkingFaceData };
};
