package dz.DocuScan.document.extractor;

import dz.DocuScan.document.config.ExtractionProperties;
import dz.DocuScan.document.dto.PageResult;
import dz.DocuScan.document.dto.PageSpan;
import dz.DocuScan.document.exception.DocumentException;
import dz.DocuScan.document.model.ExtractionMethod;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import net.sourceforge.tess4j.ITessAPI.TessPageIteratorLevel;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import org.springframework.stereotype.Component;

/**
 * Extraction OCR locale basée sur Tesseract. Aucun service externe, modèle
 * génératif ou LLM n'est utilisé : le texte est produit uniquement par le
 * moteur OCR et les données linguistiques locales.
 *
 * <p>Le pipeline essaie plusieurs passes (segmentation automatique et bloc
 * uniforme, binarisation Otsu puis Sauvola) et conserve la meilleure selon un
 * score qui combine confiance Tesseract et qualité du texte. Les documents
 * imprimés restent la cible principale ; l'écriture manuscrite n'est traitée
 * qu'en meilleur effort, Tesseract n'étant pas un moteur manuscrit.</p>
 */
@Component
public class OcrExtractor implements DocumentExtractor {

    /** Protège contre les images « bombes de décompression » (≈ 50 millions de pixels). */
    private static final long MAX_PIXELS = 50_000_000L;

    private static final int PSM_AUTO = 3;
    private static final int PSM_SINGLE_BLOCK = 6;

    private static final double FALLBACK_CONFIDENCE = 0.55;
    private static final double FALLBACK_QUALITY = 0.55;
    private static final int MIN_USEFUL_CHARACTERS = 3;

    private final ExtractionProperties properties;
    private final ImagePreprocessor preprocessor;
    private final String datapath;

    public OcrExtractor(ExtractionProperties properties, ImagePreprocessor preprocessor) {
        this.properties = properties;
        this.preprocessor = preprocessor;
        this.datapath = resolveDatapath(properties.tesseractDatapath());
    }

    /**
     * Uses the configured path when it exists, then falls back to a local
     * {@code tessdata} directory (useful on Windows where the default Linux
     * path from application.yml does not exist).
     */
    private static String resolveDatapath(String configured) {
        if (configured != null && !configured.isBlank()) {
            try {
                Path path = Path.of(configured);
                if (Files.isDirectory(path)) {
                    if (Files.exists(path.resolve("eng.traineddata"))
                            || Files.exists(path.resolve("ara.traineddata"))) {
                        return path.toAbsolutePath().toString();
                    }
                    // Standard Tesseract setups sometimes point TESSDATA_PREFIX
                    // to the parent of the tessdata directory.
                    Path nested = path.resolve("tessdata");
                    if (Files.isDirectory(nested)) {
                        return nested.toAbsolutePath().toString();
                    }
                    return path.toAbsolutePath().toString();
                }
            } catch (RuntimeException ignored) {
                // Invalid path: fall through to the local directory.
            }
        }
        Path local = Path.of("tessdata");
        if (Files.isDirectory(local)) return local.toAbsolutePath().toString();
        return configured == null ? "" : configured;
    }

