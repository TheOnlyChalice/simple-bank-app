package com.simplebank.security;

import com.simplebank.model.Account;
import com.simplebank.model.Role;
import com.simplebank.repository.AccountRepository;
import com.simplebank.service.AuditService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * "Is this yours?" checks, called by the controllers. Customers may only use their own
 * profile and accounts; admins may use everything. Every refusal is recorded in the
 * audit log as ACCESS_DENIED, since poking at other people's accounts is a fraud signal.
 */
@Component
public class AccessGuard {

    private final AccountRepository accountRepository;
    private final AuditService auditService;

    public AccessGuard(AccountRepository accountRepository, AuditService auditService) {
        this.accountRepository = accountRepository;
        this.auditService = auditService;
    }

    /** The logged-in user, read from their (already verified) token. */
    public CurrentUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            return new CurrentUser(
                    Long.valueOf(jwt.getSubject()),
                    jwt.getClaimAsString("email"),
                    Role.valueOf(jwt.getClaimAsString("role")));
        }
        throw new AuthenticationCredentialsNotFoundException("Not logged in");
    }

    /** The caller must be this user, or an admin. */
    public void requireSelfOrAdmin(Long userId) {
        CurrentUser user = currentUser();
        if (user.isAdmin() || user.userId().equals(userId)) {
            return;
        }
        throw deny(userId, null, "You can only access your own profile and accounts");
    }

    /** The caller must own this account, or be an admin. */
    public void requireAccountOwnerOrAdmin(Long accountId) {
        CurrentUser user = currentUser();
        if (user.isAdmin()) {
            return;
        }
        Optional<Account> account = accountRepository.findById(accountId);
        if (account.isEmpty()) {
            return; // doesn't exist: the service answers with a 404
        }
        if (!account.get().getUserId().equals(user.userId())) {
            throw deny(account.get().getUserId(), accountId, "You can only access your own accounts");
        }
    }

    private AccessDeniedException deny(Long targetUserId, Long accountId, String message) {
        auditService.recordAccessDenied(targetUserId, accountId, message);
        return new AccessDeniedException(message);
    }
}
