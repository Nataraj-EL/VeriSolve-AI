package com.masterai.controller;

import com.masterai.dto.AnswerResponse;
import com.masterai.dto.QuestionRequest;
import com.masterai.service.OrchestratorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/questions")
public class QuestionsController {

    private final OrchestratorService orchestratorService;

    public QuestionsController(OrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @PostMapping(value = "/solve", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnswerResponse> solve(@Valid @org.springframework.web.bind.annotation.ModelAttribute QuestionRequest request) {
        System.out.println("DEBUG: Received request with text: [" + request.text() + "]");
        if (request.image() != null) {
            System.out.println("DEBUG: Received image: " + request.image().getOriginalFilename() + " (Size: " + request.image().getSize() + ")");
        }
        
        AnswerResponse response = orchestratorService.solve(request);
        return ResponseEntity.ok(response);
    }
}
