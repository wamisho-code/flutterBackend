package com.newsapp.backend.controller;

import com.newsapp.backend.security.SecurityUtils;
import com.newsapp.backend.security.UserPrincipal;
import com.newsapp.backend.service.NewsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.Data;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    @Autowired
    private NewsService newsService;

    @GetMapping("/trending")
    public ResponseEntity<List<Map<String, Object>>> getTrending(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) List<String> excludeIds) {
        return ResponseEntity.ok(newsService.getTrending(page, pageSize, excludeIds));
    }

    @GetMapping("/recommended")
    public ResponseEntity<List<Map<String, Object>>> getRecommended(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int pageSize,
            @RequestParam(required = false) List<String> excludeIds) {
        UserPrincipal userPrincipal = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(newsService.getRecommended(userPrincipal, latitude, longitude, page, pageSize, excludeIds));
    }

    @PostMapping("/recommended/feed")
    public ResponseEntity<List<Map<String, Object>>> getRecommendedFeed(
            @RequestBody(required = false) RecommendedFeedRequest request) {
        UserPrincipal userPrincipal = SecurityUtils.getCurrentUser();
        RecommendedFeedRequest safe = request == null ? new RecommendedFeedRequest() : request;
        int page = safe.getPage() == null ? 1 : safe.getPage();
        int pageSize = safe.getPageSize() == null ? 15 : safe.getPageSize();
        return ResponseEntity.ok(
                newsService.getRecommended(
                        userPrincipal,
                        safe.getLatitude(),
                        safe.getLongitude(),
                        page,
                        pageSize,
                        safe.getExcludeIds()
                )
        );
    }

    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchNews(
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) List<String> excludeIds) {
        if (q == null || q.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(newsService.searchNews(q, page, pageSize, excludeIds));
    }

    @GetMapping("/category")
    public ResponseEntity<List<Map<String, Object>>> getByCategory(
            @RequestParam String name,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int pageSize,
            @RequestParam(required = false) List<String> excludeIds) {
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(newsService.getByCategory(name, page, pageSize, excludeIds));
    }

    @Data
    public static class RecommendedFeedRequest {
        private Double latitude;
        private Double longitude;
        private Integer page;
        private Integer pageSize;
        private List<String> excludeIds;
    }
}
