import type { FaceDataStatus } from '@/lib/api';

type FaceDataStatusShape = FaceDataStatus & {
  has_face_data?: boolean;
  hasFaceData?: boolean;
  image_count?: number;
  imageCount?: number;
  latest_status?: string | null;
  latestStatus?: string | null;
  updated_at?: string | null;
  updatedAt?: string | null;
};

const asShape = (status?: FaceDataStatus | null): FaceDataStatusShape | null => {
  if (!status) {
    return null;
  }
  return status as FaceDataStatusShape;
};

export function hasStoredFaceData(status?: FaceDataStatus | null): boolean {
  const shape = asShape(status);
  if (!shape) {
    return false;
  }
  const value = shape.hasFaceData ?? shape.has_face_data;
  return Boolean(value);
}

export function faceDataImageCount(status?: FaceDataStatus | null): number {
  const shape = asShape(status);
  if (!shape) {
    return 0;
  }
  const value = shape.imageCount ?? shape.image_count;
  return typeof value === 'number' ? value : 0;
}

export function faceDataLatestStatus(status?: FaceDataStatus | null): string | null {
  const shape = asShape(status);
  if (!shape) {
    return null;
  }
  const value = shape.latestStatus ?? shape.latest_status;
  return typeof value === 'string' && value.length > 0 ? value : null;
}

export function faceDataUpdatedAt(status?: FaceDataStatus | null): string | null {
  const shape = asShape(status);
  if (!shape) {
    return null;
  }
  const value = shape.updatedAt ?? shape.updated_at;
  return typeof value === 'string' && value.length > 0 ? value : null;
}
