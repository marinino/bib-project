package de.marinic.promptlib.prompt;

import de.marinic.promptlib.tag.Tag;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
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

    /**
     * Eagerly fetches tags in the same query instead of leaving them lazy (which would
     * cause one extra SELECT per prompt when the result list is mapped to responses).
     * Spring Data reuses this Specification for the COUNT query too, where a fetch is
     * semantically invalid, so it is skipped there.
     */
    public static Specification<Prompt> fetchTags() {
        return (root, cq, cb) -> {
            if (Long.class != cq.getResultType() && long.class != cq.getResultType()) {
                root.fetch("tags", JoinType.LEFT);
                cq.distinct(true);
            }
            return cb.conjunction();
        };
    }
}
