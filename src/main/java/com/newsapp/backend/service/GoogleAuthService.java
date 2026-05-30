package com.newsapp.backend.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.newsapp.backend.model.User;
import com.newsapp.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final List<String> googleClientIds;

    public GoogleAuthService(
            UserRepository userRepository,
            @Value("${app.google.client-ids}") String googleClientIdsProperty) {
        this.userRepository = userRepository;
        this.googleClientIds = Arrays.stream(googleClientIdsProperty.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        if (this.googleClientIds.isEmpty()) {
            throw new IllegalStateException("app.google.client-ids must contain at least one OAuth client ID.");
        }
    }

    public User authenticateWithIdToken(String idTokenString) throws GeneralSecurityException, IOException {
        if (!StringUtils.hasText(idTokenString)) {
            throw new BadCredentialsException("Google ID token is required.");
        }

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance())
                .setAudience(googleClientIds)
                .build();

        GoogleIdToken idToken = verifier.verify(idTokenString);
        if (idToken == null) {
            throw new BadCredentialsException(
                    "Invalid Google ID token. Ensure the OAuth client ID in the app matches Google Cloud Console.");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String email = payload.getEmail();
        String providerId = payload.getSubject();
        String name = payload.get("name") != null ? payload.get("name").toString() : null;

        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new BadCredentialsException("Google email is not verified.");
        }
        if (!StringUtils.hasText(email)) {
            throw new BadCredentialsException("Google account did not return an email address.");
        }
        if (!StringUtils.hasText(name)) {
            name = email.split("@")[0];
        }

        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            user.setName(name);
            if (!StringUtils.hasText(user.getProvider())) {
                user.setProvider("google");
            }
            if (!StringUtils.hasText(user.getProviderId())) {
                user.setProviderId(providerId);
            }
            return userRepository.save(user);
        }

        User user = User.builder()
                .name(name)
                .email(email)
                .provider("google")
                .providerId(providerId)
                .build();
        return userRepository.save(user);
    }
}
