package com.eduaccess.config;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Files;

/**
 * Serves Manager-uploaded film posters from the local "uploads/posters" directory.
 * This is required because the Vaadin servlet is mapped to "/*" and would
 * otherwise intercept the static "/uploads/**" requests, preventing
 * the browser from loading the image files.
 *
 * Spring MVC controllers take precedence over the Vaadin servlet, so
 * exposing posters through this controller guarantees they are reachable.
 */
@RestController
@RequestMapping("/uploads/posters")
public class PosterController {

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> servePoster(@PathVariable String filename) {
        // Reject path traversal attempts
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }

        File file = new File(new File(WebConfig.UPLOAD_ROOT, WebConfig.POSTERS_SUBDIR), filename);
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);

        String contentType = null;
        try {
            contentType = Files.probeContentType(file.toPath());
        } catch (Exception ignored) {
        }
        if (contentType == null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".png")) contentType = "image/png";
            else if (lower.endsWith(".webp")) contentType = "image/webp";
            else contentType = "image/jpeg";
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(file.length())
                .body(resource);
    }
}