    @Override
    public PageResult extractPage(int number, BufferedImage image, String languages) throws Exception {
        ImagePreprocessor.PreparedImage prepared = preprocessor.prepare(image);
        BufferedImage primary = preprocessor.binarizeOtsu(prepared);
        String requested = effectiveLanguages(languages);

        List<OcrCandidate> candidates = new ArrayList<>();
        candidates.add(ocr(primary, requested, PSM_AUTO));
        candidates.add(ocr(primary, requested, PSM_SINGLE_BLOCK));

        OcrCandidate best = best(candidates);
        BufferedImage adaptive = null;

        // Deuxième chance : contraste local et autre segmentation. Ces passes
        // ne sont lancées que si la première lecture est faible, ce qui évite
        // de multiplier le temps de traitement des documents propres.
        if (needsFallback(best)) {
            adaptive = preprocessor.binarizeAdaptive(prepared);
            candidates.add(ocr(adaptive, requested, PSM_AUTO));
            candidates.add(ocr(adaptive, requested, PSM_SINGLE_BLOCK));
            best = best(candidates);
        }

        // Documents mixtes arabe + latin : les passes par script évitent que
        // Tesseract ne mélange les alphabets sur une page mal détectée.
        if (needsFallback(best) && isMixedScript(requested)) {
            String arabic = subset(requested, "ara");
            String latin = latinSubset(requested);
            if (!arabic.isBlank()) {
                candidates.add(ocr(primary, arabic, PSM_AUTO));
            }
            if (!latin.isBlank()) {
                candidates.add(ocr(primary, latin, PSM_AUTO));
            }
            best = best(candidates);

            if (needsFallback(best) && adaptive != null) {
                if (!arabic.isBlank()) {
                    candidates.add(ocr(adaptive, arabic, PSM_AUTO));
                }
                if (!latin.isBlank()) {
                    candidates.add(ocr(adaptive, latin, PSM_AUTO));
                }
                best = best(candidates);
            }
        }

        return pageResult(number, best, prepared);
    }

    private PageResult pageResult(int number, OcrCandidate best, ImagePreprocessor.PreparedImage prepared) {
        List<PageSpan> spans = new ArrayList<>();
        String text = best.text();
        int cursor = 0;

        for (RawOcrLine line : best.lines()) {
            if (line.text().isEmpty()) {
                continue;
            }
            int start = text.indexOf(line.text(), cursor);
            if (start < 0) {
                continue;
            }
            int end = start + line.text().length();
            cursor = end;

            ImagePreprocessor.BoundingBox box =
                    prepared.toOriginalBoundingBox(line.boundingBox());
            spans.add(new PageSpan(
                    line.text(),
                    start,
                    end,
                    normalize(box.x0(), prepared.originalWidth()),
                    normalize(box.y0(), prepared.originalHeight()),
                    normalize(box.x1(), prepared.originalWidth()),
                    normalize(box.y1(), prepared.originalHeight()),
                    "OCR_PIXELS",
                    prepared.originalWidth(),
                    prepared.originalHeight(),
                    0,
                    line.confidence() == null ? null : Math.max(0.0, Math.min(1.0, line.confidence() / 100.0))));
        }

        return new PageResult(
                number, text, ExtractionMethod.OCR, best.confidence(), List.copyOf(spans));
    }

    private double normalize(double value, double dimension) {
        return Math.max(0.0, Math.min(1.0, value / Math.max(1.0, dimension)));
    }

    private OcrCandidate ocr(BufferedImage image, String language, int pageSegMode) throws Exception {
        // Tesseract n'est pas thread-safe : une instance par passe.
        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath(datapath);
        tesseract.setLanguage(language);
        tesseract.setPageSegMode(pageSegMode);

        List<Word> lines = tesseract.getWords(image, TessPageIteratorLevel.RIL_TEXTLINE);
        List<String> values = new ArrayList<>();
        List<RawOcrLine> rawLines = new ArrayList<>();
        double confidenceSum = 0.0;
        int confidenceCount = 0;

        for (Word line : lines) {
            String value = OcrTextNormalizer.normalizeLine(line.getText());
            if (!OcrTextNormalizer.isUsefulLine(value)) {
                continue;
            }
            if (!values.isEmpty() && values.get(values.size() - 1).equals(value)) {
                continue;
            }

            Rectangle box = line.getBoundingBox();
            if (box == null) {
                continue;
            }

            values.add(value);
            rawLines.add(new RawOcrLine(value, box, (double) line.getConfidence()));
            confidenceSum += Math.max(0.0, Math.min(100.0, line.getConfidence()));
            confidenceCount++;
        }

        String text = OcrTextNormalizer.normalize(String.join("\n", values));
        double confidence = confidenceCount == 0
                ? 0.0
                : Math.max(0.0, Math.min(1.0, confidenceSum / confidenceCount / 100.0));

        return new OcrCandidate(text, confidence, language, pageSegMode, List.copyOf(rawLines));
    }

