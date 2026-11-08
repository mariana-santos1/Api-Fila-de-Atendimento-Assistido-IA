package com.fila.ciclica.api.user;

import com.fila.ciclica.common.ApiException;
import com.fila.ciclica.common.PageResponse;
import com.fila.ciclica.domain.Role;
import com.fila.ciclica.domain.User;
import com.fila.ciclica.repository.UserRepository;
import java.time.Instant;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw duplicateEmail();
        }

        Instant now = Instant.now();
        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role() == null ? Role.ATTENDANT : request.role());
        user.setActive(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            throw duplicateEmail();
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(int page, int size) {
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "INVALID_PAGE_SIZE", "Size must be between 1 and 100");
        }

        Page<User> result = userRepository.findAllByOrderByNameAsc(
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name")));

        return PageResponse.of(result.map(UserResponse::from));
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return UserResponse.from(findUser(id));
    }

    @Transactional
    public UserResponse updateStatus(Long id, boolean active) {
        User user = findUser(id);
        user.setActive(active);
        user.setUpdatedAt(Instant.now());
        return UserResponse.from(userRepository.save(user));
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ApiException duplicateEmail() {
        return new ApiException(
                HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "A user with this email already exists");
    }
}
