package dz.DocuScan.document.field;

import java.util.List;

/** Deterministic rule that detects one field in a single line of page text. */
public interface FieldRule {

    String fieldKey();

    String label();

    String extractorVersion();

    List<FieldMatch> matches(String line);
}
