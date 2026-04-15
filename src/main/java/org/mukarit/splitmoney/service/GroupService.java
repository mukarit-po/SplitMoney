package org.mukarit.splitmoney.service;

import lombok.RequiredArgsConstructor;
import org.mukarit.splitmoney.entity.Group;
import org.mukarit.splitmoney.entity.User;
import org.mukarit.splitmoney.repository.GroupRepository;
import org.mukarit.splitmoney.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GroupService {
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    @Transactional
    public Group createGroup(String name, Long creatorId) {
        User creator = userRepository.findById(creatorId).orElseThrow();
        Group group = Group.builder()
                .name(name)
                .creatorId(creatorId)
                .members(new HashSet<>(Collections.singletonList(creator)))
                .build();
        group = groupRepository.save(group);
        
        creator.setCurrentGroupId(group.getId());
        userRepository.save(creator);
        
        return group;
    }

    @Transactional
    public boolean joinGroup(Long groupId, Long userId) {
        Optional<Group> groupOpt = groupRepository.findById(groupId);
        Optional<User> userOpt = userRepository.findById(userId);

        if (groupOpt.isPresent() && userOpt.isPresent()) {
            Group group = groupOpt.get();
            User user = userOpt.get();
            group.getMembers().add(user);
            groupRepository.save(group);
            
            user.setCurrentGroupId(groupId);
            userRepository.save(user);
            return true;
        }
        return false;
    }

    public Optional<Group> getGroup(Long id) {
        return groupRepository.findById(id);
    }

    public List<Group> getAllGroups() {
        return groupRepository.findAll();
    }
}
