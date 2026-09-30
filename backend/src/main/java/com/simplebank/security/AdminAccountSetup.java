package com.simplebank.security;

import com.simplebank.model.Address;
import com.simplebank.model.Role;
import com.simplebank.model.User;
import com.simplebank.repository.UserRepository;
import com.simplebank.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Creates the first admin (bank staff) account at startup from ADMIN_EMAIL and
 * ADMIN_PASSWORD, if it doesn't exist yet. Customers can't make themselves admins,
 * so this is how the first staff login comes to exist.
 * Runs after MongoSchemaSetup (@Order 1), so the validation rules are already in place.
 */
@Component
@Order(2)
public class AdminAccountSetup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountSetup.class);
    private static final Address BANK_ADDRESS = new Address("1 Bank Plaza", "Baltimore", "MD", "21201");

    private final AdminProperties admin;
    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountSetup(AdminProperties admin, UserRepository userRepository,
                             UserService userService, PasswordEncoder passwordEncoder) {
        this.admin = admin;
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!admin.isConfigured()) {
            log.info("ADMIN_EMAIL / ADMIN_PASSWORD not set: no admin account was created");
            return;
        }
        String email = admin.email().trim().toLowerCase();
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            if (existing.get().getRole() == Role.ADMIN) {
                log.info("Admin account ready: {}", email);
            } else {
                log.warn("{} exists but is not an admin; no admin account was created", email);
            }
            return;
        }
        userService.registerUser("Bank Administrator", email, BANK_ADDRESS,
                passwordEncoder.encode(admin.password()), Role.ADMIN);
        log.info("Created admin account {}", email);
    }
}
