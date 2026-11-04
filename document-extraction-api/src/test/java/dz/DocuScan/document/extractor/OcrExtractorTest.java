package dz.DocuScan.document.extractor;

import dz.DocuScan.document.config.ExtractionProperties;
import dz.DocuScan.document.dto.PageResult;
import dz.DocuScan.document.model.ExtractionMethod;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcrExtractorTest {

    private static final String TEST_DATAPATH = resolveTestDatapath();

    private static final ExtractionProperties PROPS = new ExtractionProperties(
            20L * 1024 * 1024, 50, 10, 300, "fra+eng",
            List.of("fra", "eng", "ara"),
            TEST_DATAPATH,
            1, 1000, true, 2L * 1024 * 1024, 5, 2, 4, 50);

    private static String resolveTestDatapath() {
        String fromEnvironment = System.getenv("TESSDATA_PREFIX");
        if (fromEnvironment != null) {
            Path path = Path.of(fromEnvironment);
            if (Files.exists(path.resolve("eng.traineddata"))
                    || Files.exists(path.resolve("ara.traineddata"))) {
                return path.toString();
            }
            Path nested = path.resolve("tessdata");
            if (Files.exists(nested.resolve("eng.traineddata"))
                    || Files.exists(nested.resolve("ara.traineddata"))) {
                return nested.toString();
            }
        }
        return "path-that-does-not-exist"; // force the ./tessdata fallback
    }

    @Test
    void extractsTextFromImageUsingLocalTessdata() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(Path.of("tessdata"))
                        || Files.isDirectory(Path.of(TEST_DATAPATH)),
                "Local tessdata directory is not installed");

        byte[] image = imageWithText("HELLO OCR");
        OcrExtractor extractor = new OcrExtractor(PROPS, new ImagePreprocessor());

        List<PageResult> pages = extractor.extractImage(image, "eng");

        assertEquals(1, pages.size());
        assertEquals(ExtractionMethod.OCR, pages.get(0).method());
        assertFalse(pages.get(0).text().isBlank());
        assertTrue(pages.get(0).text().toUpperCase().contains("HELLO"));
        assertTrue(pages.get(0).confidence() != null && pages.get(0).confidence() > 0);
    }

    private static byte[] imageWithText(String text) throws Exception {
        BufferedImage image = new BufferedImage(600, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 64));
            graphics.drawString(text, 30, 120);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
