package dz.DocuScan.document.controller;

import dz.DocuScan.document.dto.AuditEventView;
import dz.DocuScan.document.model.AuthenticatedUser;
import dz.DocuScan.document.service.AuditService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditEventView> latest(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "100") int limit) {
        return auditService.latest(user.organizationId(), limit);
    }
}
