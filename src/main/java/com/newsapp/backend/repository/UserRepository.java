package com.newsapp.backend.repository;

import com.newsapp.backend.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = {"bookmarks"})
    Optional<User> findWithBookmarksByEmail(String email);

    Boolean existsByEmail(String email);
}
