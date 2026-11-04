package dz.DocuScan.document.service;

import dz.DocuScan.document.config.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class MinioDocumentStorage implements DocumentStorage {

    private final StorageProperties properties;
    private final MinioClient client;

    public MinioDocumentStorage(StorageProperties properties) {
        this.properties = properties;
        this.client = MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    @PostConstruct
    void ensureBucket() throws Exception {
        boolean exists = client.bucketExists(
                BucketExistsArgs.builder().bucket(properties.bucket()).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
        }
    }

    @Override
    public String key(UUID organizationId, UUID documentId, String fileName) {
        return organizationId + "/" + documentId + "/" + safeFileName(fileName);
    }

    @Override
    public void put(String key, byte[] content) {
        try (InputStream input = new ByteArrayInputStream(content)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(key)
                    .stream(input, content.length, -1)
                    .contentType("application/octet-stream")
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible d'ecrire le fichier dans MinIO/S3.", ex);
        }
    }

    @Override
    public byte[] get(String key) {
        try (InputStream input = client.getObject(GetObjectArgs.builder()
                .bucket(properties.bucket())
                .object(key)
                .build())) {
            return input.readAllBytes();
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de lire le fichier depuis MinIO/S3.", ex);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(key)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de supprimer le fichier de MinIO/S3.", ex);
        }
    }

    private String safeFileName(String name) {
        if (name == null || name.isBlank()) return "document";
        return name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
    }
}
