package com.example.tricol.tricolspringbootrestapi.controller;

import com.example.tricol.tricolspringbootrestapi.dto.request.CreateExitSlipRequest;
import com.example.tricol.tricolspringbootrestapi.dto.response.ExitSlipResponse;
import com.example.tricol.tricolspringbootrestapi.enums.ExitSlipStatus;
import com.example.tricol.tricolspringbootrestapi.service.ExitSlipService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/exit-slips")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ExitSlipController {
    
    private final ExitSlipService exitSlipService;
    
    @PostMapping
    public ResponseEntity<ExitSlipResponse> createExitSlip(
            @Valid @RequestBody CreateExitSlipRequest request) {
        ExitSlipResponse response = exitSlipService.createExitSlip(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/{id}/validate")
    public ResponseEntity<ExitSlipResponse> validateExitSlip(@PathVariable Long id) {
        ExitSlipResponse response = exitSlipService.validateExitSlip(id);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ExitSlipResponse> cancelExitSlip(@PathVariable Long id) {
        ExitSlipResponse response = exitSlipService.cancelExitSlip(id);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ExitSlipResponse> getExitSlip(@PathVariable Long id) {
        ExitSlipResponse response = exitSlipService.getExitSlip(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<ExitSlipResponse>> getAllExitSlips(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String workshop) {
        
        if (status != null) {
            ExitSlipStatus exitSlipStatus = ExitSlipStatus.valueOf(status.toUpperCase());
            List<ExitSlipResponse> responses = exitSlipService.getExitSlipsByStatus(exitSlipStatus);
            return ResponseEntity.ok(responses);
        }
        
        if (workshop != null) {
            List<ExitSlipResponse> responses = exitSlipService.getExitSlipsByWorkshop(workshop);
            return ResponseEntity.ok(responses);
        }
        
        List<ExitSlipResponse> responses = exitSlipService.getAllExitSlips();
        return ResponseEntity.ok(responses);
    }
}
