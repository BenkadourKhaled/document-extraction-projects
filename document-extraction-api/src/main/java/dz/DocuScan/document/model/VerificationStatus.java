package dz.DocuScan.document.model;

/**
 * Trust level of an extracted field.
 *
 * <p>{@link #VERIFIED} means the value was produced by a deterministic rule and
 * is backed by at least one source span. {@link #UNVERIFIED} means the value was
 * detected but no reliable source region is available. {@link #AMBIGUOUS} means
 * the value was detected but its interpretation cannot be proven without
 * guessing (for example an ambiguous day/month order).</p>
 */
public enum VerificationStatus {
    VERIFIED,
    UNVERIFIED,
    AMBIGUOUS
}
