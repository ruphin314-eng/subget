package com.budget.subget_backend.service;

import com.budget.subget_backend.security.CustomUserDetails;
import com.budget.subget_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .map(CustomUserDetails::new)
                // Message volontairement générique : ne pas révéler si l'email existe.
                .orElseThrow(() -> new UsernameNotFoundException("Identifiants invalides"));
    }
}
