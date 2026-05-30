package com.newsapp.backend.repository;

import com.newsapp.backend.model.BookmarkedArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookmarkedArticleRepository extends JpaRepository<BookmarkedArticle, String> {
}
