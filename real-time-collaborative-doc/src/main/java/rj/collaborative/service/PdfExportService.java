package rj.collaborative.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;
import rj.collaborative.entity.DocumentEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;

@Service
public class PdfExportService {


    public byte[] exportDocumentToPdf(DocumentEntity doc) throws DocumentException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document pdfDoc = new Document(PageSize.A4, 50, 50, 50, 50);
        PdfWriter writer = PdfWriter.getInstance(pdfDoc, baos);

        pdfDoc.open();

        // 标题
        Font titleFont = new Font(BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED), 20, Font.BOLD);
        Paragraph title = new Paragraph(doc.getTitle(), titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        pdfDoc.add(title);

        // 作者 & 版本
        Font metaFont = new Font(BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED), 12);
        Paragraph meta = new Paragraph("作者: " + doc.getOwnerId() + "  |  版本: " + doc.getVersion(), metaFont);
        meta.setAlignment(Element.ALIGN_CENTER);
        pdfDoc.add(meta);

        pdfDoc.add(new Paragraph(" ")); // 空行

        // 正文内容 - 修复：从JSON字符串中提取文本内容
        String contentText = extractTextFromContent(String.valueOf(doc.getContent()));
        Font contentFont = new Font(BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED), 12, Font.NORMAL);
        Paragraph content = new Paragraph(contentText, contentFont);
        content.setLeading(20); // 行距
        pdfDoc.add(content);

        // 页脚（可选水印或页码）
        PdfContentByte canvas = writer.getDirectContent();
        Phrase footer = new Phrase("在线文本编辑器 - " + LocalDateTime.now().toString(), metaFont);
        ColumnText.showTextAligned(canvas, Element.ALIGN_CENTER, footer, 300, 30, 0);

        pdfDoc.close();
        return baos.toByteArray();
    }

    /**
     * 从content JSON字符串中提取纯文本
     */
    private String extractTextFromContent(String contentJson) {
        if (contentJson == null || contentJson.isEmpty()) {
            return "";
        }

        try {
            // 解析JSON字符串提取text字段
            org.bson.Document contentDoc = org.bson.Document.parse(contentJson);
            return contentDoc.getString("text") != null ? contentDoc.getString("text") : contentJson;
        } catch (Exception e) {
            // 如果解析失败，直接返回原始内容
            return contentJson;
        }
    }


}