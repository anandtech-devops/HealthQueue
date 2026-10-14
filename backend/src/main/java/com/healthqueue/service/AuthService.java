package com.healthqueue.service;

import com.healthqueue.domain.Doctor;
import com.healthqueue.domain.Role;
import com.healthqueue.domain.User;
import com.healthqueue.dto.Dtos.AuthResponse;
import com.healthqueue.dto.Dtos.LoginRequest;
import com.healthqueue.dto.Dtos.RegisterRequest;
import com.healthqueue.dto.Dtos.UserDto;
import com.healthqueue.repository.DoctorRepository;
import com.healthqueue.repository.UserRepository;
import com.healthqueue.security.AuthUser;
import com.healthqueue.security.JwtService;
import com.healthqueue.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    // Pre-computed hash so that unknown e-mails cost the same as wrong passwords (no user enumeration by timing).
    private final String dummyHash;

    private final UserRepository users;
    private final DoctorRepository doctors;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, DoctorRepository doctors, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.doctors = doctors;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    /** Public self-registration always creates a PATIENT. Doctors are created by an admin. */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this e-mail already exists");
        }
        try {
            User user = users.saveAndFlush(new User(req.fullName().trim(), email, encoder.encode(req.password()), Role.PATIENT));
            log.info("Registered patient userId={}", user.getId());
            return new AuthResponse(jwt.generate(user), Mappers.toDto(user, null));
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("An account with this e-mail already exists");
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email().trim()).orElse(null);
        boolean ok = encoder.matches(req.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !ok || !user.isEnabled()) {
            log.warn("Failed login attempt");
            throw ApiException.unauthorized("Invalid e-mail or password");
        }
        return new AuthResponse(jwt.generate(user), Mappers.toDto(user, doctorIdFor(user)));
    }

    @Transactional(readOnly = true)
    public UserDto me(AuthUser auth) {
        User user = users.findById(auth.id()).orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
        return Mappers.toDto(user, doctorIdFor(user));
    }

    private Long doctorIdFor(User user) {
        if (user.getRole() != Role.DOCTOR) return null;
        return doctors.findByUserId(user.getId()).map(Doctor::getId).orElse(null);
    }
}
