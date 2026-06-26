package me.dudabertole.unidoc.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import me.dudabertole.unidoc.api.ArticlesApi;
import me.dudabertole.unidoc.service.ArticleService;
import me.dudabertole.unidoc.model.ArticleRegistration;
import me.dudabertole.unidoc.model.ArticleUploadInstruction;
import me.dudabertole.unidoc.model.ArticleUrl;
import me.dudabertole.unidoc.model.ArticleView;
import me.dudabertole.unidoc.model.BoostResponse;
import me.dudabertole.unidoc.model.ErrorResponse;
import me.dudabertole.unidoc.model.PaginatedArticlePreview;
import me.dudabertole.unidoc.model.WorkType;

import java.util.UUID;

@RestController
@AllArgsConstructor
public class ArticleController implements ArticlesApi {

    private final ArticleService articleService;

//    public ArticleController(ArticleService service, ArticleService articleService) {
//        this.articleService = articleService;
//    }

    @Override
    public ResponseEntity<ArticleView> getArticleDetails(UUID uuid) {

        // 1. Pega a autenticação atual do Spring Security
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUserUid = null;

        // 2. Checa se o usuário está de fato logado com um Token válido
        if (authentication != null && authentication.isAuthenticated() && !authentication.getPrincipal().equals("anonymousUser")) {
            currentUserUid = (String) authentication.getPrincipal();
        }

        // 3. Chama o serviço
        ArticleView articleView = articleService.getArticleDetails(uuid, currentUserUid);

        // 4. Retorna Status 200 OK
        return ResponseEntity.ok(articleView);
    }

    @Override
    public ResponseEntity<ArticleUrl> getArticleFile(UUID uuid) {

        // Chama o serviço para obter a URL do PDF
        ArticleUrl articleUrl = articleService.getArticleFileUrl(uuid);

        // Retorna Status 200 OK com o JSON contendo a URL
        return ResponseEntity.ok(articleUrl);
    }

    @Override
    public ResponseEntity<ArticleUploadInstruction> registerArticle(@Valid ArticleRegistration articleRegistration) {
        // 1. Extrai o ID do usuário autenticado (Firebase UID)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String firebaseUid = (String) authentication.getPrincipal();

        // 2. Chama o serviço para registrar e gerar as URLs
        ArticleUploadInstruction instruction = articleService.registerArticle(firebaseUid, articleRegistration);

        // 3. Retorna Status 201 Created
        return ResponseEntity.status(HttpStatus.CREATED).body(instruction);
    }

    @Override
    public ResponseEntity<PaginatedArticlePreview> searchArticles(String query, Integer minYear, WorkType workType, Integer page, Integer size, String sort) {
        PaginatedArticlePreview response = articleService.searchArticles(query, minYear, workType, page, size, sort);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<BoostResponse> toggleBoost(UUID id) { // Certifique-se de que aqui mudou para UUID após a compilação

        // 1. Extrai o ID do usuário autenticado (Firebase UID)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUserUid = (String) authentication.getPrincipal();

        // 2. Chama a regra de negócio
        BoostResponse response = articleService.toggleBoost(id, currentUserUid);

        // 3. Retorna Status 200 OK
        return ResponseEntity.ok(response);
    }

}