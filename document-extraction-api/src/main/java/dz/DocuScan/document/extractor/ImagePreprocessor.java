package dz.DocuScan.document.extractor;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.Arrays;
import org.springframework.stereotype.Component;

/**
 * Prétraitement des images avant OCR : niveaux de gris, redimensionnement,
 * correction légère de l'inclinaison (±5°), puis binarisation.
 *
 * <p>Deux binarisations sont disponibles : Otsu, robuste sur les documents
 * numérisés à contraste global correct, et Sauvola, adaptée aux photos, aux
 * éclairages inégaux et aux fonds texturés. Le texte reste noir sur fond blanc
 * dans les deux cas, ce qui correspond à l'attendu de Tesseract.</p>
 */
@Component
public class ImagePreprocessor {

    private static final int TARGET_WIDTH = 2000;
    private static final int MAX_WIDTH = 3000;
    private static final long MAX_PREPARED_PIXELS = 12_000_000L;
    private static final double MAX_UPSCALE = 2.0;

    private static final double MAX_SKEW_DEGREES = 5.0;
    private static final double SKEW_STEP_DEGREES = 0.5;
    private static final double MIN_CORRECTION_DEGREES = 0.3;
    private static final int SKEW_SAMPLE_WIDTH = 600;
    private static final int MIN_DARK_POINTS = 500;

    private static final double SAUVOLA_K = 0.20;

    /**
     * Binarisation Otsu, utilisée comme passe principale.
     */
    public BufferedImage preprocess(BufferedImage source) {
        PreparedImage prepared = prepare(source);
        return binarizeOtsu(prepared);
    }

    /**
     * Binarisation adaptative Sauvola, utilisée en repli sur les images à
     * faible contraste ou à éclairage irrégulier.
     */
    public BufferedImage preprocessAdaptive(BufferedImage source) {
        PreparedImage prepared = prepare(source);
        return binarizeAdaptive(prepared);
    }

    /**
     * Prépare l'image une seule fois (gris, mise à l'échelle, redressement)
     * afin de pouvoir essayer plusieurs binarisations sans recalculer l'angle.
     */
    PreparedImage prepare(BufferedImage source) {
        if (source == null) {
            throw new IllegalArgumentException("Image illisible.");
        }

        BufferedImage gray = toGray(source);
        int initialThreshold = otsuThreshold(gray);
        double angle = detectSkew(gray, initialThreshold);
        if (Math.abs(angle) >= MIN_CORRECTION_DEGREES) {
            gray = rotate(gray, angle);
        }

        return new PreparedImage(gray, otsuThreshold(gray));
    }

    BufferedImage binarizeOtsu(PreparedImage prepared) {
        return binarize(prepared.gray(), prepared.otsuThreshold());
    }

    BufferedImage binarizeAdaptive(PreparedImage prepared) {
        int radius = Math.max(8, Math.min(30, prepared.gray().getWidth() / 100));
        return sauvola(prepared.gray(), radius, SAUVOLA_K);
    }

    private BufferedImage toGray(BufferedImage source) {
        double scale;
        if (source.getWidth() > MAX_WIDTH) {
            scale = (double) MAX_WIDTH / source.getWidth();
        } else if (source.getWidth() < TARGET_WIDTH) {
            scale = Math.min(MAX_UPSCALE, (double) TARGET_WIDTH / source.getWidth());
        } else {
            scale = 1.0;
        }

        int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(source.getHeight() * scale));

        // Les images très hautes (longues captures, tickets) peuvent dépasser
        // largement le plafond de mémoire pendant le calcul intégral de Sauvola.
        long pixels = (long) w * h;
        if (pixels > MAX_PREPARED_PIXELS) {
            double correction = Math.sqrt((double) MAX_PREPARED_PIXELS / pixels);
            w = Math.max(1, (int) Math.round(w * correction));
            h = Math.max(1, (int) Math.round(h * correction));
        }

        BufferedImage gray = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        try {
            // Fond blanc : évite le fond noir des PNG transparents.
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(source, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return gray;
    }

    private int otsuThreshold(BufferedImage gray) {
        int w = gray.getWidth();
        int h = gray.getHeight();
        WritableRaster raster = gray.getRaster();

        int[] histogram = new int[256];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                histogram[raster.getSample(x, y, 0)]++;
            }
        }

