package com.newsapp.backend.service;

import com.newsapp.backend.model.BookmarkedArticle;
import com.newsapp.backend.model.User;
import com.newsapp.backend.repository.UserRepository;
import com.newsapp.backend.security.UserPrincipal;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

@Service
@Slf4j
public class NewsService {

    @Value("${guardian.api.key}")
    private String apiKey;

    @Value("${guardian.api.base-url}")
    private String baseUrl;

    @Value("${guardian.api.show-fields}")
    private String showFields;

    @Autowired
    private UserRepository userRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<Map<String, Object>> getTrending(int page, int pageSize, List<String> excludeIds) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/search")
                .queryParam("show-fields", showFields)
                .queryParam("order-by", "newest")
                .queryParam("page-size", pageSize)
                .queryParam("page", Math.max(1, page))
                .queryParam("api-key", apiKey)
                .toUriString();
        return filterExcluded(fetchAndTransform(url, "General"), excludeIds);
    }

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "aren't", "as", "at",
        "be", "because", "been", "before", "being", "below", "between", "both", "but", "by", "can't", "cannot", "could",
        "couldn't", "did", "didn't", "do", "does", "doesn't", "doing", "don't", "down", "during", "each", "few", "for",
        "from", "further", "had", "hadn't", "has", "hasn't", "have", "haven't", "having", "he", "he'd", "he'll", "he's",
        "her", "here", "here's", "hers", "herself", "him", "himself", "his", "how", "how's", "i", "i'd", "i'll", "i'm",
        "i've", "if", "in", "into", "is", "isn't", "it", "it's", "its", "itself", "let's", "me", "more", "most", "mustn't",
        "my", "myself", "no", "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours",
        "ourselves", "out", "over", "own", "same", "shan't", "she", "she'd", "she'll", "she's", "should", "shouldn't",
        "so", "some", "such", "than", "that", "that's", "the", "their", "theirs", "them", "themselves", "then", "there",
        "there's", "these", "they", "they'd", "they'll", "they're", "they've", "this", "those", "through", "to", "too",
        "under", "until", "up", "very", "was", "wasn't", "we", "we'd", "we'll", "we're", "we've", "were", "weren't",
        "what", "what's", "when", "when's", "where", "where's", "which", "while", "who", "who's", "whom", "why", "why's",
        "with", "won't", "would", "wouldn't", "you", "you'd", "you'll", "you're", "you've", "your", "yours", "yourself",
        "yourselves"
    ));

    private Set<String> getUserKeywords(Set<BookmarkedArticle> bookmarks) {
        Map<String, Integer> wordCounts = new HashMap<>();
        for (BookmarkedArticle article : bookmarks) {
            String text = ((article.getTitle() != null ? article.getTitle() : "") + " " + (article.getContent() != null ? article.getContent() : ""))
                    .toLowerCase()
                    .replaceAll("[^a-zA-Z0-9\\s]", "");
            String[] words = text.split("\\s+");
            for (String word : words) {
                if (word.length() > 2 && !STOP_WORDS.contains(word)) {
                    wordCounts.put(word, wordCounts.getOrDefault(word, 0) + 1);
                }
            }
        }
        List<Map.Entry<String, Integer>> list = new ArrayList<>(wordCounts.entrySet());
        list.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        Set<String> keywords = new HashSet<>();
        for (int i = 0; i < Math.min(15, list.size()); i++) {
            keywords.add(list.get(i).getKey());
        }
        return keywords;
    }

    private int calculateRecommendationScore(Map<String, Object> article, Set<String> keywords, Set<String> categories, String countryCode, String countryName) {
        int score = 0;
        String title = (String) article.get("title");
        String content = (String) article.get("content");
        String category = (String) article.get("category");

        String combinedText = ((title != null ? title : "") + " " + (content != null ? content : "")).toLowerCase();

        // 1. Location match
        if (isLocalMatch(article, countryCode, countryName)) {
            score += 50; 
        }

        // 2. Category match
        if (category != null && categories.contains(category.trim().toLowerCase())) {
            score += 30;
        }

        // 3. Keyword match (Content Similarity)
        if (keywords != null && !keywords.isEmpty()) {
            int keywordMatches = 0;
            for (String keyword : keywords) {
                if (combinedText.contains(keyword)) {
                    keywordMatches++;
                }
            }
            score += (keywordMatches * 8); 
        }

        // 4. Freshness boost
        String publishedAt = (String) article.get("publishedAt");
        if (publishedAt != null) {
            try {
                java.time.ZonedDateTime pubTime = java.time.ZonedDateTime.parse(publishedAt);
                java.time.Duration age = java.time.Duration.between(pubTime, java.time.ZonedDateTime.now());
                if (age.toHours() < 24) {
                    score += 15; 
                } else if (age.toHours() < 48) {
                    score += 5;
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        return score;
    }

    private boolean isLocalMatch(Map<String, Object> article, String countryCode, String countryName) {
        String category = (String) article.get("category");
        String title = (String) article.get("title");
        String content = (String) article.get("content");
        
        if (category != null && category.startsWith("Local")) {
            return true;
        }

        String text = ((title != null ? title : "") + " " + (content != null ? content : "")).toLowerCase();
        if (countryName != null && text.contains(countryName.toLowerCase())) {
            return true;
        }
        if (countryCode != null && text.contains(" " + countryCode.toLowerCase() + " ")) {
            return true;
        }

        return false;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRecommended(UserPrincipal userPrincipal, Double latitude, Double longitude, int page, int pageSize, List<String> excludeIds) {
        List<Map<String, Object>> candidatePool = new ArrayList<>();
        Set<String> bookmarkedCategories = new HashSet<>();
        Set<String> userKeywords = new HashSet<>();
        String userCountryCode = null;
        String userCountryName = null;
        boolean isOldUser = false;

        // 1. Extract User Profile (Activity)
        if (userPrincipal != null) {
            Optional<User> userOpt = userRepository.findWithBookmarksByEmail(userPrincipal.getEmail());
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                Set<BookmarkedArticle> bookmarks = user.getBookmarks();
                if (bookmarks != null && !bookmarks.isEmpty()) {
                    isOldUser = true;
                    for (BookmarkedArticle article : bookmarks) {
                        if (article.getCategory() != null && !article.getCategory().trim().isEmpty()) {
                            bookmarkedCategories.add(article.getCategory().trim().toLowerCase());
                        }
                    }
                    userKeywords = getUserKeywords(bookmarks);
                }
            }
        }

        // 2. Resolve Location
        if (latitude != null && longitude != null) {
            Map<String, String> locInfo = reverseGeocode(latitude, longitude);
            if (locInfo != null) {
                userCountryCode = locInfo.get("code");
                userCountryName = locInfo.get("name");
            }
        }

        // 3. Fetch Candidates
        // Candidate Set A: General Trending News
        try {
            candidatePool.addAll(getTrending(page, pageSize, excludeIds));
        } catch (Exception e) {
            log.warn("Failed to fetch trending candidates", e);
        }

        // Candidate Set B: Location-based News
        List<Map<String, Object>> localCandidates = new ArrayList<>();
        if (userCountryCode != null) {
            try {
                String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/search")
                        .queryParam("q", userCountryCode)
                        .queryParam("show-fields", showFields)
                        .queryParam("order-by", "newest")
                        .queryParam("page-size", pageSize)
                        .queryParam("page", Math.max(1, page))
                        .queryParam("api-key", apiKey)
                        .toUriString();
                localCandidates = fetchAndTransform(url, "Local (" + userCountryCode.toUpperCase() + ")");
                if (localCandidates == null || localCandidates.isEmpty()) {
                    if (userCountryName != null) {
                        String fallbackUrl = UriComponentsBuilder.fromHttpUrl(baseUrl + "/search")
                                .queryParam("q", userCountryName)
                                .queryParam("show-fields", showFields)
                                .queryParam("order-by", "newest")
                                .queryParam("page-size", pageSize)
                                .queryParam("page", Math.max(1, page))
                                .queryParam("api-key", apiKey)
                                .toUriString();
                        localCandidates = fetchAndTransform(fallbackUrl, "Local (" + userCountryName + ")");
                    }
                }
                if (localCandidates != null) {
                    candidatePool.addAll(localCandidates);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch location candidates", e);
            }
        }

        // Candidate Set C: Activity/Category-based News
        if (isOldUser && !bookmarkedCategories.isEmpty()) {
            try {
                String catQuery = String.join(" OR ", bookmarkedCategories);
                String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/search")
                        .queryParam("q", catQuery)
                        .queryParam("show-fields", showFields)
                        .queryParam("order-by", "relevance")
                        .queryParam("page-size", pageSize)
                        .queryParam("page", Math.max(1, page))
                        .queryParam("api-key", apiKey)
                        .toUriString();
                List<Map<String, Object>> categoryNews = fetchAndTransform(url, "Recommended");
                if (categoryNews != null) {
                    candidatePool.addAll(categoryNews);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch category candidates", e);
            }
        } else {
            // New user without bookmarks - fetch some curated topics to add variety
            try {
                String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/search")
                        .queryParam("q", "world OR technology OR business OR science")
                        .queryParam("show-fields", showFields)
                        .queryParam("order-by", "relevance")
                        .queryParam("page-size", pageSize)
                        .queryParam("page", Math.max(1, page))
                        .queryParam("api-key", apiKey)
                        .toUriString();
                List<Map<String, Object>> starterNews = fetchAndTransform(url, "Curated Starter");
                if (starterNews != null) {
                    candidatePool.addAll(starterNews);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch starter candidates", e);
            }
        }

        // De-duplicate candidate pool by article ID (URL)
        Map<String, Map<String, Object>> uniqueCandidates = new LinkedHashMap<>();
        for (Map<String, Object> article : candidatePool) {
            String id = (String) article.get("id");
            if (id != null) {
                uniqueCandidates.put(id, article);
            }
        }

        // If the pool is still empty, return empty to force client refresh/retry.
        if (uniqueCandidates.isEmpty()) {
            log.warn("All candidate sources returned empty. Returning no results.");
            return Collections.emptyList();
        }

        // 4. AI Recommendation / Ranking Algorithm (Content-based Filtering)
        List<Map<String, Object>> rankedArticles = new ArrayList<>(uniqueCandidates.values());
        
        final Set<String> finalKeywords = userKeywords;
        final Set<String> finalCategories = bookmarkedCategories;
        final String finalCountryCode = userCountryCode;
        final String finalCountryName = userCountryName;
        final boolean finalIsOldUser = isOldUser;

        rankedArticles.sort((a, b) -> {
            int scoreA = calculateRecommendationScore(a, finalKeywords, finalCategories, finalCountryCode, finalCountryName);
            int scoreB = calculateRecommendationScore(b, finalKeywords, finalCategories, finalCountryCode, finalCountryName);
            return Integer.compare(scoreB, scoreA); // Descending order
        });

        // 5. Update categories for UI presentation and return top 15
        List<Map<String, Object>> finalSelection = new ArrayList<>();
        int count = Math.min(pageSize, rankedArticles.size());
        for (int i = 0; i < count; i++) {
            Map<String, Object> art = rankedArticles.get(i);
            
            int score = calculateRecommendationScore(art, finalKeywords, finalCategories, finalCountryCode, finalCountryName);
            if (score > 40 && finalCountryName != null && isLocalMatch(art, finalCountryCode, finalCountryName)) {
                art.put("category", "Local (" + finalCountryName + ")");
            } else if (score > 20 && finalIsOldUser) {
                art.put("category", "Recommended");
            } else {
                art.put("category", "Trending");
            }
            finalSelection.add(art);
        }

        return filterExcluded(finalSelection, excludeIds);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> reverseGeocode(Double lat, Double lon) {
        try {
            String nominatimUrl = String.format("https://nominatim.openstreetmap.org/reverse?lat=%f&lon=%f&format=json", lat, lon);
            
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("User-Agent", "DailyFeedApp/1.0 (contact: final-year-project-developer@newsapp.com)");
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
            
            org.springframework.http.ResponseEntity<Map> response = restTemplate.exchange(
                    nominatimUrl,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Map<String, Object> address = (Map<String, Object>) body.get("address");
                if (address != null) {
                    String countryCode = (String) address.get("country_code");
                    String countryName = (String) address.get("country");
                    if (countryCode != null && !countryCode.trim().isEmpty()) {
                        log.info("Reverse geocoded location: CountryCode={}, CountryName={}", countryCode, countryName);
                        Map<String, String> result = new HashMap<>();
                        result.put("code", countryCode);
                        result.put("name", countryName);
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to reverse geocode lat={}, lon={}", lat, lon, e);
        }
        return null;
    }

    public List<Map<String, Object>> searchNews(String query, int page, int pageSize, List<String> excludeIds) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/search")
                .queryParam("q", query)
                .queryParam("show-fields", showFields)
                .queryParam("order-by", "newest")
                .queryParam("page-size", pageSize)
                .queryParam("page", Math.max(1, page))
                .queryParam("api-key", apiKey)
                .toUriString();
        return filterExcluded(fetchAndTransform(url, "General"), excludeIds);
    }

    public List<Map<String, Object>> getByCategory(String category, int page, int pageSize, List<String> excludeIds) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/search")
                .queryParam("section", category.toLowerCase())
                .queryParam("show-fields", showFields)
                .queryParam("order-by", "newest")
                .queryParam("page-size", pageSize)
                .queryParam("page", Math.max(1, page))
                .queryParam("api-key", apiKey)
                .toUriString();
        return filterExcluded(fetchAndTransform(url, category), excludeIds);
    }

    private List<Map<String, Object>> filterExcluded(List<Map<String, Object>> source, List<String> excludeIds) {
        if (excludeIds == null || excludeIds.isEmpty()) {
            return source;
        }
        Set<String> excluded = new HashSet<>(excludeIds);
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> item : source) {
            Object idObj = item.get("id");
            String id = idObj == null ? null : idObj.toString();
            if (id != null && !excluded.contains(id)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchAndTransform(String url, String category) {
        try {
            log.info("Fetching news from URL: {}", url.replaceAll("api-key=[^&]+", "api-key=***"));
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("User-Agent", "DailyFeedApp/1.0 (contact: final-year-project-developer@newsapp.com)");
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
            
            org.springframework.http.ResponseEntity<Map> responseEntity = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    Map.class
            );
            
            Map<String, Object> response = responseEntity.getBody();
            if (response == null || !(response.get("response") instanceof Map)) {
                log.warn("Guardian API response shape invalid: {}", response);
                return Collections.emptyList();
            }

            Map<String, Object> responseObj = (Map<String, Object>) response.get("response");
            List<Map<String, Object>> articles = (List<Map<String, Object>>) responseObj.get("results");
            if (articles == null || articles.isEmpty()) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> results = new ArrayList<>();
            for (Map<String, Object> art : articles) {
                Map<String, Object> fields = art.get("fields") instanceof Map
                        ? (Map<String, Object>) art.get("fields")
                        : Collections.emptyMap();

                String title = fields.get("headline") != null
                        ? fields.get("headline").toString()
                        : (art.get("webTitle") != null ? art.get("webTitle").toString() : null);
                if (title == null || title.trim().isEmpty()) {
                    continue;
                }

                Map<String, Object> transformed = new HashMap<>();
                
                String articleUrl = art.get("webUrl") != null ? art.get("webUrl").toString() : null;
                String articleId = articleUrl != null ? articleUrl : Objects.toString(art.get("id"), UUID.randomUUID().toString());
                transformed.put("id", articleId);
                transformed.put("title", title);
                
                String bodyText = fields.get("bodyText") != null ? fields.get("bodyText").toString() : null;
                String trailText = fields.get("trailText") != null ? fields.get("trailText").toString() : null;
                String content = bodyText != null && !bodyText.isBlank()
                        ? bodyText
                        : (trailText != null && !trailText.isBlank() ? trailText : "No content available.");
                transformed.put("content", content);

                String sectionName = art.get("sectionName") != null ? art.get("sectionName").toString() : null;
                transformed.put("category", sectionName != null && !sectionName.isBlank() ? sectionName : category);
                
                String author = fields.get("byline") != null ? fields.get("byline").toString() : null;
                transformed.put("author", author != null ? author : "Unknown Author");
                
                String imageUrl = fields.get("thumbnail") != null ? fields.get("thumbnail").toString() : null;
                transformed.put("imageUrl", imageUrl != null ? imageUrl : "https://images.unsplash.com/photo-1504711434969-e33886168f5c?auto=format&fit=crop&w=800&q=80");
                
                transformed.put("publishedAt", art.get("webPublicationDate"));
                
                // Calculate dynamic read time
                int wordsCount = content.split("\\s+").length;
                int readTime = Math.max(1, wordsCount / 150); // ~150 words per minute
                transformed.put("readTimeMinutes", readTime);

                results.add(transformed);
            }
            return results;
        } catch (Exception e) {
            log.error("Failed to fetch news from Guardian API", e);
            return Collections.emptyList();
        }
    }
}
