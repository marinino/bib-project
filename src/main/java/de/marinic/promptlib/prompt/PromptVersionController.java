package de.marinic.promptlib.prompt;

import de.marinic.promptlib.prompt.dto.CreateVersionRequest;
import de.marinic.promptlib.prompt.dto.PromptResponse;
import de.marinic.promptlib.prompt.dto.VersionResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/prompts/{promptId}/versions")
public class PromptVersionController {

    private final PromptVersionService promptVersionService;

    public PromptVersionController(PromptVersionService promptVersionService) {
        this.promptVersionService = promptVersionService;
    }

    @PostMapping
    public VersionResponse create(@PathVariable UUID promptId, @Valid @RequestBody CreateVersionRequest request) {
        return promptVersionService.create(promptId, request);
    }

    @GetMapping
    public List<VersionResponse> list(@PathVariable UUID promptId) {
        return promptVersionService.list(promptId);
    }

    @GetMapping("/{versionNo}")
    public VersionResponse get(@PathVariable UUID promptId, @PathVariable int versionNo) {
        return promptVersionService.get(promptId, versionNo);
    }

    @PostMapping("/{versionNo}/activate")
    public PromptResponse activate(@PathVariable UUID promptId, @PathVariable int versionNo) {
        return promptVersionService.activate(promptId, versionNo);
    }
}
