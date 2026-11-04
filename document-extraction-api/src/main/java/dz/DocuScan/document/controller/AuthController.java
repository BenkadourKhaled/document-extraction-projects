package dz.DocuScan.document.controller;

import dz.DocuScan.document.dto.ChangePasswordRequest;
import dz.DocuScan.document.dto.LoginRequest;
import dz.DocuScan.document.dto.LoginResponse;
import dz.DocuScan.document.dto.UpdateProfileRequest;
import dz.DocuScan.document.dto.UserView;
import dz.DocuScan.document.model.AuthenticatedUser;
import dz.DocuScan.document.service.AuthService;
import dz.DocuScan.document.service.UserService;
import dz.DocuScan.document.web.RequestInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        RequestInfo info = RequestInfo.from(httpRequest);
        return authService.login(
                request.email().trim(),
                request.password(),
                info.ipAddress(),
                info.userAgent());
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal AuthenticatedUser user) {
        return authService.me(user);
    }

    @PatchMapping("/me")
    public UserView updateProfile(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateProfileRequest request,
            HttpServletRequest httpRequest) {
        return userService.updateProfile(
                request,
                user,
                RequestInfo.from(httpRequest));
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        userService.changePassword(
                request.currentPassword(),
                request.newPassword(),
                user,
                RequestInfo.from(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestInfo info = RequestInfo.from(httpRequest);
        authService.logout(user, info.ipAddress(), info.userAgent());
        return ResponseEntity.noContent().build();
    }
}
