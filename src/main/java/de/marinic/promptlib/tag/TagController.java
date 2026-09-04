package de.marinic.promptlib.tag;

import de.marinic.promptlib.tag.dto.TagResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final TagRepository tagRepository;

    public TagController(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @GetMapping
    public List<TagResponse> list() {
        return tagRepository.findAllWithPromptCount().stream()
                .map(row -> new TagResponse(row.getName(), row.getPromptCount()))
                .toList();
    }
}
