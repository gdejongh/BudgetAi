package com.budget.budgetai.controller;

import com.budget.budgetai.config.SecurityUtils;
import com.budget.budgetai.dto.CreateEnvelopeRequest;
import com.budget.budgetai.dto.EnvelopeAllocationDTO;
import com.budget.budgetai.dto.EnvelopeDTO;
import com.budget.budgetai.dto.EnvelopeSpentSummaryDTO;
import com.budget.budgetai.dto.SetAllocationRequest;
import com.budget.budgetai.service.EnvelopeAllocationService;
import com.budget.budgetai.service.EnvelopeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/envelopes", produces = MediaType.APPLICATION_JSON_VALUE)
public class EnvelopeController {

    private final EnvelopeService envelopeService;
    private final EnvelopeAllocationService envelopeAllocationService;

    public EnvelopeController(EnvelopeService envelopeService, EnvelopeAllocationService envelopeAllocationService) {
        this.envelopeService = envelopeService;
        this.envelopeAllocationService = envelopeAllocationService;
    }

    @PostMapping
    @Operation(operationId = "createEnvelope")
    public ResponseEntity<EnvelopeDTO> create(@Valid @RequestBody CreateEnvelopeRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        EnvelopeDTO envelopeDTO = new EnvelopeDTO();
        envelopeDTO.setAppUserId(userId);
        envelopeDTO.setEnvelopeCategoryId(request.getEnvelopeCategoryId());
        envelopeDTO.setName(request.getName());
        envelopeDTO.setAllocatedBalance(request.getAllocatedBalance());
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

    @GetMapping("/spent-summary")
    @Operation(operationId = "getEnvelopeSpentSummary", summary = "Get spent summary per envelope for the current user")
    public ResponseEntity<List<EnvelopeSpentSummaryDTO>> getSpentSummary(
            @Parameter(description = "Period start date (yyyy-MM-dd)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Period end date (yyyy-MM-dd)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(envelopeService.getSpentSummaries(userId, startDate, endDate));
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

    // ── Monthly Allocation Endpoints ──────────────────────────────

    @GetMapping("/allocations")
    @Operation(operationId = "getMonthlyAllocations", summary = "Get all envelope allocations for the current user in a specific month")
    public ResponseEntity<List<EnvelopeAllocationDTO>> getMonthlyAllocations(
            @Parameter(description = "Month as yyyy-MM-dd (day is ignored, first-of-month used)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(envelopeAllocationService.getAllocationsForMonth(userId, month));
    }

    @PutMapping("/{id}/allocation")
    @Operation(operationId = "setAllocation", summary = "Set the allocation amount for an envelope in a specific month")
    public ResponseEntity<EnvelopeAllocationDTO> setAllocation(
            @PathVariable UUID id,
            @Parameter(description = "Month as yyyy-MM-dd (day is ignored, first-of-month used)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month,
            @Valid @RequestBody SetAllocationRequest request) {
        EnvelopeDTO existing = envelopeService.getById(id);
        SecurityUtils.verifyOwnership(existing.getAppUserId());
        return ResponseEntity.ok(envelopeAllocationService.setAllocation(id, month, request.getAmount()));
    }

    @GetMapping("/{id}/allocations")
    @Operation(operationId = "getAllocationHistory", summary = "Get the full allocation history for an envelope")
    public ResponseEntity<List<EnvelopeAllocationDTO>> getAllocationHistory(@PathVariable UUID id) {
        EnvelopeDTO existing = envelopeService.getById(id);
        SecurityUtils.verifyOwnership(existing.getAppUserId());
        return ResponseEntity.ok(envelopeAllocationService.getAllocationHistory(id));
    }
}
