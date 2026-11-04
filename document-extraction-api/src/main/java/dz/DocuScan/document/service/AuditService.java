package dz.DocuScan.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dz.DocuScan.document.dto.AuditEventView;
import dz.DocuScan.document.model.AuthenticatedUser;
import dz.DocuScan.document.repository.AuditRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void record(
            UUID organizationId,
            UUID actorId,
            String action,
            UUID documentId,
            String ipAddress,
            String userAgent,
            Map<String, ?> metadata) {
        repository.insert(
                organizationId,
                actorId,
                action,
                documentId,
                ipAddress,
                userAgent,
                metadata == null ? null : toJson(metadata));
    }

    public void record(
            AuthenticatedUser actor,
            String action,
            UUID documentId,
            String ipAddress,
            String userAgent,
            Map<String, ?> metadata) {
        if (actor == null) return;
        record(actor.organizationId(), actor.id(), action, documentId, ipAddress, userAgent, metadata);
    }

    public List<AuditEventView> latest(UUID organizationId, int limit) {
        return repository.latest(organizationId, Math.max(1, Math.min(limit, 500)));
    }

    private String toJson(Map<String, ?> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception ex) {
            return null;
        }
    }
}
