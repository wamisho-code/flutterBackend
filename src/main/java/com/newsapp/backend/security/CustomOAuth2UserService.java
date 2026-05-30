package com.newsapp.backend.security;

import com.newsapp.backend.model.User;
import com.newsapp.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest oAuth2UserRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(oAuth2UserRequest);
        return processOAuth2User(oAuth2UserRequest, oAuth2User);
    }

    private OAuth2User processOAuth2User(OAuth2UserRequest oAuth2UserRequest, OAuth2User oAuth2User) {
        String registrationId = oAuth2UserRequest.getClientRegistration().getRegistrationId();
        
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String providerId;

        if ("google".equalsIgnoreCase(registrationId)) {
            providerId = oAuth2User.getAttribute("sub");
        } else if ("github".equalsIgnoreCase(registrationId)) {
            providerId = String.valueOf(oAuth2User.getAttribute("id"));
            if (!StringUtils.hasText(email)) {
                // Github email can be null, fallback to login name
                String login = oAuth2User.getAttribute("login");
                email = login + "@github.com";
            }
            if (!StringUtils.hasText(name)) {
                name = oAuth2User.getAttribute("login");
            }
        } else {
            throw new OAuth2AuthenticationException("Unsupported provider: " + registrationId);
        }

        if (!StringUtils.hasText(email)) {
            throw new OAuth2AuthenticationException("Email not found from OAuth2 provider");
        }

        Optional<User> userOptional = userRepository.findByEmail(email);
        User user;
        if (userOptional.isPresent()) {
            user = userOptional.get();
            // Update existing user details if provider matches
            user.setName(name);
            user = userRepository.save(user);
        } else {
            user = User.builder()
                    .name(name)
                    .email(email)
                    .provider(registrationId.toLowerCase())
                    .providerId(providerId)
                    .build();
            user = userRepository.save(user);
        }

        return UserPrincipal.create(user, oAuth2User.getAttributes());
    }
}
