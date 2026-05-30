package com.newsapp.backend.controller;

import com.newsapp.backend.security.SecurityUtils;
import com.newsapp.backend.security.UserPrincipal;
import com.newsapp.backend.service.BookmarkService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookmarks")
public class BookmarkController {

    @Autowired
    private BookmarkService bookmarkService;

    @GetMapping
    public ResponseEntity<?> getBookmarks() {
        UserPrincipal userPrincipal = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(bookmarkService.getBookmarks(userPrincipal));
    }

    @PostMapping("/toggle")
    public ResponseEntity<?> toggleBookmark(@RequestBody BookmarkRequest request) {
        UserPrincipal userPrincipal = SecurityUtils.getCurrentUser();
        BookmarkService.BookmarkRequest serviceRequest = new BookmarkService.BookmarkRequest();
        serviceRequest.setId(request.getId());
        serviceRequest.setTitle(request.getTitle());
        serviceRequest.setContent(request.getContent());
        serviceRequest.setCategory(request.getCategory());
        serviceRequest.setAuthor(request.getAuthor());
        serviceRequest.setImageUrl(request.getImageUrl());
        serviceRequest.setPublishedAt(request.getPublishedAt());
        serviceRequest.setReadTimeMinutes(request.getReadTimeMinutes());
        return ResponseEntity.ok(bookmarkService.toggleBookmark(userPrincipal, serviceRequest));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookmarkRequest {
        private String id;
        private String title;
        private String content;
        private String category;
        private String author;
        private String imageUrl;
        private String publishedAt;
        private Integer readTimeMinutes;
    }
}
