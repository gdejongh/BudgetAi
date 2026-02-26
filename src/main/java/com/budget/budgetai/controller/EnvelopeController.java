package com.budget.budgetai.controller;

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
        EnvelopeDTO created = envelopeService.create(envelopeDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EnvelopeDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(envelopeService.getById(id));
    }

    @GetMapping(params = "userId")
    public ResponseEntity<List<EnvelopeDTO>> getByAppUserId(@RequestParam UUID userId) {
        return ResponseEntity.ok(envelopeService.getByAppUserId(userId));
    }

    @GetMapping(params = {"userId", "name"})
    public ResponseEntity<List<EnvelopeDTO>> getByAppUserIdAndName(@RequestParam UUID userId, @RequestParam String name) {
        return ResponseEntity.ok(envelopeService.getByAppUserIdAndName(userId, name));
    }

    @GetMapping
    public ResponseEntity<List<EnvelopeDTO>> getAll() {
        return ResponseEntity.ok(envelopeService.getAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<EnvelopeDTO> update(@PathVariable UUID id, @Valid @RequestBody EnvelopeDTO envelopeDTO) {
        return ResponseEntity.ok(envelopeService.update(id, envelopeDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        envelopeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
