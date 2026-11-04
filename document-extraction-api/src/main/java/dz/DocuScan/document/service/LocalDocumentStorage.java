package dz.DocuScan.document.service;

import dz.DocuScan.document.config.StorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalDocumentStorage implements DocumentStorage {

    private final Path root;

    public LocalDocumentStorage(StorageProperties properties) {
        this.root = Path.of(properties.localPath()).toAbsolutePath().normalize();
    }

    @Override
    public String key(UUID organizationId, UUID documentId, String fileName) {
        return organizationId + "/" + documentId + "/" + safeFileName(fileName);
    }

    @Override
    public void put(String key, byte[] content) {
        try {
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, content, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException ex) {
            throw new IllegalStateException("Impossible d'ecrire le fichier dans le stockage local.", ex);
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException ex) {
            throw new IllegalStateException("Impossible de lire le fichier depuis le stockage local.", ex);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ex) {
            throw new IllegalStateException("Impossible de supprimer le fichier du stockage local.", ex);
        }
    }

    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Cle de stockage invalide.");
        }
        return target;
    }

    private String safeFileName(String name) {
        if (name == null || name.isBlank()) return "document";
        return name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
    }
}
