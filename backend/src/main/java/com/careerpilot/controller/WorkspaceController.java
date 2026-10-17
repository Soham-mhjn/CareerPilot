package com.careerpilot.controller;
import com.careerpilot.entity.UserAccount;
import com.careerpilot.repository.UserRepository;
import com.careerpilot.repository.WorkspaceItemRepository;
import com.careerpilot.entity.WorkspaceItem;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/workspace/items")
public class WorkspaceController {
    private final WorkspaceItemRepository items;
    private final UserRepository users;

    public WorkspaceController(WorkspaceItemRepository items, UserRepository users) {
        this.items = items;
        this.users = users;
    }

    public record Input(@NotBlank String type, @NotBlank @Size(max = 255) String title, @Size(max = 5000) String notes,
                        @Size(max = 255) String company, Instant startsAt, @Min(1) @Max(1440) Integer durationMinutes,
                        @Size(max = 2000) String meetingUrl) {
    }

    private UserAccount current(Authentication a) {
        return users.findByEmailIgnoreCase(a.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private WorkspaceItem owned(Long id, Long uid) {
        return items.findById(id).filter(i -> i.owner.getId().equals(uid)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
    }

    private Map<String, Object> view(WorkspaceItem i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.id);
        m.put("type", i.type);
        m.put("title", i.title);
        m.put("notes", i.notes);
        m.put("company", i.company);
        m.put("startsAt", i.startsAt);
        m.put("durationMinutes", i.durationMinutes);
        m.put("meetingUrl", i.meetingUrl);
        m.put("completed", i.completed);
        return m;
    }

    @GetMapping
    List<Map<String, Object>> list(Authentication a, @RequestParam(required = false) String type) {
        return items.findAllByOwnerIdOrderByStartsAtAsc(current(a).id).stream().filter(i -> type == null || i.type.equalsIgnoreCase(type)).map(this::view).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> create(Authentication a, @Valid @RequestBody Input in) {
        String type = in.type().toUpperCase(Locale.ROOT);
        if (!Set.of("INTERVIEW", "TASK", "DEADLINE", "QUESTION").contains(type))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid workspace item type");
        WorkspaceItem item = new WorkspaceItem(current(a), type, in.title().trim(), in.notes(), in.company(), in.startsAt(), in.durationMinutes(), in.meetingUrl());
        return view(items.save(item));
    }

    @PutMapping("/{id}")
    Map<String, Object> update(Authentication a, @PathVariable Long id, @Valid @RequestBody Input in) {
        WorkspaceItem i = owned(id, current(a).id);
        String type = in.type().toUpperCase(Locale.ROOT);
        if (!Set.of("INTERVIEW", "TASK", "DEADLINE", "QUESTION").contains(type))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid workspace item type");
        i.type = type;
        i.title = in.title().trim();
        i.notes = in.notes();
        i.company = in.company();
        i.startsAt = in.startsAt();
        i.durationMinutes = in.durationMinutes();
        i.meetingUrl = in.meetingUrl();
        return view(items.save(i));
    }

    @PatchMapping("/{id}/complete")
    Map<String, Object> complete(Authentication a, @PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        WorkspaceItem i = owned(id, current(a).id);
        i.completed = Boolean.TRUE.equals(body.get("completed"));
        return view(items.save(i));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Authentication a, @PathVariable Long id) {
        items.delete(owned(id, current(a).id));
    }
}

