package dz.DocuScan.document.model;

public enum Role {
    ADMIN,
    REVIEWER,
    USER;

    public boolean canSeeOrganizationHistory() {
        return this == ADMIN || this == REVIEWER;
    }
}
