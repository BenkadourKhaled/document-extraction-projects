package dz.DocuScan.document.controller;

import dz.DocuScan.document.dto.AdminUserView;
import dz.DocuScan.document.dto.CreateUserRequest;
import dz.DocuScan.document.dto.ResetPasswordRequest;
import dz.DocuScan.document.dto.UpdateUserRequest;
import dz.DocuScan.document.dto.UserView;
import dz.DocuScan.document.model.AuthenticatedUser;
import dz.DocuScan.document.service.UserService;
import dz.DocuScan.document.web.RequestInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<AdminUserView> list(@AuthenticationPrincipal AuthenticatedUser actor) {
        return userService.list(actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest httpRequest) {
        return userService.create(request, actor, RequestInfo.from(httpRequest));
    }

    @PatchMapping("/{id}")
    public AdminUserView update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest httpRequest) {
        return userService.update(id, request, actor, RequestInfo.from(httpRequest));
    }

    @PostMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(
            @PathVariable UUID id,
            @Valid @RequestBody ResetPasswordRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor,
            HttpServletRequest httpRequest) {
        userService.resetPassword(id, request, actor, RequestInfo.from(httpRequest));
    }
}
