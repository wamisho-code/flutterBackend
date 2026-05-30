package com.newsapp.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "bookmarked_articles")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class BookmarkedArticle {
    @Id
    @EqualsAndHashCode.Include
    @Column(length = 512)
    private String id; // Guardian article URL/id (max ~200 chars)

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String category;

    private String author;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime publishedAt;

    private Integer readTimeMinutes;

    @ManyToMany(mappedBy = "bookmarks", fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private Set<User> users = new HashSet<>();
}
