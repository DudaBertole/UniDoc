package me.dudabertole.unidoc.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import me.dudabertole.unidoc.model.WorkType;

@Entity
@Table(name = "articles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Article {

    // --- TABLE: articles ---
    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "publication_date", nullable = false)
    private LocalDate publicationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", nullable = false, length = 50)
    private WorkType workType;

    @Column(name = "abstract_text", nullable = false, columnDefinition = "TEXT")
    private String abstractText;

    @Column(name = "cover_key", columnDefinition = "TEXT")
    private String coverKey;

    @Column(name = "pdf_key", columnDefinition = "TEXT")
    private String pdfKey;

    // fk_articles_owner
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by", nullable = false)
    private User publisher;

    // --- TABLE: articles_authors ---
    @ElementCollection
    @CollectionTable(
            name = "article_authors",
            // fk_authors_article
            joinColumns = @JoinColumn(name = "article_id")
    )
    @Column(name = "author_name", nullable = false)
    @Builder.Default
    private List<String> authors = new ArrayList<>();

    // --- TABLE: article_boosts ---
    @ManyToMany
    @JoinTable(
            name = "article_boosts",
            // fk_boosts_user
            joinColumns = @JoinColumn(name = "article_id"),
            // fk_boosts_article
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @Builder.Default
    private Set<User> boostedBy = new HashSet<>();
    // Using Set prevents duplicate entries in memory, pk_article_boosts
}