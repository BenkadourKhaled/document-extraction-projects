package dz.DocuScan.document.service;

import java.util.UUID;

public interface DocumentStorage {

    String key(UUID organizationId, UUID documentId, String fileName);

    void put(String key, byte[] content);

    byte[] get(String key);

    void delete(String key);
}
