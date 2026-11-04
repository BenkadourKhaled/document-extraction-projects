package dz.DocuScan.document.dto;

import dz.DocuScan.document.model.Role;

public record UpdateUserRequest(Role role, Boolean enabled) {
}
