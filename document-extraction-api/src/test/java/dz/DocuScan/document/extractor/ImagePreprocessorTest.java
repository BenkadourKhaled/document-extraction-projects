package dz.DocuScan.document.extractor;

import org.junit.jupiter.api.Test;
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
}
