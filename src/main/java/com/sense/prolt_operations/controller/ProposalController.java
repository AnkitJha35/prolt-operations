package com.sense.prolt_operations.controller;

import com.sense.prolt_operations.service.ProposalService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/proposals", "/api/proposal"})
public class ProposalController {

    private final ProposalService proposalService;

    public ProposalController(ProposalService proposalService) {
        this.proposalService = proposalService;
    }

    @PostMapping("/generate")
    public ResponseEntity<byte[]> generate(@RequestBody(required = false) Map<String, Object> clientData) {
        byte[] docBytes = proposalService.generateProposal(clientData != null ? clientData : Map.of(), "default");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Proposal.docx")
            .body(docBytes);
    }

    @PostMapping("/generate-technical")
    public ResponseEntity<byte[]> generateTechnical(@RequestBody(required = false) Map<String, Object> clientData) {
        byte[] docBytes = proposalService.generateProposal(clientData != null ? clientData : Map.of(), "technical");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Technical_Proposal.docx")
            .body(docBytes);
    }

    @PostMapping("/generate-commercial")
    public ResponseEntity<byte[]> generateCommercial(@RequestBody(required = false) Map<String, Object> clientData) {
        byte[] docBytes = proposalService.generateProposal(clientData != null ? clientData : Map.of(), "commercial");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Commercial_Proposal.docx")
            .body(docBytes);
    }
}
