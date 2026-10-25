package com.garage.config;

import com.garage.domain.Language;
import com.garage.domain.Role;
import com.garage.domain.User;
import com.garage.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the first administrator from ADMIN_EMAIL / ADMIN_PASSWORD, only when no ADMIN exists. */
@Component
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder, AppProperties props) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = props.admin().email();
        String password = props.admin().password();
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        if (email == null || email.isBlank() || password == null || password.length() < 8) {
            log.warn("No ADMIN account exists and ADMIN_EMAIL / ADMIN_PASSWORD (min 8 chars) are not set");
            return;
        }
        User admin = new User();
        admin.setEmail(email.trim().toLowerCase());
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setFirstName("Admin");
        admin.setLastName("Garage");
        admin.setRole(Role.ADMIN);
        admin.setLanguage(Language.FR);
        userRepository.save(admin);
        log.info("Initial administrator account created");
    }
}
