package com.nyzy.attachment;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nyzy.auth.UserContext;
import com.nyzy.common.ApiException;
import com.nyzy.attachment.mapper.AttachmentMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class AttachmentService {

    private final AttachmentMapper mapper;
    private final Path root;

    public AttachmentService(AttachmentMapper mapper,
                             @Value("${nyzy.upload-dir:/app/uploads}") String uploadDir) {
        this.mapper = mapper;
        this.root = Paths.get(uploadDir);
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录: " + uploadDir, e);
        }
    }

    public List<Attachment> list(String bizType, Long bizId) {
        return mapper.selectList(new QueryWrapper<Attachment>()
                .eq("biz_type", bizType).eq("biz_id", bizId).orderByDesc("id"));
    }

    public Attachment upload(String bizType, Long bizId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ApiException("文件不能为空");
        if (file.getSize() > 20 * 1024 * 1024) throw new ApiException("文件不能超过 20MB");
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')) : "";
        String stored = UUID.randomUUID().toString().replace("-", "") + ext;
        try {
            Files.copy(file.getInputStream(), root.resolve(stored));
        } catch (IOException e) {
            throw new ApiException("文件保存失败: " + e.getMessage());
        }
        Attachment a = new Attachment();
        a.setBizType(bizType);
        a.setBizId(bizId);
        a.setFileName(original);
        a.setStoredName(stored);
        a.setContentType(file.getContentType());
        a.setFileSize(file.getSize());
        a.setUploadedBy(UserContext.username());
        mapper.insert(a);
        return a;
    }

    public Attachment get(Long id) {
        Attachment a = mapper.selectById(id);
        if (a == null) throw new ApiException(404, "附件不存在");
        return a;
    }

    public Path pathOf(Attachment a) {
        return root.resolve(a.getStoredName());
    }

    public void delete(Long id) {
        Attachment a = get(id);
        try {
            Files.deleteIfExists(pathOf(a));
        } catch (IOException ignored) {
        }
        mapper.deleteById(id);
    }
}
