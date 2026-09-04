package de.marinic.promptlib.prompt;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromptRepository extends JpaRepository<Prompt, UUID> {}
