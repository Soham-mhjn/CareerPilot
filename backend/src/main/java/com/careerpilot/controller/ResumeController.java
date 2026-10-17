package com.careerpilot.controller;
import com.careerpilot.entity.UserAccount;
import com.careerpilot.entity.ResumeDocument;
import com.careerpilot.repository.UserRepository;
import com.careerpilot.repository.ResumeRepository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/resumes")
public class ResumeController {
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private final ResumeRepository resumes;
    private final UserRepository users;
    private final Path root;
    private final Tika tika = new Tika();

    public ResumeController(ResumeRepository resumes, UserRepository users, @Value("${careerpilot.storage.path:./data/resumes}") String storagePath) {
        this.resumes = resumes;
        this.users = users;
        this.root = Path.of(storagePath).toAbsolutePath().normalize();
    }

    private UserAccount current(Authentication a) {
        return users.findByEmailIgnoreCase(a.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private ResumeDocument owned(Long id, Long userId) {
        return resumes.findById(id).filter(r -> r.owner.getId().equals(userId)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found"));
    }

    private Map<String, Object> view(ResumeDocument r) {
        return Map.of("id", r.id, "fileName", r.fileName, "versionName", r.versionName, "targetRole", Objects.toString(r.targetRole, ""), "extractedText", Objects.toString(r.extractedText, ""), "fileSize", r.fileSize, "createdAt", r.createdAt);
    }

    @GetMapping
    List<Map<String, Object>> list(Authentication a) {
        return resumes.findAllByOwnerIdOrderByCreatedAtDesc(current(a).id).stream().map(this::view).toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> upload(Authentication auth, @RequestPart("file") MultipartFile file, @RequestPart(value = "versionName", required = false) String versionName, @RequestPart(value = "targetRole", required = false) String targetRole) {
        UserAccount owner = current(auth);
        if (file.isEmpty() || file.getSize() > MAX_BYTES)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a non-empty PDF, DOCX, or TXT file up to 10 MB");
        String original = Path.of(Objects.toString(file.getOriginalFilename(), "resume")).getFileName().toString();
        if (original.length() > 255)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume filename is too long");
        String lower = original.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".pdf") || lower.endsWith(".docx") || lower.endsWith(".txt")))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PDF, DOCX, and TXT files are supported");
        try {
            byte[] bytes = file.getBytes();
            if (lower.endsWith(".pdf") && (bytes.length < 5 || !new String(bytes, 0, 5).equals("%PDF-")))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is not a valid PDF");
            if (lower.endsWith(".docx") && (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K'))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is not a valid DOCX file");
            Path userDir = root.resolve(owner.id.toString()).normalize();
            if (!userDir.startsWith(root)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            Files.createDirectories(userDir);
            String storage = UUID.randomUUID() + extension(lower);
            Path target = userDir.resolve(storage).normalize();
            if (!target.startsWith(userDir)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
            String text;
            try {
                text = tika.parseToString(target.toFile());
            } catch (Exception parseError) {
                Files.deleteIfExists(target);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read this document. Check that it is not encrypted or damaged.");
            }
            if (text == null || text.isBlank()) {
                Files.deleteIfExists(target);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No readable text was found in this document");
            }
            if (text.length() > 95000) text = text.substring(0, 95000);
            ResumeDocument resume = resumes.save(new ResumeDocument(owner, original, storage, versionName == null || versionName.isBlank() ? "Original" : versionName.substring(0, Math.min(255, versionName.length())), targetRole, text, bytes.length));
            return view(resume);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume could not be saved");
        }
    }

    private String extension(String name) {
        return name.endsWith(".pdf") ? ".pdf" : name.endsWith(".docx") ? ".docx" : ".txt";
    }

    @GetMapping("/{id}")
    Map<String, Object> get(Authentication a, @PathVariable Long id) {
        return view(owned(id, current(a).id));
    }

    @GetMapping("/{id}/download")
    ResponseEntity<FileSystemResource> download(Authentication a, @PathVariable Long id) {
        ResumeDocument r = owned(id, current(a).id);
        Path path = root.resolve(r.owner.getId().toString()).resolve(r.storageName).normalize();
        if (!path.startsWith(root) || !Files.exists(path))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume file not found");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + r.fileName.replace("\"", "") + "\"").body(new FileSystemResource(path));
    }

    @PutMapping("/{id}")
    Map<String, Object> update(Authentication a, @PathVariable Long id, @RequestBody Map<String, String> body) {
        ResumeDocument r = owned(id, current(a).id);
        String text = Objects.toString(body.get("extractedText"), "");
        if (text.length() > 95000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume text exceeds the 95,000 character limit");
        r.extractedText = text;
        String versionName = Objects.toString(body.get("versionName"), r.versionName);
        String targetRole = body.get("targetRole");
        if (versionName.isBlank() || versionName.length() > 255 || (targetRole != null && targetRole.length() > 300))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Version name or target role is invalid");
        r.versionName = versionName.trim();
        r.targetRole = targetRole;
        return view(resumes.save(r));
    }

    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> version(Authentication a, @PathVariable Long id, @RequestBody Map<String, String> body) {
        ResumeDocument source = owned(id, current(a).id);
        String text = Objects.toString(body.get("extractedText"), source.extractedText);
        String name = Objects.toString(body.get("versionName"), "Tailored draft");
        String targetRole = body.getOrDefault("targetRole", source.targetRole);
        if (text.length() > 95000 || name.isBlank() || name.length() > 255 || (targetRole != null && targetRole.length() > 300))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Version text, name, or target role is invalid");
        ResumeDocument copy = resumes.save(new ResumeDocument(source.owner, source.fileName, source.storageName, name.trim(), targetRole, text, source.fileSize));
        return view(copy);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Authentication a, @PathVariable Long id) {
        ResumeDocument r = owned(id, current(a).id);
        Path path = root.resolve(r.owner.getId().toString()).resolve(r.storageName).normalize();
        resumes.delete(r);
        if (resumes.countByOwnerIdAndStorageName(r.owner.getId(), r.storageName) == 0) {
            try {
                if (path.startsWith(root)) Files.deleteIfExists(path);
            } catch (Exception ignored) {
            }
        }
    }
}


