package com.nyzy.attachment;

import com.nyzy.common.Result;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

@RestController
@RequestMapping("/api/attachment")
public class AttachmentController {

    private final AttachmentService service;

    public AttachmentController(AttachmentService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public Result<List<Attachment>> list(@RequestParam String bizType, @RequestParam Long bizId) {
        return Result.ok(service.list(bizType, bizId));
    }

    @PostMapping("/upload")
    public Result<Attachment> upload(@RequestParam String bizType, @RequestParam Long bizId,
                                     @RequestParam("file") MultipartFile file) {
        return Result.ok(service.upload(bizType, bizId, file));
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) throws IOException {
        Attachment a = service.get(id);
        InputStream in = Files.newInputStream(service.pathOf(a));
        String fn = URLEncoder.encode(a.getFileName(), StandardCharsets.UTF_8.name()).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fn)
                .contentType(MediaType.parseMediaType(
                        a.getContentType() == null ? "application/octet-stream" : a.getContentType()))
                .body(new InputStreamResource(in));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }
}
