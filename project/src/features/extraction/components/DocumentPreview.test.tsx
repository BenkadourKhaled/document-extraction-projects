import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { DocumentPreview } from '@/features/extraction/components/DocumentPreview';
import i18n from '@/i18n';

vi.mock('react-pdf', () => ({
  Document: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  Page: () => <div />,
  pdfjs: { GlobalWorkerOptions: {} },
}));

vi.mock('pdfjs-dist/build/pdf.worker.min.js?url', () => ({
  default: 'pdf-worker.js',
}));

describe('DocumentPreview', () => {
  it('shows a download fallback for TIFF files instead of a broken image', async () => {
    await i18n.changeLanguage('fr');
    const file = new File([new Uint8Array([0x49, 0x49, 0x2a, 0x00])], 'scan.tif', {
      type: 'image/tiff',
    });

    render(<DocumentPreview file={file} />);

    expect(await screen.findByText(/aperçu TIFF/i)).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    const link = screen.getByRole('link', {
      name: /télécharger le fichier original/i,
    });
    expect(link).toHaveAttribute('download', 'scan.tif');
    expect(link).toHaveAttribute('href', 'blob:test');
  });
});
