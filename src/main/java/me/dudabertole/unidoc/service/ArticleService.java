package me.dudabertole.unidoc.service;

import me.dudabertole.unidoc.entity.Article;
import me.dudabertole.unidoc.entity.User;
import me.dudabertole.unidoc.model.*;
import me.dudabertole.unidoc.model.WorkType;
import me.dudabertole.unidoc.repository.ArticleRepository;
import me.dudabertole.unidoc.repository.ArticleSpecification;
import me.dudabertole.unidoc.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;

    public ArticleService(ArticleRepository articleRepository, UserRepository userRepository, S3Service s3Service) {
        this.articleRepository = articleRepository;
        this.userRepository = userRepository;
        this.s3Service = s3Service;
    }

    public PaginatedArticlePreview searchArticles(String query, Integer minYear, WorkType workType,
                                                  Integer page, Integer size, String sortParam) {

        Sort sort = getSortDirection(sortParam);
        Pageable pageable = PageRequest.of(page, size, sort);

        Specification<Article> spec = Specification.where(ArticleSpecification.containsKeyword(query))
                .and(ArticleSpecification.publishedAfterYear(minYear))
                .and(ArticleSpecification.hasWorkType(workType));

        Page<Article> articlePage = articleRepository.findAll(spec, pageable);

        return mapToPaginatedResponse(articlePage);
    }

    private Sort getSortDirection(String sortParam) {
        if (sortParam == null) return Sort.by(Sort.Direction.DESC, "boostCount"); // default

        return switch (sortParam.toUpperCase()) {
            case "TITLE" -> Sort.by(Sort.Direction.ASC, "title");
            case "PUBLICATION_DATE" -> Sort.by(Sort.Direction.DESC, "publicationDate");
            case "WORK_TYPE" -> Sort.by(Sort.Direction.ASC, "workType");
            case "BOOST_COUNT" -> Sort.by(Sort.Direction.DESC, "boostCount");
            default -> Sort.by(Sort.Direction.DESC, "boostCount");
        };
    }

    private PaginatedArticlePreview mapToPaginatedResponse(Page<Article> articlePage) {
        List<ArticlePreview> previews = articlePage.getContent().stream().map(article -> {
            ArticlePreview preview = new ArticlePreview();
            preview.setId(article.getId());
            preview.setTitle(article.getTitle());
            preview.setAuthors(article.getAuthors());
            preview.setPublicationDate(article.getPublicationDate());
            preview.setWorkType(me.dudabertole.unidoc.model.WorkType.fromValue(article.getWorkType().name()));
            preview.setBoostCount(article.getBoostCount());
            return preview;
        }).collect(Collectors.toList());

        PaginatedArticlePreview response = new PaginatedArticlePreview();
        response.setContent(previews);
        response.setPage(articlePage.getNumber());
        response.setSize(articlePage.getSize());
        response.setTotalElements((int) articlePage.getTotalElements());
        response.setTotalPages(articlePage.getTotalPages());
        response.setLast(articlePage.isLast());

        return response;
    }

    @Transactional
    public ArticleUploadInstruction registerArticle(UUID firebaseUid, ArticleRegistration registration) {

        User publisher = userRepository.findById(firebaseUid)
                .orElseThrow(() -> new RuntimeException("Article not found"));

        Article article = new Article();
        article.setId(UUID.randomUUID());
        article.setTitle(registration.getTitle());
        article.setPublicationDate(registration.getPublicationDate());
        article.setWorkType(me.dudabertole.unidoc.model.WorkType.valueOf(registration.getWorkType().name()));
        article.setAbstractText(registration.getAbstract());
        article.setAuthors(registration.getAuthors());
        article.setPublisher(publisher);

        String pdfKey = "articles/" + article.getId() + "/document.pdf";
        article.setPdfKey(pdfKey);

        String coverUploadUrl = null;
        if (Boolean.TRUE.equals(registration.getHasCover())) {
            String coverKey = "articles/" + article.getId() + "/cover.jpg";
            article.setCoverKey(coverKey);
            coverUploadUrl = s3Service.generatePresignedUploadUrl(coverKey);
        }

        articleRepository.save(article);

        String pdfUploadUrl = s3Service.generatePresignedUploadUrl(pdfKey);

        ArticleUploadInstruction instruction = new ArticleUploadInstruction();
        instruction.setArticleId(article.getId());
        instruction.setPdfUploadUrl(URI.create(pdfUploadUrl));

        // Sem JsonNullable, passamos a URI direta se existir
        if (coverUploadUrl != null) {
            instruction.setCoverUploadUrl(URI.create(coverUploadUrl));
        }

        return instruction;
    }

    public ArticleView getArticleDetails(UUID articleId, String currentUserUid) {

        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found"));

        boolean boostedByMe = false;
        boolean publishedByMe = false;
        if (currentUserUid != null) {
            boostedByMe = article.getBoostedBy().stream()
                    .anyMatch(user -> user.getId().equals(currentUserUid));

            publishedByMe = article.getPublisher().getId().toString().equals(currentUserUid);
        }


        ArticleView view = new ArticleView();
        view.setId(article.getId());
        view.setTitle(article.getTitle());
        view.setAuthors(article.getAuthors());
        view.setPublicationDate(article.getPublicationDate());
        view.setWorkType(me.dudabertole.unidoc.model.WorkType.valueOf(article.getWorkType().name()));
        view.setAbstract(article.getAbstractText());
        view.setBoostCount(article.getBoostCount());
        view.setBoostedByMe(boostedByMe);
        view.setPublishedBy(article.getPublisher().getId());
        view.setPublishedByMe(publishedByMe);


        if (article.getCoverKey() != null && !article.getCoverKey().isBlank()) {
            String generatedUrl = s3Service.generatePresignedDownloadUrl(article.getCoverKey());
            if (generatedUrl != null) {
                view.setCoverUrl(URI.create(generatedUrl));
            }
        }

        return view;
    }

    public ArticleUrl getArticleFileUrl(UUID articleId) {

        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artigo não encontrado"));

        if (article.getPdfKey() == null || article.getPdfKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo PDF não encontrado para este artigo");
        }

        String generatedUrl = s3Service.generatePresignedDownloadUrl(article.getPdfKey());

        ArticleUrl articleUrlResponse = new ArticleUrl();

        if (generatedUrl != null) {
            // Sem JsonNullable
            articleUrlResponse.setPdfUrl(URI.create(generatedUrl));
        }

        return articleUrlResponse;
    }

    @Transactional
    public BoostResponse toggleBoost(UUID articleId, UUID userUid) {

        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artigo não encontrado"));

        User user = userRepository.findById(userUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));

        boolean isBoostedNow;

        if (article.getBoostedBy().contains(user)) {
            article.getBoostedBy().remove(user);
            isBoostedNow = false;
        } else {
            article.getBoostedBy().add(user);
            isBoostedNow = true;
        }

        int newTotalBoosts = article.getBoostedBy().size();

        articleRepository.save(article);

        BoostResponse response = new BoostResponse();
        response.setBoosted(isBoostedNow);
        response.setBoostCount(newTotalBoosts);

        return response;
    }

    @Transactional
    public void deleteArticle(UUID articleId, String currentUserUid) {

        // 1. Busca o artigo no banco de dados (lança 404 se não existir)
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artigo não encontrado"));

        // 2. Verifica se o usuário que fez a requisição é o dono (publisher) do artigo (lança 403 se não for)
        if (!article.getPublisher().getId().equals(currentUserUid)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não tem permissão para deletar este artigo");
        }

        // 3. Remove os arquivos atrelados no S3 (Boas práticas para não deixar lixo na nuvem)
        // Nota: Assumindo que você crie um método deleteFile(String key) no seu S3Service
        /*
        if (article.getPdfKey() != null) {
            s3Service.deleteFile(article.getPdfKey());
        }
        if (article.getCoverKey() != null) {
            s3Service.deleteFile(article.getCoverKey());
        }
        */

        // 4. Deleta o artigo do banco de dados
        // Como 'article_authors' e 'article_boosts' estão com ON DELETE CASCADE e @ManyToMany,
        // o JPA/Banco cuidará de limpar as tabelas dependentes automaticamente.
        articleRepository.delete(article);
    }
}