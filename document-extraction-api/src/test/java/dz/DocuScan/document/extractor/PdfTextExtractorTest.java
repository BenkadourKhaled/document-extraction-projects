package dz.DocuScan.document.extractor;

import dz.DocuScan.document.config.ExtractionProperties;
import dz.DocuScan.document.dto.PageResult;
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
    void countsPdfPages() throws Exception {
        assertEquals(1, extractor.pageCount(pdfWithText("one page")));
    }

    public static byte[] pdfWithText(String text) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }

            document.save(output);
            return output.toByteArray();
        }
    }
}
