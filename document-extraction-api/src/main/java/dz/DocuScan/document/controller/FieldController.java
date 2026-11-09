package dz.DocuScan.document.controller;

import dz.DocuScan.document.dto.ExtractedFieldView;
import dz.DocuScan.document.dto.FieldCorrectionRequest;
import dz.DocuScan.document.model.AuthenticatedUser;
import dz.DocuScan.document.service.FieldService;
import dz.DocuScan.document.web.RequestInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/extractions/{id}/fields")
@Tag(name = "Fields", description = "Traceable extracted fields and append-only corrections")
public class FieldController {

    private final FieldService fields;

    public FieldController(FieldService fields) {
        this.fields = fields;
    }

    @GetMapping
    @Operation(summary = "List extracted fields with sources and correction history")
    public List<ExtractedFieldView> list(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return fields.list(id, user);
    }

    @PostMapping("/{fieldKey}/corrections")
    @Operation(
            summary = "Append a correction to a field",
            description = "The raw extracted value is never modified. The correction applies to the "
                    + "whole document lineage and supersedes the previous active correction.")
    public ExtractedFieldView correct(
            @PathVariable UUID id,
            @PathVariable String fieldKey,
            @Valid @RequestBody FieldCorrectionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestInfo info = RequestInfo.from(httpRequest);
        return fields.correct(
                id, fieldKey, request, user, info.ipAddress(), info.userAgent());
    }
}
