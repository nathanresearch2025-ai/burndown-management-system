package com.burndown.auth.service;

import com.burndown.auth.entity.User;
import com.burndown.auth.repository.UserRepository;
import com.burndown.common.dto.UserDTO;
import com.burndown.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AuthService authService;

    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        return toDTO(user);
    }

    public Page<UserDTO> getUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toDTO);
    }

    @Transactional
    public UserDTO updateUser(Long id, User updates) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        if (updates.getFullName() != null) user.setFullName(updates.getFullName());
        if (updates.getAvatarUrl() != null) user.setAvatarUrl(updates.getAvatarUrl());
        if (updates.getPhone() != null) user.setPhone(updates.getPhone());
        if (updates.getTimezone() != null) user.setTimezone(updates.getTimezone());
        if (updates.getLanguage() != null) user.setLanguage(updates.getLanguage());
        return toDTO(userRepository.save(user));
    }

    private UserDTO toDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFullName(user.getFullName());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setIsActive(user.getIsActive());
        dto.setCreatedAt(user.getCreatedAt());
        // Load permissions
        Set<String> perms = authService.getUserPermissions(user.getId());
        dto.setPermissions(new java.util.ArrayList<>(perms));
        return dto;
    }
}
