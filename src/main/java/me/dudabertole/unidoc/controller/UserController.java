package me.dudabertole.unidoc.controller;

import com.google.firebase.auth.FirebaseToken;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import me.dudabertole.unidoc.api.UsersApi;
import me.dudabertole.unidoc.model.UpdateUserProfile;
import me.dudabertole.unidoc.model.UserInfo;
import me.dudabertole.unidoc.model.UserProfile;
// import me.dudabertole.unidoc.service.ArticleService;
import me.dudabertole.unidoc.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@AllArgsConstructor
public class UserController implements UsersApi {

    private final UserService userService;


    @Override
    public ResponseEntity<Void> createUserProfile(@Valid UserProfile userProfile) {

        // 1. Acessa o contexto de segurança da requisição atual
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 2. Extrai o UID (que colocamos como Principal no filtro)
        String principalString = (String) authentication.getPrincipal();
        UUID firebaseUid = UUID.nameUUIDFromBytes(principalString.getBytes());

        // 3. Extrai o E-mail do token completo (que colocamos como Credentials no filtro)
        FirebaseToken firebaseToken = (FirebaseToken) authentication.getCredentials();
        String email = firebaseToken.getEmail();

        // 4. Chama o service com os dados reais
        userService.createUserProfile(firebaseUid, email, userProfile);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    public ResponseEntity<UserInfo> getCurrentUser() {

        // 1. Acessa o contexto de segurança da requisição atual
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 2. Extrai o UID (que colocamos como Principal no filtro)
        String principalString = (String) authentication.getPrincipal();
        UUID firebaseUid = UUID.nameUUIDFromBytes(principalString.getBytes());

        // 3. Chama o service passando o UID real do usuário autenticado
        UserInfo userInfo = userService.getCurrentUserInfo(firebaseUid);

        return ResponseEntity.ok(userInfo);
    }

    @Override
    public ResponseEntity<UserInfo> updateUserProfile(UpdateUserProfile updateUserProfile) {

        // 1. Acessa o contexto de segurança da requisição atual
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 2. Extrai o UID do usuário autenticado (configurado no filtro)
        String principalString = (String) authentication.getPrincipal();
        UUID firebaseUid = UUID.nameUUIDFromBytes(principalString.getBytes());

        // 3. Chama o service passando o UID real e os dados que serão atualizados
        UserInfo updatedInfo = userService.updateUserProfile(firebaseUid, updateUserProfile);

        return ResponseEntity.ok(updatedInfo);
    }
}
