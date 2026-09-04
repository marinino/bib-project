package de.marinic.promptlib.tag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    Optional<Tag> findByName(String name);

    @Query(
            "select t.name as name, count(p) as promptCount "
                    + "from Tag t left join t.prompts p "
                    + "group by t.name "
                    + "order by t.name")
    List<TagCount> findAllWithPromptCount();

    interface TagCount {
        String getName();

        long getPromptCount();
    }
}
