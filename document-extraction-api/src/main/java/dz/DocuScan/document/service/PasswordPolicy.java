package dz.DocuScan.document.service;

import dz.DocuScan.document.exception.DocumentException;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new DocumentException(
                    400,
                    "Le mot de passe doit contenir au moins " + MIN_LENGTH + " caracteres.");
        }
        if (password.length() > MAX_LENGTH) {
            throw new DocumentException(
                    400,
                    "Le mot de passe ne doit pas depasser " + MAX_LENGTH + " caracteres.");
        }
    }
}
