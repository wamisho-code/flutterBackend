package com.newsapp.backend.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/notifications")
@Slf4j
public class NotificationController {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private int mockIndex = 0;

    private final String[][] mockNews = {
        {"Breaking: Tech Alert", "New Artificial Intelligence model beats human benchmark in math reasoning."},
        {"Breaking: Business Alert", "Global markets surge as inflation rates fall below expectations."},
        {"Breaking: Health Alert", "Researchers discover a breakthrough treatment for common allergies."},
        {"Breaking: Science Alert", "NASA's telescope captures stunning new details of a distant habitable planet."},
        {"Breaking: Sport Alert", "Underdog team clinches championship victory in dramatic final match."}
    };

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications() {
        // Create an emitter that stays alive for a long time (30 minutes)
        SseEmitter emitter = new SseEmitter(1800000L);
        this.emitters.add(emitter);

        emitter.onCompletion(() -> {
            log.info("SSE emitter completed");
            this.emitters.remove(emitter);
        });

        emitter.onTimeout(() -> {
            log.info("SSE emitter timed out");
            this.emitters.remove(emitter);
        });

        emitter.onError((e) -> {
            log.info("SSE emitter error: {}", e.getMessage());
            this.emitters.remove(emitter);
        });

        // Send an initial handshake event
        try {
            Map<String, String> handshake = new HashMap<>();
            handshake.put("title", "Connected");
            handshake.put("message", "You are now connected to real-time news alerts!");
            handshake.put("timestamp", String.valueOf(System.currentTimeMillis()));
            emitter.send(SseEmitter.event().name("handshake").data(handshake));
        } catch (IOException e) {
            this.emitters.remove(emitter);
        }

        return emitter;
    }

    // @Scheduled(fixedRate = 45000)
    public void broadcastMockNews() {
        if (emitters.isEmpty()) {
            return;
        }

        String[] news = mockNews[mockIndex];
        mockIndex = (mockIndex + 1) % mockNews.length;

        Map<String, String> notification = new HashMap<>();
        notification.put("title", news[0]);
        notification.put("message", news[1]);
        notification.put("timestamp", String.valueOf(System.currentTimeMillis()));

        log.info("Broadcasting real-time news alert: {}", news[0]);
        List<SseEmitter> deadEmitters = new ArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(notification));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }

        emitters.removeAll(deadEmitters);
    }
}
