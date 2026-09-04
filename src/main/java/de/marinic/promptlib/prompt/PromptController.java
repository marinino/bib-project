package de.marinic.promptlib.prompt;

import de.marinic.promptlib.prompt.dto.CreatePromptRequest;
import de.marinic.promptlib.prompt.dto.PromptResponse;
import de.marinic.promptlib.prompt.dto.UpdatePromptRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/prompts")
public class PromptController {

    private final PromptService promptService;

    public PromptController(PromptService promptService) {
        this.promptService = promptService;
    }

    @PostMapping
    public ResponseEntity<PromptResponse> create(@Valid @RequestBody CreatePromptRequest request) {
        PromptResponse response = promptService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/prompts/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public PromptResponse get(@PathVariable UUID id) {
        return promptService.get(id);
    }

    @PatchMapping("/{id}")
    public PromptResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePromptRequest request) {
        return promptService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        promptService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
