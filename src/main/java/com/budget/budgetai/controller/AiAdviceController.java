package com.budget.budgetai.controller;

import com.budget.budgetai.config.SecurityUtils;
import com.budget.budgetai.dto.AiAdviceDTO;
import com.budget.budgetai.service.AiAdviceService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(value = "/api/ai", produces = MediaType.APPLICATION_JSON_VALUE)
public class AiAdviceController {

    private final AiAdviceService aiAdviceService;

    public AiAdviceController(AiAdviceService aiAdviceService) {
        this.aiAdviceService = aiAdviceService;
    }

    @PostMapping("/advice")
    @Operation(operationId = "getAiAdvice")
    public ResponseEntity<AiAdviceDTO> getAdvice() {
        UUID userId = SecurityUtils.getCurrentUserId();
        AiAdviceDTO advice = aiAdviceService.getAdvice(userId);
        return ResponseEntity.ok(advice);
    }

    @DeleteMapping("/advice/cache")
    @Operation(operationId = "clearAiAdviceCache")
    public ResponseEntity<Void> clearCache() {
        UUID userId = SecurityUtils.getCurrentUserId();
        aiAdviceService.clearCache(userId);
        return ResponseEntity.noContent().build();
    }
}
