package dz.DocuScan.document.web;

import jakarta.servlet.http.HttpServletRequest;

public record RequestInfo(String ipAddress, String userAgent) {

    public static RequestInfo from(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
        return new RequestInfo(ip, request.getHeader("User-Agent"));
    }
}
