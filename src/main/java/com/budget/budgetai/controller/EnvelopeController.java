package com.budget.budgetai.controller;

import com.budget.budgetai.config.SecurityUtils;
import com.budget.budgetai.dto.EnvelopeDTO;
import com.budget.budgetai.service.EnvelopeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/envelopes")
public class EnvelopeController {

    private final EnvelopeService envelopeService;

    public EnvelopeController(EnvelopeService envelopeService) {
        this.envelopeService = envelopeService;
    }

    @PostMapping
    public ResponseEntity<EnvelopeDTO> create(@Valid @RequestBody EnvelopeDTO envelopeDTO) {
        UUID userId = SecurityUtils.getCurrentUserId();
        envelopeDTO.setAppUserId(userId);
        EnvelopeDTO created = envelopeService.create(envelopeDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EnvelopeDTO> getById(@PathVariable UUID id) {
        EnvelopeDTO envelope = envelopeService.getById(id);
        SecurityUtils.verifyOwnership(envelope.getAppUserId());
        return ResponseEntity.ok(envelope);
    }

    @GetMapping
    public ResponseEntity<List<EnvelopeDTO>> getByCurrentUser() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(envelopeService.getByAppUserId(userId));
    }

    @GetMapping(params = "name")
    public ResponseEntity<List<EnvelopeDTO>> getByName(@RequestParam String name) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(envelopeService.getByAppUserIdAndName(userId, name));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EnvelopeDTO> update(@PathVariable UUID id, @Valid @RequestBody EnvelopeDTO envelopeDTO) {
        EnvelopeDTO existing = envelopeService.getById(id);
        SecurityUtils.verifyOwnership(existing.getAppUserId());
        return ResponseEntity.ok(envelopeService.update(id, envelopeDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        EnvelopeDTO existing = envelopeService.getById(id);
        SecurityUtils.verifyOwnership(existing.getAppUserId());
        envelopeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
