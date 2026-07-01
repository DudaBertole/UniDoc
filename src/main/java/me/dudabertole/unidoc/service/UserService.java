package me.dudabertole.unidoc.service;

import me.dudabertole.unidoc.entity.User;
import me.dudabertole.unidoc.model.UpdateUserProfile;
import me.dudabertole.unidoc.repository.ArticleRepository;
import me.dudabertole.unidoc.repository.UserRepository;
import me.dudabertole.unidoc.model.UserProfile;
import me.dudabertole.unidoc.model.UserInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ArticleRepository articleRepository;

    public UserService(UserRepository repository, ArticleRepository articleRepository) {
        this.userRepository = repository;
        this.articleRepository = articleRepository;
    }

    @Transactional
    public void createUserProfile(UUID firebaseUid, String email, UserProfile profile) {
        if (userRepository.existsById(firebaseUid)) {
            throw new IllegalArgumentException("Usuário já existe.");
        }

        User user = new User();
        user.setId(firebaseUid);
        user.setEmail(email);
        user.setFirstName(profile.getFirstName());
        user.setLastName(profile.getLastName());
        user.setBirthDate(profile.getBirthDate());
        user.setUniversity(profile.getUniversity());

        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserInfo getCurrentUserInfo(UUID firebaseUid) {
        User user = userRepository.findById(firebaseUid)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        int totalArticles = articleRepository.countByPublisherId(firebaseUid);
        int totalBoosts = articleRepository.countTotalBoostsByPublisherId(firebaseUid);

        return new UserInfo(
                user.getFirstName(),
                user.getLastName(),
                user.getBirthDate(),
                user.getUniversity(),
                user.getEmail(),
                totalArticles,
                totalBoosts
        );
    }

    @Transactional
    public UserInfo updateUserProfile(UUID firebaseUid, UpdateUserProfile updateData) {
        User user = userRepository.findById(firebaseUid)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        if (updateData.getFirstName() != null) user.setFirstName(updateData.getFirstName());
        if (updateData.getLastName() != null) user.setLastName(updateData.getLastName());
        if (updateData.getBirthDate() != null) user.setBirthDate(updateData.getBirthDate());
        if (updateData.getUniversity() != null) user.setUniversity(updateData.getUniversity());

        userRepository.save(user);
        return getCurrentUserInfo(firebaseUid); // Reutiliza a lógica de leitura
    }
}