    private OcrCandidate best(List<OcrCandidate> candidates) {
        return candidates.stream()
                .max(Comparator.comparingDouble(OcrCandidate::score)
                        .thenComparingInt(candidate ->
                                OcrTextNormalizer.usefulCharacters(candidate.text())))
                .orElse(OcrCandidate.EMPTY);
    }

    private boolean needsFallback(OcrCandidate candidate) {
        return candidate.text().isBlank()
                || OcrTextNormalizer.usefulCharacters(candidate.text()) < MIN_USEFUL_CHARACTERS
                || candidate.confidence() < FALLBACK_CONFIDENCE
                || OcrTextNormalizer.quality(candidate.text()) < FALLBACK_QUALITY;
    }

    private String effectiveLanguages(String languages) {
        Set<String> supported = new LinkedHashSet<>(properties.supportedLanguages());
        List<String> requested = splitLanguages(languages).stream()
                .filter(supported::contains)
                .distinct()
                .toList();

        if (requested.isEmpty()) {
            requested = splitLanguages(properties.defaultLanguages()).stream()
                    .filter(supported::contains)
                    .distinct()
                    .toList();
        }

        return requested.isEmpty() ? "eng" : String.join("+", requested);
    }

    private static List<String> splitLanguages(String languages) {
        if (languages == null || languages.isBlank()) {
            return List.of();
        }
        return Arrays.stream(languages.split("\\+", -1))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static boolean isMixedScript(String languages) {
        List<String> values = splitLanguages(languages);
        return values.contains("ara") && (values.contains("fra") || values.contains("eng"));
    }

    private static String latinSubset(String languages) {
        return subset(languages, "fra", "eng");
    }

    private static String subset(String languages, String... wanted) {
        List<String> values = splitLanguages(languages);
        List<String> selected = values.stream()
                .filter(value -> Arrays.asList(wanted).contains(value))
                .toList();
        return String.join("+", selected);
    }

    public List<PageResult> extractImage(byte[] content, String languages) {
        try (var imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (imageInput == null) {
                throw new DocumentException(422, "Image illisible ou décodeur indisponible.");
            }
            var readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new DocumentException(422, "Image illisible ou décodeur indisponible.");
            }
            var reader = readers.next();
            try {
                reader.setInput(imageInput);

                int count;
                try {
                    count = reader.getNumImages(true);
                } catch (Exception e) {
                    count = 1;
                }
                if (count < 1 || count > properties.maxPages()) {
                    throw new DocumentException(422, "Nombre de pages d'image non valide.");
                }

                List<PageResult> pages = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    long pixels = (long) reader.getWidth(i) * reader.getHeight(i);
                    if (pixels > MAX_PIXELS) {
                        throw new DocumentException(422, "Image trop grande pour être traitée.");
                    }
                    pages.add(extractPage(i + 1, reader.read(i), languages));
                }
                return List.copyOf(pages);
            } finally {
                reader.dispose();
            }
        } catch (DocumentException e) {
            throw e;
        } catch (Exception e) {
            throw new DocumentException(422, "Impossible de décoder ou traiter l'image.", e);
        }
    }

    record RawOcrLine(String text, Rectangle boundingBox, Double confidence) {
    }

    record OcrCandidate(
            String text,
            double confidence,
            String language,
            int pageSegMode,
            List<RawOcrLine> lines) {

        private static final OcrCandidate EMPTY =
                new OcrCandidate("", 0.0, "", PSM_AUTO, List.of());

        double score() {
            int useful = OcrTextNormalizer.usefulCharacters(text);
            if (useful == 0) {
                return 0.0;
            }
            double coverage = Math.min(1.0, useful / 60.0);
            return 0.55 * confidence
                    + 0.35 * OcrTextNormalizer.quality(text)
                    + 0.10 * coverage;
        }
    }
}
