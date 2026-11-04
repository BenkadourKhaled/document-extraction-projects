import { describe, expect, it } from 'vitest';
import { isTiff, validateFile, MAX_FILE_SIZE } from '@/utils/fileValidation';

function makeFile(name: string, type: string, size = 1024): File {
  const blob = new Blob([new Uint8Array(size)], { type });
  return new File([blob], name, { type });
}

describe('fileValidation', () => {
  it('accepts a PDF within size limit', () => {
    expect(validateFile(makeFile('doc.pdf', 'application/pdf')).valid).toBe(true);
  });

  it('accepts a PNG within size limit', () => {
    expect(validateFile(makeFile('img.png', 'image/png')).valid).toBe(true);
  });

  it('rejects unsupported types', () => {
    const result = validateFile(makeFile('file.txt', 'text/plain'));
    expect(result.valid).toBe(false);
    expect(result.error).toBe('invalid-type');
  });

  it('rejects files over 20 MB', () => {
    const oversized = MAX_FILE_SIZE + 1;
    const result = validateFile(makeFile('big.pdf', 'application/pdf', oversized));
    expect(result.valid).toBe(false);
    expect(result.error).toBe('too-large');
  });

  it('accepts by extension when mime is empty', () => {
    const blob = new Blob([new Uint8Array(1024)]);
    const file = new File([blob], 'scan.webp', {});
    expect(validateFile(file).valid).toBe(true);
  });

  it('detects TIFF by MIME or extension case-insensitively', () => {
    expect(isTiff(makeFile('scan.tif', 'image/tiff'))).toBe(true);
    expect(isTiff(makeFile('SCAN.TIFF', ''))).toBe(true);
    expect(isTiff(makeFile('scan.png', 'image/png'))).toBe(false);
  });
});
