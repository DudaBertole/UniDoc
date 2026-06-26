package me.dudabertole.unidoc.repository;

import me.dudabertole.unidoc.entity.Article;
import me.dudabertole.unidoc.model.WorkType;
import org.springframework.data.jpa.domain.Specification;
import java.time.LocalDate;

public class ArticleSpecification {

    // Filtra por palavra-chave no título ou resumo (ignorando maiúsculas/minúsculas)
    public static Specification<Article> containsKeyword(String query) {
        return (root, queryObj, criteriaBuilder) -> {
            if (query == null || query.isBlank()) return criteriaBuilder.conjunction();
            String likePattern = "%" + query.toLowerCase() + "%";
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), likePattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("abstractText")), likePattern) // ajuste para o nome do seu campo na entidade
            );
        };
    }

    // Filtra por ano mínimo de publicação
    public static Specification<Article> publishedAfterYear(Integer minYear) {
        return (root, queryObj, criteriaBuilder) -> {
            if (minYear == null || minYear <= 0) return criteriaBuilder.conjunction();
            LocalDate startOfYear = LocalDate.of(minYear, 1, 1);
            return criteriaBuilder.greaterThanOrEqualTo(root.get("publicationDate"), startOfYear);
        };
    }

    // Filtra pelo tipo de trabalho
    public static Specification<Article> hasWorkType(WorkType workType) {
        return (root, queryObj, criteriaBuilder) -> {
            // Se o usuário não enviou o filtro no Postman, o Enum chega nulo.
            if (workType == null) {
                return criteriaBuilder.conjunction(); // Retorna 1=1 (não filtra nada)
            }

            // Se enviou, compara de forma estrita com a coluna do banco
            return criteriaBuilder.equal(root.get("workType"), workType);
        };
    }
}