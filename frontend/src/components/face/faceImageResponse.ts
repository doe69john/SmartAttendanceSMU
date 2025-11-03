import type { FaceImageUploadResponse } from '@/lib/api';

function coerceString(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null;
  }
  const trimmed = value.trim();
  return trimmed.length ? trimmed : null;
}

function pickProperty(file: FaceImageUploadResponse, camelKey: string, snakeKey: string): string | null {
  const camelValue = coerceString((file as Record<string, unknown>)[camelKey]);
  if (camelValue) {
    return camelValue;
  }
  return coerceString((file as Record<string, unknown>)[snakeKey]);
}

export function getStoragePath(file: FaceImageUploadResponse): string | null {
  return pickProperty(file, 'storagePath', 'storage_path');
}

export function getFileName(file: FaceImageUploadResponse): string | null {
  return pickProperty(file, 'fileName', 'file_name');
}

export function getDownloadUrl(file: FaceImageUploadResponse): string | null {
  return pickProperty(file, 'downloadUrl', 'download_url');
}

export function getPublicUrl(file: FaceImageUploadResponse): string | null {
  return pickProperty(file, 'publicUrl', 'public_url');
}