        long total = (long) w * h;
        double sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += (double) i * histogram[i];
        }

        double sumBackground = 0;
        long weightBackground = 0;
        double maxVariance = -1;
        int threshold = 127;

        for (int t = 0; t < 256; t++) {
            weightBackground += histogram[t];
            if (weightBackground == 0) {
                continue;
            }
            long weightForeground = total - weightBackground;
            if (weightForeground == 0) {
                break;
            }
            sumBackground += (double) t * histogram[t];
            double meanBackground = sumBackground / weightBackground;
            double meanForeground = (sum - sumBackground) / weightForeground;
            double variance = (double) weightBackground * weightForeground
                    * (meanBackground - meanForeground) * (meanBackground - meanForeground);
            if (variance > maxVariance) {
                maxVariance = variance;
                threshold = t;
            }
        }
        return threshold;
    }

    /**
     * Détecte l'angle (en degrés) à appliquer à l'image pour redresser le texte,
     * par profil de projection horizontale : les lignes de texte bien alignées
     * donnent le profil le plus « contrasté ».
     */
    private double detectSkew(BufferedImage gray, int threshold) {
        int step = Math.max(1, gray.getWidth() / SKEW_SAMPLE_WIDTH);
        int sw = gray.getWidth() / step;
        int sh = gray.getHeight() / step;
        WritableRaster raster = gray.getRaster();

        int[] xs = new int[sw * sh];
        int[] ys = new int[sw * sh];
        int n = 0;
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                if (raster.getSample(x * step, y * step, 0) <= threshold) {
                    xs[n] = x;
                    ys[n] = y;
                    n++;
                }
            }
        }
        if (n < MIN_DARK_POINTS) {
            return 0.0;
        }

        int offset = sw;
        int[] bins = new int[sh + 2 * sw + 2];
        double bestAngle = 0.0;
        long bestScore = -1;

        for (double angle = -MAX_SKEW_DEGREES; angle <= MAX_SKEW_DEGREES + 1e-9;
             angle += SKEW_STEP_DEGREES) {
            double radians = Math.toRadians(angle);
            double sin = Math.sin(radians);
            double cos = Math.cos(radians);

            Arrays.fill(bins, 0);
            for (int i = 0; i < n; i++) {
                int row = (int) Math.round(xs[i] * sin + ys[i] * cos) + offset;
                bins[row]++;
            }

            long score = 0;
            for (int count : bins) {
                score += (long) count * count;
            }
            if (score > bestScore) {
                bestScore = score;
                bestAngle = angle;
            }
        }
        return bestAngle;
    }

    private BufferedImage rotate(BufferedImage gray, double angleDegrees) {
        int w = gray.getWidth();
        int h = gray.getHeight();
        BufferedImage rotated = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = rotated.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.rotate(Math.toRadians(angleDegrees), w / 2.0, h / 2.0);
            g.drawImage(gray, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rotated;
    }

    private BufferedImage binarize(BufferedImage gray, int threshold) {
        int w = gray.getWidth();
        int h = gray.getHeight();
        WritableRaster in = gray.getRaster();

        BufferedImage binary = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);
        WritableRaster out = binary.getRaster();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setSample(x, y, 0, in.getSample(x, y, 0) > threshold ? 1 : 0);
            }
        }
        return binary;
    }

    /**
     * Binarisation locale de Sauvola. Le seuil dépend de la moyenne et de
     * l'écart-type locaux, ce qui conserve les traits fins sur les documents
     * photographiés ou ombrés.
     */
    private BufferedImage sauvola(BufferedImage gray, int radius, double k) {
        int w = gray.getWidth();
        int h = gray.getHeight();
        WritableRaster in = gray.getRaster();
        int stride = w + 1;

        long[] sum = new long[stride * (h + 1)];
        long[] sumSquares = new long[stride * (h + 1)];

        for (int y = 1; y <= h; y++) {
            long rowSum = 0;
            long rowSquares = 0;
            for (int x = 1; x <= w; x++) {
                int value = in.getSample(x - 1, y - 1, 0);
                rowSum += value;
                rowSquares += (long) value * value;
                int index = y * stride + x;
                sum[index] = sum[index - stride] + rowSum;
                sumSquares[index] = sumSquares[index - stride] + rowSquares;
            }
        }

        BufferedImage binary = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);
        WritableRaster out = binary.getRaster();
        for (int y = 0; y < h; y++) {
            int y0 = Math.max(0, y - radius);
            int y1 = Math.min(h - 1, y + radius);
            for (int x = 0; x < w; x++) {
                int x0 = Math.max(0, x - radius);
                int x1 = Math.min(w - 1, x + radius);
                long area = (long) (x1 - x0 + 1) * (y1 - y0 + 1);
                long localSum = rectangleSum(sum, stride, x0, y0, x1, y1);
                long localSquares = rectangleSum(sumSquares, stride, x0, y0, x1, y1);

                double mean = (double) localSum / area;
                double variance = Math.max(0.0, (double) localSquares / area - mean * mean);
                double deviation = Math.sqrt(variance);
                double threshold = mean * (1.0 + k * (deviation / 128.0 - 1.0));

                out.setSample(x, y, 0, in.getSample(x, y, 0) > threshold ? 1 : 0);
            }
        }
        return binary;
    }

    private long rectangleSum(long[] integral, int stride, int x0, int y0, int x1, int y1) {
        return integral[(y1 + 1) * stride + (x1 + 1)]
                - integral[y0 * stride + (x1 + 1)]
                - integral[(y1 + 1) * stride + x0]
                + integral[y0 * stride + x0];
    }

    record PreparedImage(BufferedImage gray, int otsuThreshold) {
    }
}
