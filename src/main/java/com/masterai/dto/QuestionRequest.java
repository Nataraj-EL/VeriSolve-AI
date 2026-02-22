package com.masterai.dto;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.multipart.MultipartFile;

public class QuestionRequest {
    private String text;
    private MultipartFile image;
    private String subject; // "APTITUDE" or "CODING"

    public QuestionRequest() {}

    public QuestionRequest(String text, MultipartFile image, String subject) {
        this.text = text;
        this.image = image;
        this.subject = subject;
    }

    public String text() { return text; }
    public void setText(String text) { this.text = text; }

    public MultipartFile image() { return image; }
    public void setImage(MultipartFile image) { this.image = image; }

    public String subject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    // Logic for backwards compatibility with record-style access
    public String getText() { return text; }
    public MultipartFile getImage() { return image; }
    public String getSubject() { return subject; }
}
