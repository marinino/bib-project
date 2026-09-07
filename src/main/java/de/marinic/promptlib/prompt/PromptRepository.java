package de.marinic.promptlib.prompt;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromptRepository extends JpaRepository<Prompt, UUID>, JpaSpecificationExecutor<Prompt> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Prompt p where p.id = :id")
    Optional<Prompt> findByIdForUpdate(@Param("id") UUID id);
}
