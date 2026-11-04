package dz.DocuScan.document.extractor;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Nettoyage et évaluation du texte produit par Tesseract.
 *
 * <p>Le moteur OCR peut renvoyer des caractères de contrôle, des marques
 * bidirectionnelles, des espaces insécables ou des formes de présentation
 * arabes. Ce composant normalise le texte en Unicode NFKC, supprime les
 * artefacts invisibles et fournit un score de qualité simple utilisé pour
 * choisir la meilleure passe OCR.</p>
 */
final class OcrTextNormalizer {

    private static final Pattern HORIZONTAL_WHITESPACE = Pattern.compile("[\\t\\x0B\\f\\r ]+");
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");
    private static final Pattern INVISIBLE = Pattern.compile(
            "[\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]");
    private static final Pattern MULTIPLE_BLANK_LINES = Pattern.compile("\\n{3,}");

    private OcrTextNormalizer() {
    }

    /** Normalise une ligne isolée (sans conserver de saut de ligne). */
    static String normalizeLine(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String value = Normalizer.normalize(text, Normalizer.Form.NFKC);
        value = INVISIBLE.matcher(value).replaceAll("");
        value = value.replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .replace('\u2007', ' ');
        value = CONTROL.matcher(value).replaceAll("");
        value = HORIZONTAL_WHITESPACE.matcher(value).replaceAll(" ");
        return value.strip();
    }

    /**
     * Normalise un texte multi-lignes. Les lignes vides successives sont
     * réduites à une seule ligne vide afin de préserver les paragraphes.
     */
    static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String unified = text.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder result = new StringBuilder();
        boolean previousBlank = false;

        for (String rawLine : unified.split("\n", -1)) {
            String line = normalizeLine(rawLine);
            if (line.isEmpty()) {
                if (result.length() > 0) {
                    previousBlank = true;
                }
                continue;
            }

            if (result.length() > 0) {
                result.append('\n');
                if (previousBlank && !endsWithDoubleNewline(result)) {
                    result.append('\n');
                }
            }
            result.append(line);
            previousBlank = false;
        }

        return MULTIPLE_BLANK_LINES.matcher(result.toString().strip()).replaceAll("\n\n");
    }

    private static boolean endsWithDoubleNewline(StringBuilder value) {
        int length = value.length();
        return length >= 2
                && value.charAt(length - 1) == '\n'
                && value.charAt(length - 2) == '\n';
    }

    /**
     * Indique si une ligne contient assez de lettres/chiffres pour être
     * conservée. Les lignes composées uniquement de ponctuation ou de
     * symboles de reconnaissance sont écartées.
     */
    static boolean isUsefulLine(String line) {
        String value = normalizeLine(line);
        if (value.isBlank() || value.indexOf('\uFFFD') >= 0) {
            return false;
        }

        int useful = usefulCharacters(value);
        int nonSpace = nonWhitespaceCharacters(value);
        if (useful == 0 || nonSpace == 0) {
            return false;
        }
        if (useful == 1 && nonSpace > 3) {
            return false;
        }

        double density = (double) useful / nonSpace;
        return density >= 0.25 || nonSpace <= 2;
    }

    /** Densité de caractères utiles (lettres et chiffres) dans le texte. */
    static double quality(String text) {
        String value = normalize(text);
        int nonSpace = nonWhitespaceCharacters(value);
        if (nonSpace == 0) {
            return 0.0;
        }

        int useful = usefulCharacters(value);
        double density = (double) useful / nonSpace;
        double lengthFactor = Math.min(1.0, useful / 30.0);
        return density * 0.8 + lengthFactor * 0.2;
    }

    static int usefulCharacters(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (Character.isLetter(codePoint) || Character.isDigit(codePoint)) {
                count++;
            }
            i += Character.charCount(codePoint);
        }
        return count;
    }

    private static int nonWhitespaceCharacters(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (!Character.isWhitespace(codePoint)) {
                count++;
            }
            i += Character.charCount(codePoint);
        }
        return count;
    }
}
