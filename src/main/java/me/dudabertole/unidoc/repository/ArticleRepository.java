package me.dudabertole.unidoc.repository;

import me.dudabertole.unidoc.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ArticleRepository extends JpaRepository<Article, UUID>, JpaSpecificationExecutor<Article> {
    // Conta os artigos cujo "publisher.id" seja igual ao UID fornecido
    int countByPublisherId(UUID publisherId);

    // Faz um JOIN na coleção @ManyToMany 'boostedBy' e conta o total de registros
    // para todos os artigos de um determinado publicador.
    @Query("SELECT COALESCE(COUNT(b), 0) FROM Article a JOIN a.boostedBy b WHERE a.publisher.id = :publisherId")
    int countTotalBoostsByPublisherId(@Param("publisherId") UUID publisherId);
}
