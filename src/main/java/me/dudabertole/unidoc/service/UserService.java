package me.dudabertole.unidoc.service;

import me.dudabertole.unidoc.entity.User;
import me.dudabertole.unidoc.model.UpdateUserProfile;
import me.dudabertole.unidoc.repository.UserRepository;
import me.dudabertole.unidoc.model.UserProfile;
import me.dudabertole.unidoc.model.UserInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository repository) {
        this.userRepository = repository;
    }

    @Transactional
    public void createUserProfile(String firebaseUid, String email, UserProfile profile) {
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
    public UserInfo getCurrentUserInfo(String firebaseUid) {
        User user = userRepository.findById(firebaseUid)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        // Nota: Para preencher totalPublishedArticles e totalBoosts,
        // você precisará fazer queries nas tabelas 'articles' e 'article_boosts'.
        int mockTotalArticles = 42;
        int mockTotalBoosts = 42;

        return new UserInfo(
                user.getFirstName(),
                user.getLastName(),
                user.getBirthDate(),
                user.getUniversity(),
                user.getEmail(),
                mockTotalArticles,
                mockTotalBoosts
        );
    }

    @Transactional
    public UserInfo updateUserProfile(String firebaseUid, UpdateUserProfile updateData) {
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