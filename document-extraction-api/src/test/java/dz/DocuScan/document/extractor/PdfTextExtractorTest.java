package dz.DocuScan.document.extractor;

import dz.DocuScan.document.config.ExtractionProperties;
import dz.DocuScan.document.dto.PageResult;
import dz.DocuScan.document.dto.PageSpan;
import dz.DocuScan.document.model.ExtractionMethod;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PdfTextExtractorTest {

    private static final ExtractionProperties PROPS = new ExtractionProperties(
            20L * 1024 * 1024, 50, 10, 300, "fra+eng",
            List.of("fra", "eng", "ara"), "tessdata",
            1, 1000, true, 2L * 1024 * 1024, 5, 2, 4, 50);

    private final PdfTextExtractor extractor =
            new PdfTextExtractor(PROPS, new OcrExtractor(PROPS, new ImagePreprocessor()));

    @Test
    void extractsNativeTextLayerPageByPage() throws Exception {
        List<PageResult> pages = extractor.extract(pdfWithText("Hello PDFBox native extraction"), "fra+eng");

        assertEquals(1, pages.size());
        assertEquals(ExtractionMethod.NATIVE, pages.get(0).method());
        assertTrue(pages.get(0).text().contains("Hello PDFBox"));
        assertEquals(null, pages.get(0).confidence());
    }

    @Test
    void capturesNormalizedLineBoundingBoxesFromNativeText() throws Exception {
        List<PageResult> pages = extractor.extract(
                pdfWithText("Hello PDFBox native extraction"), "fra+eng");

        PageResult page = pages.get(0);
        assertFalse(page.spans().isEmpty());
        PageSpan span = page.spans().get(0);
        assertEquals("PDF_POINTS", span.sourceSpace());
        assertEquals(612.0, span.pageWidth(), 0.01);
        assertEquals(792.0, span.pageHeight(), 0.01);
        assertEquals(0, span.rotationDeg());
        assertEquals(0, span.charStart());
        assertEquals(page.text().indexOf(span.text()), span.charStart());
        assertEquals(span.text().length(), span.charEnd() - span.charStart());

        assertTrue(span.bboxX0() >= 0.0 && span.bboxX0() <= 1.0);
        assertTrue(span.bboxY0() >= 0.0 && span.bboxY0() <= 1.0);
        assertTrue(span.bboxX1() > span.bboxX0());
        assertTrue(span.bboxY1() > span.bboxY0());
        assertTrue(span.bboxX1() <= 1.0 && span.bboxY1() <= 1.0);
        assertTrue(span.bboxY0() < 0.20, "line starts near the top of the page");
    }

    @Test
    void normalizesSpansAgainstDisplayDimensionsOnRotatedPages() throws Exception {
        List<PageResult> pages = extractor.extract(
                pdfWithText("Rotated page text", 90), "fra+eng");

        PageResult page = pages.get(0);
        assertFalse(page.spans().isEmpty());
        PageSpan span = page.spans().get(0);
        assertEquals(792.0, span.pageWidth(), 0.01);
        assertEquals(612.0, span.pageHeight(), 0.01);
        assertEquals(90, span.rotationDeg());
        assertTrue(span.bboxX0() >= 0.0 && span.bboxX0() <= 1.0);
        assertTrue(span.bboxY0() >= 0.0 && span.bboxY0() <= 1.0);
        assertTrue(span.bboxX1() <= 1.0 && span.bboxY1() <= 1.0);
    }

    @Test
    void countsPdfPages() throws Exception {
        assertEquals(1, extractor.pageCount(pdfWithText("one page")));
    }

    public static byte[] pdfWithText(String text) throws Exception {
        return pdfWithText(text, 0);
    }

    public static byte[] pdfWithText(String text, int rotation) throws Exception {
        return pdfWithLines(rotation, text);
    }

    /** Builds a one-page PDF with one text line per entry, top to bottom. */
    public static byte[] pdfWithLines(String... lines) throws Exception {
        return pdfWithLines(0, lines);
    }

    private static byte[] pdfWithLines(int rotation, String... lines) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            page.setRotation(rotation);
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float y = 700;
                for (String line : lines) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(50, y);
                    content.showText(line);
                    content.endText();
                    y -= 20;
                }
            }

            document.save(output);
            return output.toByteArray();
        }
    }
}
