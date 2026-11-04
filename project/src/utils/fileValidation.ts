export const MAX_FILE_SIZE = 50 * 1024 * 1024;

export const ACCEPTED_MIME_TYPES: string[] = [
  'application/pdf',
  'image/png',
  'image/jpeg',
  'image/tiff',
  'image/bmp',
  'image/webp',
];

export const ACCEPTED_EXTENSIONS = [
  '.pdf', '.png', '.jpg', '.jpeg', '.tif', '.tiff', '.bmp', '.webp',
];

export type ValidationError =
  | 'invalid-type'
  | 'too-large';

export interface ValidationResult {
  valid: boolean;
  error?: ValidationError;
}

export function validateFile(file: File): ValidationResult {
  if (!ACCEPTED_MIME_TYPES.includes(file.type)) {
    const ext = '.' + file.name.split('.').pop()?.toLowerCase();
    if (!ACCEPTED_EXTENSIONS.includes(ext)) {
      return { valid: false, error: 'invalid-type' };
    }
  }
  if (file.size > MAX_FILE_SIZE) {
    return { valid: false, error: 'too-large' };
  }
  return { valid: true };
}

export function isPdf(file: File): boolean {
  return file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
}

export function isTiff(file: File): boolean {
  return file.type === 'image/tiff' || /\.tiff?$/i.test(file.name);
}

export function isImage(file: File): boolean {
  return file.type.startsWith('image/');
}
