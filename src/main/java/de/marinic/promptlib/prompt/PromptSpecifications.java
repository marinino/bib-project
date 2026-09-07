package de.marinic.promptlib.prompt;

import de.marinic.promptlib.tag.Tag;
import jakarta.persistence.criteria.Join;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

public final class PromptSpecifications {

    private PromptSpecifications() {}

    public static Specification<Prompt> titleOrDescriptionContains(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String pattern = "%" + query.toLowerCase() + "%";
        return (root, cq, cb) ->
                cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern));
    }

    public static Specification<Prompt> hasAnyTag(Set<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return null;
        }
        return (root, cq, cb) -> {
            cq.distinct(true);
            Join<Prompt, Tag> tagJoin = root.join("tags");
            return tagJoin.get("name").in(tagNames);
        };
    }
}
