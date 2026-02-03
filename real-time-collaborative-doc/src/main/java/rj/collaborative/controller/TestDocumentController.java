package rj.collaborative.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import rj.collaborative.entity.DocumentEntity;
import rj.collaborative.service.DocumentService;

import java.util.List;
@Slf4j
@RestController
public class TestDocumentController {

    @Autowired
    private DocumentService documentService;

    @PostMapping("/test-create-doc")
    public String testCreate() {
        DocumentEntity doc = documentService.create("测试标题", "测试内容");
        log.info("创建成功！ID: {}", doc.getId());
        return "创建成功！ID: " + doc.getId();
    }

    @GetMapping("/test-get-doc/{id}")
    public DocumentEntity testGetById(@PathVariable String id) {
        return documentService.getById(id).orElseThrow(() -> new RuntimeException("文档不存在"));
    }

    @GetMapping("/test-list-docs/{userId}")
    public List<DocumentEntity> testListByUser(@PathVariable String userId) {
        return documentService.listByUser(userId);
    }
}