package org.mukarit.splitmoney.service;

import lombok.RequiredArgsConstructor;
import org.mukarit.splitmoney.entity.User;
import org.mukarit.splitmoney.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    @Transactional
    public User getOrCreateUser(Long id, String name, String username) {
        return userRepository.findById(id)
                .map(user -> {
                    boolean changed = false;
                    if (name != null && !name.equals(user.getName())) {
                        user.setName(name);
                        changed = true;
                    }
                    if (username != null && !username.equals(user.getUsername())) {
                        user.setUsername(username);
                        changed = true;
                    }
                    return changed ? userRepository.save(user) : user;
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .id(id)
                        .name(name)
                        .username(username)
                        .build()));
    }

    public Optional<User> getUserByUsername(String username) {
        if (username.startsWith("@")) {
            username = username.substring(1);
        }
        return userRepository.findByUsername(username);
    }

    public List<User> getUsersByName(String name) {
        return userRepository.findByNameContainingIgnoreCase(name);
    }

    public Optional<User> getUser(Long id) {
        return userRepository.findById(id);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
    
    @Transactional
    public void setCurrentGroup(Long userId, Long groupId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setCurrentGroupId(groupId);
            userRepository.save(user);
        });
    }
}
