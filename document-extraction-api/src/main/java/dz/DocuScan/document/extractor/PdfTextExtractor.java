package dz.DocuScan.document.extractor;

import dz.DocuScan.document.config.ExtractionProperties;
import dz.DocuScan.document.dto.PageResult;
import dz.DocuScan.document.exception.DocumentException;
import dz.DocuScan.document.model.ExtractionMethod;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

@Component
public class PdfTextExtractor {

    /** Plafond de pixels pour le rendu d'une page (évite l'OutOfMemoryError sur les grands formats). */
    private static final double MAX_RENDER_PIXELS = 50_000_000.0;

    private final ExtractionProperties properties;
    private final OcrExtractor ocr;

    public PdfTextExtractor(ExtractionProperties properties, OcrExtractor ocr) {
        this.properties = properties;
        this.ocr = ocr;
    }

    public int pageCount(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            int n = doc.getNumberOfPages();
            if (n < 1) {
                throw new DocumentException(422, "Le PDF ne contient aucune page.");
            }
            if (n > properties.maxPages()) {
                throw new DocumentException(422, "Le PDF dépasse la limite de pages autorisée.");
            }
            return n;
        } catch (InvalidPasswordException e) {
            throw new DocumentException(422, "Le PDF est protégé par mot de passe.", e);
        } catch (DocumentException e) {
            throw e;
        } catch (Exception e) {
            throw new DocumentException(422, "PDF corrompu ou illisible.", e);
        }
    }

    public List<PageResult> extract(byte[] bytes, String languages) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            int count = doc.getNumberOfPages();
            if (count < 1 || count > properties.maxPages()) {
                throw new DocumentException(422, "Nombre de pages PDF non valide.");
            }

            PDFRenderer renderer = new PDFRenderer(doc);

            List<PageResult> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                PDPage page = doc.getPage(i);
                DisplayPage display = displayPage(page);
                PositionedPdfTextStripper stripper = new PositionedPdfTextStripper(
                        display.width(), display.height(), display.rotation());
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String text = stripper.getText(doc).strip();

                if (text.length() >= properties.nativeTextMinCharacters()) {
                    result.add(new PageResult(
                            i + 1, text, ExtractionMethod.NATIVE, null, stripper.spans(text)));
                } else {
                    try {
                        BufferedImage image = renderer.renderImageWithDPI(i, safeDpi(doc, i));
                        result.add(ocr.extractPage(i + 1, image, languages));
                    } catch (Exception e) {
                        throw new DocumentException(422, "Échec OCR de la page " + (i + 1) + ".", e);
                    }
                }
            }
            return List.copyOf(result);
        } catch (InvalidPasswordException e) {
            throw new DocumentException(422, "Le PDF est protégé par mot de passe.", e);
        } catch (DocumentException e) {
            throw e;
        } catch (Exception e) {
            throw new DocumentException(422, "PDF corrompu ou illisible.", e);
        }
    }

    private record DisplayPage(double width, double height, int rotation) {
    }

    private DisplayPage displayPage(PDPage page) {
        PDRectangle crop = page.getCropBox();
        int rotation = Math.floorMod(page.getRotation(), 360);
        double width = crop.getWidth();
        double height = crop.getHeight();
        if (rotation == 90 || rotation == 270) {
            double swapped = width;
            width = height;
            height = swapped;
        }
        return new DisplayPage(width, height, rotation);
    }

    /** Réduit le DPI si la page rendue dépasserait le plafond de pixels. */
    private float safeDpi(PDDocument doc, int pageIndex) {
        float dpi = properties.pdfDpi();
        PDRectangle box = doc.getPage(pageIndex).getMediaBox();
        double pixels = (box.getWidth() / 72.0 * dpi) * (box.getHeight() / 72.0 * dpi);
        if (pixels > MAX_RENDER_PIXELS) {
            dpi = (float) (dpi * Math.sqrt(MAX_RENDER_PIXELS / pixels));
        }
        return Math.max(72f, dpi);
    }
}