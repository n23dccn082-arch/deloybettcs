package com.taskmanager.security;

import com.taskmanager.entity.User;
import com.taskmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        User user;
        try {
            Long id = Long.parseLong(identifier);
            user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + identifier));
        } catch (NumberFormatException e) {
            user = userRepository.findByEmail(identifier)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + identifier));
        }
        return org.springframework.security.core.userdetails.User
            .withUsername(String.valueOf(user.getId()))
            .password(user.getPassword())
            .authorities("ROLE_USER")
            .build();
    }
}
