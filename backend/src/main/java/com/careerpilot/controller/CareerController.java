package com.careerpilot.controller;
import com.careerpilot.repository.ApplicationRepository;
import com.careerpilot.entity.JobApplication;
import com.careerpilot.entity.UserAccount;
import com.careerpilot.service.CareerAiService;
import com.careerpilot.repository.AnalysisRepository;
import com.careerpilot.repository.ApplicationStatusHistoryRepository;
import com.careerpilot.entity.ApplicationStatusHistory;
import com.careerpilot.entity.JobAnalysis;
import com.careerpilot.repository.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class CareerController {
    private final ApplicationRepository applications;
    private final UserRepository users;
    private final AnalysisRepository analyses;
    private final ApplicationStatusHistoryRepository statusHistory;
    private final CareerAiService ai;

    public CareerController(ApplicationRepository applications, UserRepository users, AnalysisRepository analyses, ApplicationStatusHistoryRepository statusHistory, CareerAiService ai) {
        this.applications = applications;
        this.users = users;
        this.analyses = analyses;
        this.statusHistory = statusHistory;
        this.ai = ai;
    }

    public record ApplicationInput(@NotBlank @Size(max = 200) String company, @NotBlank @Size(max = 200) String role,
                                   @Size(max = 200) String location, @Size(max = 12000) String jobDescription,
                                   @Size(max = 2000) String postingUrl, @Size(max = 30) String status,
                                   @Size(max = 30) String priority, @Size(max = 100) String source) {
    }

    private UserAccount current(java.security.Principal p) {
        return users.findByEmailIgnoreCase(p.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private JobApplication owned(Long id, Long uid) {
        return applications.findById(id).filter(a -> a.owner.getId().equals(uid)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
    }

    private Map<String, Object> view(JobApplication a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.id);
        m.put("company", a.company);
        m.put("role", a.role);
        m.put("location", a.location);
        m.put("jobDescription", a.jobDescription);
        m.put("postingUrl", a.postingUrl);
        m.put("status", a.status);
        m.put("priority", a.priority);
        m.put("source", a.source);
        m.put("createdAt", a.createdAt);
        m.put("updatedAt", a.updatedAt);
        return m;
    }

    private void recordStatus(UserAccount owner, JobApplication application, String from, String to) {
        statusHistory.save(new ApplicationStatusHistory(owner, application, from, to));
    }

    private static final Set<String> STATUSES = Set.of("SAVED", "PREPARING", "APPLIED", "ASSESSMENT", "INTERVIEW", "OFFER", "REJECTED", "WITHDRAWN", "ACCEPTED");

    private void set(JobApplication a, ApplicationInput in) {
        String status = in.status() == null || in.status().isBlank() ? "SAVED" : in.status().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid application status");
        String priority = in.priority() == null || in.priority().isBlank() ? "MEDIUM" : in.priority().toUpperCase(Locale.ROOT);
        if (!Set.of("LOW", "MEDIUM", "HIGH").contains(priority))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Priority must be LOW, MEDIUM, or HIGH");
        a.company = in.company().trim();
        a.role = in.role().trim();
        a.location = in.location();
        a.jobDescription = in.jobDescription();
        a.postingUrl = in.postingUrl();
        a.status = status;
        a.priority = priority;
        a.source = in.source();
        a.updatedAt = Instant.now();
    }

    @GetMapping("/applications")
    List<Map<String, Object>> list(java.security.Principal p, @RequestParam(required = false) String q, @RequestParam(required = false) String status) {
        return applications.findAllByOwnerIdOrderByUpdatedAtDesc(current(p).id).stream().filter(a -> q == null || (a.company + " " + a.role + " " + Objects.toString(a.location, "")).toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))).filter(a -> status == null || a.status.equalsIgnoreCase(status)).map(this::view).toList();
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Map<String, Object> create(java.security.Principal p, @Valid @RequestBody ApplicationInput in) {
        UserAccount owner = current(p);
        JobApplication a = new JobApplication(owner, in.company().trim(), in.role().trim(), in.location(), in.jobDescription(), in.postingUrl(), "SAVED", "MEDIUM", in.source());
        set(a, in);
        a = applications.save(a);
        recordStatus(owner, a, null, a.status);
        return view(a);
    }

    @GetMapping("/applications/{id}")
    Map<String, Object> get(java.security.Principal p, @PathVariable Long id) {
        return view(owned(id, current(p).id));
    }

    @PutMapping("/applications/{id}")
    @Transactional
    Map<String, Object> update(java.security.Principal p, @PathVariable Long id, @Valid @RequestBody ApplicationInput in) {
        UserAccount owner = current(p);
        JobApplication a = owned(id, owner.id);
        String previousStatus = a.status;
        set(a, in);
        a = applications.save(a);
        if (!Objects.equals(previousStatus, a.status)) recordStatus(owner, a, previousStatus, a.status);
        return view(a);
    }

    @PatchMapping("/applications/{id}/status")
    @Transactional
    Map<String, Object> status(java.security.Principal p, @PathVariable Long id, @RequestBody Map<String, String> body) {
        UserAccount owner = current(p);
        JobApplication a = owned(id, owner.id);
        String previousStatus = a.status;
        String s = Objects.toString(body.get("status"), "").toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(s))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid application status");
        a.status = s;
        a.updatedAt = Instant.now();
        a = applications.save(a);
        if (!Objects.equals(previousStatus, s)) recordStatus(owner, a, previousStatus, s);
        return view(a);
    }

    @GetMapping("/applications/{id}/status-history")
    List<Map<String, Object>> statusHistoryList(java.security.Principal p, @PathVariable Long id) {
        UserAccount owner = current(p);
        owned(id, owner.id);
        return statusHistory.findHistory(owner.id, id).stream()
                .map(event -> Map.<String, Object>of("fromStatus", Objects.toString(event.fromStatus, ""), "toStatus", event.toStatus, "changedAt", event.changedAt))
                .toList();
    }

    @DeleteMapping("/applications/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(java.security.Principal p, @PathVariable Long id) {
        applications.delete(owned(id, current(p).id));
    }

    @GetMapping("/dashboard/summary")
    Map<String, Object> summary(java.security.Principal p) {
        Long id = current(p).id;
        List<JobApplication> all = applications.findAllByOwnerIdOrderByUpdatedAtDesc(id);
        Map<String, Long> distribution = all.stream().collect(Collectors.groupingBy(a -> a.status, Collectors.counting()));
        long total = all.size(), interview = distribution.getOrDefault("INTERVIEW", 0L), offer = distribution.getOrDefault("OFFER", 0L);
        return Map.of("totalApplications", total, "inProgress", all.stream().filter(a -> Set.of("APPLIED", "ASSESSMENT", "INTERVIEW").contains(a.status)).count(), "interviews", interview, "offers", offer, "statusDistribution", distribution, "interviewConversion", total == 0 ? 0 : Math.round(interview * 100.0 / total), "offerConversion", total == 0 ? 0 : Math.round(offer * 100.0 / total));
    }

    @GetMapping("/analytics/applications")
    Map<String, Object> analytics(java.security.Principal p, @RequestParam(defaultValue = "all") String range) {
        Instant now = Instant.now();
        Instant from = switch (range) {
            case "7d" -> now.minusSeconds(7 * 86400L);
            case "30d" -> now.minusSeconds(30 * 86400L);
            case "90d" -> now.minusSeconds(90 * 86400L);
            case "all" -> Instant.EPOCH;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Range must be 7d, 30d, 90d, or all");
        };
        Long ownerId = current(p).id;
        List<JobApplication> all = applications.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId);
        List<JobApplication> filtered = all.stream().filter(a -> a.createdAt != null && !a.createdAt.isBefore(from)).toList();
        List<ApplicationStatusHistory> rangedEvents = statusHistory.findSince(ownerId, from);
        Map<String, Long> statuses = filtered.stream().collect(Collectors.groupingBy(a -> a.status, Collectors.counting()));
        Map<String, Long> sources = filtered.stream().filter(a -> a.source != null && !a.source.isBlank()).collect(Collectors.groupingBy(a -> a.source, Collectors.counting()));
        boolean shortRange = !range.equals("all");
        Map<String, Long> buckets = filtered.stream().collect(Collectors.groupingBy(a -> {
            java.time.LocalDate d = a.createdAt.atZone(java.time.ZoneOffset.UTC).toLocalDate();
            return shortRange ? d.toString() : d.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toString();
        }, TreeMap::new, Collectors.counting()));
        Map<String, Long> transitionStatuses = rangedEvents.stream().collect(Collectors.groupingBy(e -> e.toStatus, TreeMap::new, Collectors.counting()));
        Map<String, Long> transitionBuckets = rangedEvents.stream().collect(Collectors.groupingBy(e -> {
            java.time.LocalDate d = e.changedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate();
            return shortRange ? d.toString() : d.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toString();
        }, TreeMap::new, Collectors.counting()));
        Set<Long> cohortIds = filtered.stream().map(a -> a.id).collect(Collectors.toSet());
        List<ApplicationStatusHistory> cohortEvents = cohortIds.isEmpty() ? List.of() : statusHistory.findForApplications(ownerId, new ArrayList<>(cohortIds));
        Map<Long, ApplicationStatusHistory> firstInterview = new LinkedHashMap<>();
        for (ApplicationStatusHistory event : cohortEvents) {
            if (cohortIds.contains(event.application.id) && event.toStatus.equals("INTERVIEW") && !event.toStatus.equals(event.fromStatus))
                firstInterview.putIfAbsent(event.application.id, event);
        }
        double avgDaysToInterview = firstInterview.values().stream()
                .mapToLong(event -> Math.max(0, java.time.Duration.between(event.application.createdAt, event.changedAt).toDays()))
                .average().orElse(-1);
        long progressed = filtered.stream().filter(a -> Set.of("INTERVIEW", "OFFER", "ACCEPTED").contains(a.status)).count();
        long resolved = filtered.stream().filter(a -> Set.of("OFFER", "ACCEPTED", "REJECTED", "WITHDRAWN").contains(a.status)).count();
        long positive = filtered.stream().filter(a -> Set.of("OFFER", "ACCEPTED").contains(a.status)).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("range", range);
        out.put("total", filtered.size());
        out.put("statusDistribution", statuses);
        out.put("sourceDistribution", sources);
        out.put("timeline", buckets.entrySet().stream().map(e -> Map.of("date", e.getKey(), "count", e.getValue())).toList());
        out.put("transitionDistribution", transitionStatuses);
        out.put("transitionTimeline", transitionBuckets.entrySet().stream().map(e -> Map.of("date", e.getKey(), "count", e.getValue())).toList());
        out.put("averageDaysToInterview", avgDaysToInterview < 0 ? null : Math.round(avgDaysToInterview * 10.0) / 10.0);
        out.put("progressed", progressed);
        out.put("resolved", resolved);
        out.put("positiveOutcomes", positive);
        out.put("interviewProgressionRate", filtered.isEmpty() ? 0 : Math.round(progressed * 100.0 / filtered.size()));
        out.put("positiveOutcomeRate", resolved == 0 ? 0 : Math.round(positive * 100.0 / resolved));
        out.put("smallSample", filtered.size() < 10);
        return out;
    }

    @GetMapping("/ai/status")
    Map<String, Object> aiStatus(java.security.Principal p) {
        current(p);
        return Map.of("configured", ai.providerConfigured(), "provider", ai.providerMode());
    }

    @GetMapping("/ai/career-insights")
    Map<String, Object> careerInsights(java.security.Principal p, @RequestParam(defaultValue = "30d") String range) {
        Map<String, Object> stats = analytics(p, range);
        Map<String, Object> metrics = Map.of("range", stats.get("range"), "total", stats.get("total"), "statusDistribution", stats.get("statusDistribution"), "sourceDistribution", stats.get("sourceDistribution"), "progressed", stats.get("progressed"), "resolved", stats.get("resolved"), "positiveOutcomes", stats.get("positiveOutcomes"), "interviewProgressionRate", stats.get("interviewProgressionRate"), "positiveOutcomeRate", stats.get("positiveOutcomeRate"), "smallSample", stats.get("smallSample"));
        return ai.careerInsights(metrics);
    }

    @GetMapping("/export/applications.csv")
    org.springframework.http.ResponseEntity<String> export(java.security.Principal p) {
        StringBuilder csv = new StringBuilder("Company,Role,Location,Status,Source,Created At\r\n");
        for (JobApplication a : applications.findAllByOwnerIdOrderByUpdatedAtDesc(current(p).id)) {
            csv.append(csv(a.company)).append(',').append(csv(a.role)).append(',').append(csv(a.location)).append(',').append(csv(a.status)).append(',').append(csv(a.source)).append(',').append(csv(a.createdAt.toString())).append("\r\n");
        }
        return org.springframework.http.ResponseEntity.ok().header("Content-Type", "text/csv; charset=utf-8").header("Content-Disposition", "attachment; filename=careerpilot-applications.csv").body(csv.toString());
    }

    private String csv(String value) {
        return "\"" + Objects.toString(value, "").replace("\"", "\"\"") + "\"";
    }

    @PostMapping("/applications/{id}/analyses")
    Map<String, Object> analyze(java.security.Principal p, @PathVariable Long id, @RequestBody Map<String, String> body) {
        UserAccount user = current(p);
        JobApplication app = owned(id, user.id);
        String resume = Objects.toString(body.get("resumeText"), "");
        String jd = Objects.toString(app.jobDescription, "");
        if (jd.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add a job description before analysis");
        if (resume.length() > 30000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume text is too long");
        Map<String, Object> result = new LinkedHashMap<>(ai.analyze(resume, jd));
        int score = ((Number) result.getOrDefault("score", 0)).intValue();
        JobAnalysis saved = analyses.save(new JobAnalysis(user, app, score, toJson(result)));
        result.put("id", saved.id);
        result.put("createdAt", saved.createdAt);
        return result;
    }

    @GetMapping("/applications/{id}/analyses")
    List<Map<String, Object>> history(java.security.Principal p, @PathVariable Long id) {
        UserAccount user = current(p);
        owned(id, user.id);
        return analyses.findAllByOwnerIdAndApplicationIdOrderByCreatedAtDesc(user.id, id).stream().map(a -> Map.<String, Object>of("id", a.id, "score", a.score, "result", a.resultJson, "createdAt", a.createdAt)).toList();
    }

    private String toJson(Map<String, Object> result) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result);
        } catch (Exception e) {
            throw new IllegalStateException("Could not save analysis");
        }
    }
}

