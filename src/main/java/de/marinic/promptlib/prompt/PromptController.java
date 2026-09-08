package de.marinic.promptlib.prompt;

import de.marinic.promptlib.common.page.PageResponse;
import de.marinic.promptlib.common.security.AppUserPrincipal;
import de.marinic.promptlib.prompt.dto.CreatePromptRequest;
import de.marinic.promptlib.prompt.dto.PromptResponse;
import de.marinic.promptlib.prompt.dto.UpdatePromptRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/prompts")
public class PromptController {

    private final PromptService promptService;

    public PromptController(PromptService promptService) {
        this.promptService = promptService;
    }

    @PostMapping
    public ResponseEntity<PromptResponse> create(
            @Valid @RequestBody CreatePromptRequest request, @AuthenticationPrincipal AppUserPrincipal principal) {
        PromptResponse response = promptService.create(request, principal.getId());
        return ResponseEntity.created(URI.create("/api/v1/prompts/" + response.id())).body(response);
    }

    @GetMapping
    public PageResponse<PromptResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Set<String> tags,
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return promptService.search(query, tags, principal.getId(), pageable);
    }

    @GetMapping("/{id}")
    public PromptResponse get(@PathVariable UUID id, @AuthenticationPrincipal AppUserPrincipal principal) {
        return promptService.get(id, principal.getId());
    }

    @PatchMapping("/{id}")
    public PromptResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePromptRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return promptService.update(id, request, principal.getId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal AppUserPrincipal principal) {
        promptService.delete(id, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
