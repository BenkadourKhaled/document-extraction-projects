package dz.DocuScan.document.extractor;

import dz.DocuScan.document.exception.DocumentException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;

@Component
public class DocumentTypeDetector {
    private static final Set<String> ALLOWED = Set.of("application/pdf", "image/png", "image/jpeg", "image/tiff", "image/bmp", "image/webp");
    private final Tika tika = new Tika();
    public String detect(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new DocumentException(400, "Le fichier est vide.");
        try (var in = new ByteArrayInputStream(bytes)) {
            String mime = tika.detect(in);
            if (!ALLOWED.contains(mime)) throw new DocumentException(415, "Type MIME non pris en charge : " + mime);
            return mime;
        } catch (IOException e) { throw new DocumentException(400, "Impossible de lire le fichier.", e); }
    }
}
