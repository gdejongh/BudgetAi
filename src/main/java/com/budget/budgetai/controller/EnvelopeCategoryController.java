package com.budget.budgetai.controller;

import com.budget.budgetai.config.SecurityUtils;
import com.budget.budgetai.dto.CreateEnvelopeCategoryRequest;
import com.budget.budgetai.dto.EnvelopeCategoryDTO;
import com.budget.budgetai.service.EnvelopeCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/envelope-categories", produces = MediaType.APPLICATION_JSON_VALUE)
public class EnvelopeCategoryController {

    private final EnvelopeCategoryService envelopeCategoryService;

    public EnvelopeCategoryController(EnvelopeCategoryService envelopeCategoryService) {
        this.envelopeCategoryService = envelopeCategoryService;
    }

    @PostMapping
    @Operation(operationId = "createEnvelopeCategory")
    public ResponseEntity<EnvelopeCategoryDTO> create(
            @Valid @RequestBody CreateEnvelopeCategoryRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        EnvelopeCategoryDTO dto = new EnvelopeCategoryDTO();
        dto.setAppUserId(userId);
        dto.setName(request.getName());
        EnvelopeCategoryDTO created = envelopeCategoryService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getEnvelopeCategoryById")
    public ResponseEntity<EnvelopeCategoryDTO> getById(@PathVariable UUID id) {
        EnvelopeCategoryDTO category = envelopeCategoryService.getById(id);
        SecurityUtils.verifyOwnership(category.getAppUserId());
        return ResponseEntity.ok(category);
    }

    @GetMapping
    @Operation(operationId = "getEnvelopeCategories", summary = "Get envelope categories for current user, optionally filtered by name")
    public ResponseEntity<List<EnvelopeCategoryDTO>> getCategories(
            @Parameter(description = "Filter by category name") @RequestParam(required = false) String name) {
        UUID userId = SecurityUtils.getCurrentUserId();
        if (name != null) {
            return ResponseEntity.ok(envelopeCategoryService.getByAppUserIdAndName(userId, name));
        }
        return ResponseEntity.ok(envelopeCategoryService.getByAppUserId(userId));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateEnvelopeCategory")
    public ResponseEntity<EnvelopeCategoryDTO> update(
            @PathVariable UUID id, @Valid @RequestBody EnvelopeCategoryDTO dto) {
        EnvelopeCategoryDTO existing = envelopeCategoryService.getById(id);
        SecurityUtils.verifyOwnership(existing.getAppUserId());
        return ResponseEntity.ok(envelopeCategoryService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteEnvelopeCategory")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        EnvelopeCategoryDTO existing = envelopeCategoryService.getById(id);
        SecurityUtils.verifyOwnership(existing.getAppUserId());
        envelopeCategoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
