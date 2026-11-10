package dz.DocuScan.document.extractor;

import org.junit.jupiter.api.Test;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class ImagePreprocessorTest {
    private final ImagePreprocessor preprocessor = new ImagePreprocessor();

    @Test
    void shouldReturnBinaryImage() {
        var image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        var result = preprocessor.preprocess(image);
        assertEquals(BufferedImage.TYPE_BYTE_BINARY, result.getType());
        assertTrue(result.getWidth() >= image.getWidth());
    }

    @Test
    void shouldReturnAdaptiveBinaryImage() {
        var image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        var result = preprocessor.preprocessAdaptive(image);
        assertEquals(BufferedImage.TYPE_BYTE_BINARY, result.getType());
        assertTrue(result.getWidth() >= image.getWidth());
    }

    @Test
    void shouldDownscaleVeryWideImages() {
        var image = new BufferedImage(4000, 100, BufferedImage.TYPE_INT_RGB);
        var result = preprocessor.preprocessAdaptive(image);
        assertTrue(result.getWidth() <= 3000);
    }

    @Test
    void shouldRejectNull() {
        assertThrows(IllegalArgumentException.class, () -> preprocessor.preprocess(null));
        assertThrows(IllegalArgumentException.class, () -> preprocessor.preprocessAdaptive(null));
    }

    @Test
    void mapsPreprocessedBoxBackToOriginalScale() {
        var image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        var prepared = preprocessor.prepare(image);

        var box = prepared.toOriginalBoundingBox(new Rectangle(100, 50, 20, 10));

        assertEquals(50.0, box.x0(), 0.5);
        assertEquals(25.0, box.y0(), 0.5);
        assertEquals(60.0, box.x1(), 0.5);
        assertEquals(30.0, box.y1(), 0.5);
    }

    @Test
    void mapsPreprocessedBoxBackThroughRotation() {
        var gray = new BufferedImage(100, 100, BufferedImage.TYPE_BYTE_GRAY);
        var prepared = new ImagePreprocessor.PreparedImage(gray, 128, 100, 100, 1.0, 1.0, 90.0);

        var box = prepared.toOriginalBoundingBox(new Rectangle(50, 0, 10, 10));

        assertEquals(0.0, box.x0(), 0.5);
        assertEquals(40.0, box.y0(), 0.5);
        assertEquals(10.0, box.x1(), 0.5);
        assertEquals(50.0, box.y1(), 0.5);
    }
}
