package com.careerpilot.controller;
import com.careerpilot.repository.ApplicationRepository;
import com.careerpilot.entity.InterviewQuestion;
import com.careerpilot.entity.JobApplication;
import com.careerpilot.entity.UserAccount;
import com.careerpilot.service.CareerAiService;
import com.careerpilot.repository.UserRepository;
import com.careerpilot.repository.InterviewQuestionRepository;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class InterviewController {
    private final UserRepository users;
    private final ApplicationRepository apps;
    private final InterviewQuestionRepository questions;
    private final CareerAiService generator;

    public InterviewController(UserRepository u, ApplicationRepository a, InterviewQuestionRepository q, CareerAiService g) {
        users = u;
        apps = a;
        questions = q;
        generator = g;
    }

    public record Generate(@NotBlank String difficulty) {
    }

    private UserAccount current(Authentication a) {
        return users.findByEmailIgnoreCase(a.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private JobApplication app(Long id, Long uid) {
        return apps.findById(id).filter(x -> x.owner.getId().equals(uid)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
    }

    private Map<String, Object> view(InterviewQuestion q) {
        return Map.of("id", q.id, "applicationId", q.application.getId(), "category", q.category, "difficulty", q.difficulty, "question", q.question, "relevance", Objects.toString(q.relevance, ""), "answerStructure", Objects.toString(q.answerStructure, ""), "practiceAnswer", Objects.toString(q.practiceAnswer, ""), "selfAssessment", Objects.toString(q.selfAssessment, ""), "provider", generator.providerMode());
    }

    @PostMapping("/applications/{id}/interview-questions")
    List<Map<String, Object>> generate(Authentication auth, @PathVariable Long id, @RequestBody Generate body) {
        UserAccount u = current(auth);
        JobApplication a = app(id, u.id);
        String d = body.difficulty().toUpperCase(Locale.ROOT);
        if (!Set.of("EASY", "MEDIUM", "HARD").contains(d))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Difficulty must be EASY, MEDIUM, or HARD");
        return generator.generateQuestions(u, a, d).stream().map(questions::save).map(this::view).toList();
    }

    @GetMapping("/applications/{id}/interview-questions")
    List<Map<String, Object>> list(Authentication auth, @PathVariable Long id) {
        UserAccount u = current(auth);
        app(id, u.id);
        return questions.findAllByOwnerIdAndApplicationIdOrderByCreatedAtDesc(u.id, id).stream().map(this::view).toList();
    }

    @PatchMapping("/interview-questions/{id}/answer")
    Map<String, Object> answer(Authentication auth, @PathVariable Long id, @RequestBody Map<String, String> body) {
        UserAccount u = current(auth);
        InterviewQuestion q = questions.findById(id).filter(x -> x.owner.getId().equals(u.id)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));
        String text = Objects.toString(body.get("practiceAnswer"), "");
        String notes = Objects.toString(body.get("selfAssessment"), "");
        if (text.length() > 8000 || notes.length() > 3000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Practice response is too long");
        q.practiceAnswer = text;
        q.selfAssessment = notes;
        return view(questions.save(q));
    }
}
