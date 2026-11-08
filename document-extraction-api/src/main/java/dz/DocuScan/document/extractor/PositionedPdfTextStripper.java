package dz.DocuScan.document.extractor;

import dz.DocuScan.document.dto.PageSpan;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * PDFTextStripper that keeps the display-space bounding box of every line it
 * writes. PDFBox exposes coordinates adjusted for the page rotation through
 * {@code getXDirAdj()} / {@code getYDirAdj()}; the caller supplies the display
 * page dimensions so the result can be normalized to [0, 1].
 */
final class PositionedPdfTextStripper extends PDFTextStripper {

    private record RawLine(String text, double x0, double y0, double x1, double y1) {
    }

    private final double displayWidth;
    private final double displayHeight;
    private final int rotationDeg;
    private final List<RawLine> lines = new ArrayList<>();

    PositionedPdfTextStripper(double displayWidth, double displayHeight, int rotationDeg)
            throws IOException {
        this.displayWidth = Math.max(1.0, displayWidth);
        this.displayHeight = Math.max(1.0, displayHeight);
        this.rotationDeg = rotationDeg;
        setSortByPosition(true);
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
        super.writeString(text, textPositions);
        if (text == null || text.isBlank() || textPositions == null || textPositions.isEmpty()) {
            return;
        }

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (TextPosition position : textPositions) {
            double x = position.getXDirAdj();
            double baseline = position.getYDirAdj();
            double width = Math.abs(position.getWidthDirAdj());
            double height = Math.abs(position.getHeightDir());
            minX = Math.min(minX, x);
            minY = Math.min(minY, baseline - height);
            maxX = Math.max(maxX, x + width);
            maxY = Math.max(maxY, baseline);
        }

        if (minX == Double.MAX_VALUE) {
            return;
        }

        lines.add(new RawLine(text, minX, minY, maxX, maxY));
    }

    List<PageSpan> spans(String pageText) {
        if (pageText == null || pageText.isBlank()) {
            return List.of();
        }

        List<PageSpan> spans = new ArrayList<>(lines.size());
        int cursor = 0;
        for (RawLine line : lines) {
            String needle = line.text().strip();
            if (needle.isEmpty()) {
                continue;
            }

            int start = pageText.indexOf(needle, Math.max(0, cursor));
            if (start < 0) {
                start = pageText.indexOf(needle, 0);
            }
            if (start < 0) {
                continue;
            }

            int end = start + needle.length();
            cursor = end;
            spans.add(new PageSpan(
                    needle,
                    start,
                    end,
                    normalize(line.x0(), displayWidth),
                    normalize(line.y0(), displayHeight),
                    normalize(line.x1(), displayWidth),
                    normalize(line.y1(), displayHeight),
                    "PDF_POINTS",
                    displayWidth,
                    displayHeight,
                    rotationDeg,
                    null));
        }
        return List.copyOf(spans);
    }

    private double normalize(double value, double dimension) {
        return Math.max(0.0, Math.min(1.0, value / dimension));
    }
}
