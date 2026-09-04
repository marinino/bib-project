package de.marinic.promptlib.prompt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromptVersionRepository extends JpaRepository<PromptVersion, UUID> {

    List<PromptVersion> findByPromptIdOrderByVersionNoDesc(UUID promptId);

    Optional<PromptVersion> findByPromptIdAndVersionNo(UUID promptId, Integer versionNo);

    @Query("select coalesce(max(v.versionNo), 0) from PromptVersion v where v.prompt.id = :promptId")
    int findMaxVersionNo(@Param("promptId") UUID promptId);
}
