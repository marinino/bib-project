package de.marinic.promptlib.execution;

import de.marinic.promptlib.common.security.AppUserPrincipal;
import de.marinic.promptlib.execution.dto.CreateExecutionRequest;
import de.marinic.promptlib.execution.dto.ExecutionResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/executions")
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping
    public ResponseEntity<ExecutionResponse> create(
            @Valid @RequestBody CreateExecutionRequest request, @AuthenticationPrincipal AppUserPrincipal principal) {
        ExecutionResponse response = executionService.create(request, principal.getId());
        return ResponseEntity.accepted().location(URI.create("/api/v1/executions/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public ExecutionResponse get(@PathVariable UUID id) {
        return executionService.get(id);
    }

    @GetMapping
    public List<ExecutionResponse> list(@RequestParam UUID promptId) {
        return executionService.listByPrompt(promptId);
    }
}
