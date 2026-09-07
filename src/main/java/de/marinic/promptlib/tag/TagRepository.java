package de.marinic.promptlib.tag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    Optional<Tag> findByName(String name);

    /**
     * Race-safe "create if missing": ON CONFLICT DO NOTHING never throws, so two
     * concurrent requests creating the same new tag for the first time can't collide -
     * whichever transaction commits first "wins", the other silently does nothing.
     * Always follow up with findByName() to get the (either way now-existing) row.
     */
    @Modifying
    @Query(value = "insert into tag (id, name) values (gen_random_uuid(), :name) on conflict (name) do nothing", nativeQuery = true)
    void upsertByName(@Param("name") String name);

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
