package com.budget.budgetai.controller;

import com.budget.budgetai.dto.AppUserDTO;
import com.budget.budgetai.service.AppUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class AppUserController {

    private final AppUserService appUserService;

    public AppUserController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    @PostMapping
    public ResponseEntity<AppUserDTO> create(@Valid @RequestBody AppUserDTO appUserDTO) {
        AppUserDTO created = appUserService.create(appUserDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppUserDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(appUserService.getById(id));
    }

    @GetMapping(params = "email")
    public ResponseEntity<AppUserDTO> getByEmail(@RequestParam String email) {
        return ResponseEntity.ok(appUserService.getByEmail(email));
    }

    @GetMapping
    public ResponseEntity<List<AppUserDTO>> getAll() {
        return ResponseEntity.ok(appUserService.getAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<AppUserDTO> update(@PathVariable UUID id, @Valid @RequestBody AppUserDTO appUserDTO) {
        return ResponseEntity.ok(appUserService.update(id, appUserDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        appUserService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
