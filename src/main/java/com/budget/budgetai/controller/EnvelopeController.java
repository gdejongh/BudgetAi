package com.budget.budgetai.controller;

import com.budget.budgetai.config.SecurityUtils;
import com.budget.budgetai.dto.EnvelopeDTO;
import com.budget.budgetai.service.EnvelopeService;
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
@RequestMapping(value = "/api/envelopes", produces = MediaType.APPLICATION_JSON_VALUE)
public class EnvelopeController {

    private final EnvelopeService envelopeService;

    public EnvelopeController(EnvelopeService envelopeService) {
        this.envelopeService = envelopeService;
    }

    @PostMapping
    @Operation(operationId = "createEnvelope")
    public ResponseEntity<EnvelopeDTO> create(@Valid @RequestBody EnvelopeDTO envelopeDTO) {
        UUID userId = SecurityUtils.getCurrentUserId();
        envelopeDTO.setAppUserId(userId);
        EnvelopeDTO created = envelopeService.create(envelopeDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getEnvelopeById")
    public ResponseEntity<EnvelopeDTO> getById(@PathVariable UUID id) {
        EnvelopeDTO envelope = envelopeService.getById(id);
        SecurityUtils.verifyOwnership(envelope.getAppUserId());
        return ResponseEntity.ok(envelope);
    }

    @GetMapping
    @Operation(operationId = "getEnvelopes", summary = "Get envelopes for current user, optionally filtered by name")
    public ResponseEntity<List<EnvelopeDTO>> getEnvelopes(
            @Parameter(description = "Filter by envelope name") @RequestParam(required = false) String name) {
        UUID userId = SecurityUtils.getCurrentUserId();
        if (name != null) {
            return ResponseEntity.ok(envelopeService.getByAppUserIdAndName(userId, name));
        }
        return ResponseEntity.ok(envelopeService.getByAppUserId(userId));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateEnvelope")
    public ResponseEntity<EnvelopeDTO> update(@PathVariable UUID id, @Valid @RequestBody EnvelopeDTO envelopeDTO) {
        EnvelopeDTO existing = envelopeService.getById(id);
        SecurityUtils.verifyOwnership(existing.getAppUserId());
        return ResponseEntity.ok(envelopeService.update(id, envelopeDTO));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteEnvelope")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        EnvelopeDTO existing = envelopeService.getById(id);
        SecurityUtils.verifyOwnership(existing.getAppUserId());
        envelopeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
