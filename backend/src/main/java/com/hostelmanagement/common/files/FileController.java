package com.hostelmanagement.common.files;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Files")
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService storage;

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        FileAttachment f = storage.getAccessible(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(f.getOriginalName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(storage.load(f));
    }
}
