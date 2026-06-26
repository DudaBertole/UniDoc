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
import java.time.LocalDate;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;
    private UserRepository userRepository;
    private S3Service s3Service;

    public ArticleService(ArticleRepository articleRepository, UserRepository userRepository, S3Service s3Service ) {
        this.articleRepository = articleRepository;
        this.userRepository = userRepository;
        this.s3Service = s3Service;
    }

    public PaginatedArticlePreview searchArticles(String query, Integer minYear, WorkType workType,
                                                  Integer page, Integer size, String sortParam) {

        // 1. Mapeia a string do Swagger (sortParam) para o Sort do Spring
        Sort sort = getSortDirection(sortParam);

        // 2. Cria o objeto de paginação
        Pageable pageable = PageRequest.of(page, size, sort);

        // 3. Monta os filtros dinâmicos
        Specification<Article> spec = Specification.where(ArticleSpecification.containsKeyword(query))
                .and(ArticleSpecification.publishedAfterYear(minYear))
                .and(ArticleSpecification.hasWorkType(workType));

        // 4. Vai no banco de dados e busca SÓ a página solicitada
        Page<Article> articlePage = articleRepository.findAll(spec, pageable);

        // 5. Converte a resposta do banco (Page<Article>) para o DTO do Swagger (PaginatedArticlePreview)
        return mapToPaginatedResponse(articlePage);
    }

    // --- Métodos Auxiliares Privados ---

    private Sort getSortDirection(String sortParam) {
        if (sortParam == null) return Sort.by(Sort.Direction.DESC, "boostCount"); // default

        return switch (sortParam.toUpperCase()) {
            case "TITLE" -> Sort.by(Sort.Direction.ASC, "title");
            case "PUBLICATION_DATE" -> Sort.by(Sort.Direction.DESC, "publicationDate"); // mais novos primeiro
            case "WORK_TYPE" -> Sort.by(Sort.Direction.ASC, "workType");
            case "BOOST_COUNT" -> Sort.by(Sort.Direction.DESC, "boostCount"); // mais bombados primeiro
            default -> Sort.by(Sort.Direction.DESC, "boostCount");
        };
    }

    private PaginatedArticlePreview mapToPaginatedResponse(Page<Article> articlePage) {
        List<ArticlePreview> previews = articlePage.getContent().stream().map(article -> {
            ArticlePreview preview = new ArticlePreview();
            preview.setId(article.getId());
            preview.setTitle(article.getTitle());
            // preview.setAuthors(article.getAuthors()); // Ajuste dependendo de como salva os autores
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
    public ArticleUploadInstruction registerArticle(String firebaseUid, ArticleRegistration registration) {

        // 1. Busca o usuário que está publicando
        User publisher = userRepository.findById(firebaseUid)
                .orElseThrow(() -> new RuntimeException("Article not found"));

        // 2. Cria a entidade do Artigo e salva no banco
        Article article = new Article();
        article.setId(UUID.randomUUID());
        article.setTitle(registration.getTitle());
        article.setPublicationDate(registration.getPublicationDate());
        article.setWorkType(me.dudabertole.unidoc.model.WorkType.valueOf(registration.getWorkType().name()));
        article.setAbstractText(registration.getAbstract());
        article.setAuthors(registration.getAuthors());
        article.setPublisher(publisher);

        // 3. Define as chaves (caminhos) onde os arquivos serão salvos no S3
        String pdfKey = "articles/" + article.getId() + "/document.pdf";
        article.setPdfKey(pdfKey);

        String coverUploadUrl = null;
        if (Boolean.TRUE.equals(registration.getHasCover())) {
            String coverKey = "articles/" + article.getId() + "/cover.jpg"; // ou .png
            article.setCoverKey(coverKey);
            coverUploadUrl = s3Service.generatePresignedUploadUrl(coverKey);
        }

        // Salva as chaves no banco de dados
        articleRepository.save(article);

        // 4. Gera a URL pré-assinada do PDF
        String pdfUploadUrl = s3Service.generatePresignedUploadUrl(pdfKey);

        // 5. Monta o DTO de resposta
        ArticleUploadInstruction instruction = new ArticleUploadInstruction();
        instruction.setArticleId(article.getId());
        instruction.setPdfUploadUrl(URI.create(pdfUploadUrl));
        assert coverUploadUrl != null;
        instruction.setCoverUploadUrl(URI.create(coverUploadUrl));

        return instruction;
    }

    public ArticleView getArticleDetails(UUID articleId, String currentUserUid) {

        // 1. Busca o artigo no banco ou lança erro 404
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found"));

        // 2. Verifica se o usuário atual curtiu (boosted) o artigo
        boolean boostedByMe = false;
        if (currentUserUid != null) {
            boostedByMe = article.getBoostedBy().stream()
                    .anyMatch(user -> user.getId().equals(currentUserUid));
        }

        // 3. Gera a URL temporária da capa (se existir)
        String coverUrl = null;
        if (article.getCoverKey() != null) {
            coverUrl = s3Service.generatePresignedDownloadUrl(article.getCoverKey());
        }

        // 4. Mapeia para o DTO de visualização
        ArticleView view = new ArticleView();
        view.setId(article.getId()); // Ajuste conforme o tipo gerado pelo seu OpenAPI (Integer)
        view.setTitle(article.getTitle());
        view.setAuthors(article.getAuthors());
        view.setPublicationDate(article.getPublicationDate());
        view.setWorkType(me.dudabertole.unidoc.model.WorkType.valueOf(article.getWorkType().name()));
        view.setAbstract(article.getAbstractText());
        view.setBoostCount(article.getBoostCount());
        view.setBoostedByMe(boostedByMe);

        if (article.getCoverKey() != null && !article.getCoverKey().isBlank()) {
            String generatedUrl = s3Service.generatePresignedDownloadUrl(article.getCoverKey());

            // Só tenta converter para URI se a AWS realmente devolver uma URL válida
            if (generatedUrl != null) {
                view.setCoverUrl(java.net.URI.create(generatedUrl));
            }
        }

        return view;
    }

    public ArticleUrl getArticleFileUrl(UUID articleId) {

        // 1. Busca o artigo no banco ou lança erro 404
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artigo não encontrado"));

        // 2. Valida se o artigo realmente possui um PDF atrelado
        if (article.getPdfKey() == null || article.getPdfKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo PDF não encontrado para este artigo");
        }

        // 3. Gera a URL temporária de leitura (GET) na AWS
        String generatedUrl = s3Service.generatePresignedDownloadUrl(article.getPdfKey());

        // 4. Monta o DTO de resposta exigido pelo Swagger
        ArticleUrl articleUrlResponse = new ArticleUrl();
        if (generatedUrl != null) {
            articleUrlResponse.setPdfUrl(URI.create(generatedUrl));
        }

        return articleUrlResponse;
    }

    @Transactional
    public BoostResponse toggleBoost(UUID articleId, String userUid) {

        // 1. Busca o artigo
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artigo não encontrado"));

        // 2. Busca o usuário que está fazendo a requisição
        User user = userRepository.findById(userUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));

        // 3. Checa o estado atual e inverte (Toggle)
        boolean isBoostedNow;

        // Assumindo que sua entidade Article tem um getBoostedBy() que retorna um Set<User> ou List<User>
        if (article.getBoostedBy().contains(user)) {
            article.getBoostedBy().remove(user); // Remove o boost
            isBoostedNow = false;
        } else {
            article.getBoostedBy().add(user);    // Adiciona o boost
            isBoostedNow = true;
        }

        // Opcional: Se você tiver uma coluna física "boost_count" na tabela, atualize-a aqui
        // Se ela for apenas calculada pelo tamanho da lista, não precisa fazer nada.
        int newTotalBoosts = article.getBoostedBy().size();

        // Salva a alteração no banco
        articleRepository.save(article);

        // 4. Monta a resposta DTO
        BoostResponse response = new BoostResponse();
        response.setBoosted(isBoostedNow);
        response.setBoostCount(newTotalBoosts);

        return response;
    }
}