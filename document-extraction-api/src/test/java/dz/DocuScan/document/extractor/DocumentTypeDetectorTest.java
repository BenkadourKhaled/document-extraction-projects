package dz.DocuScan.document.extractor;

import dz.DocuScan.document.exception.DocumentException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentTypeDetectorTest {
    private final DocumentTypeDetector detector = new DocumentTypeDetector();

    @Test void rejectsEmpty() { assertThrows(DocumentException.class, () -> detector.detect(new byte[0])); }
    @Test void rejectsPlainText() { assertThrows(DocumentException.class, () -> detector.detect("plain text not a document".getBytes())); }
    @Test void rejectsNull() { assertThrows(DocumentException.class, () -> detector.detect(null)); }

    @Test void detectsPdfFromRealContent() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            assertEquals("application/pdf", detector.detect(output.toByteArray()));
        }
    }

    @Test void detectsPngFromRealContent() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        assertEquals("image/png", detector.detect(output.toByteArray()));
    }
}
