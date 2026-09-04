package de.marinic.promptlib.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.marinic.promptlib.TestcontainersConfiguration;
import de.marinic.promptlib.common.config.JpaAuditingConfig;
import de.marinic.promptlib.tag.Tag;
import de.marinic.promptlib.tag.TagRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class PromptRepositoryTest {

    @Autowired private PromptRepository promptRepository;
    @Autowired private TagRepository tagRepository;

    @Test
    void savesAndLoadsPromptWithVersionAndTags() {
        Tag tag = tagRepository.save(new Tag("comfyui"));

        Prompt prompt = new Prompt();
        prompt.setTitle("Video Prompt");
        prompt.setDescription("Für ComfyUI Workflows");
        prompt.setCurrentVersionNo(1);
        prompt.setTags(Set.of(tag));

        PromptVersion version = new PromptVersion();
        version.setPrompt(prompt);
        version.setVersionNo(1);
        version.setContent("Ein Video von {{subject}}");
        prompt.getVersions().add(version);

        Prompt saved = promptRepository.saveAndFlush(prompt);

        Optional<Prompt> found = promptRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Video Prompt");
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getTags()).extracting(Tag::getName).containsExactly("comfyui");
        assertThat(found.get().getVersions()).hasSize(1);
        assertThat(found.get().getVersions().getFirst().getContent()).isEqualTo("Ein Video von {{subject}}");
    }

    @Test
    void rejectsDuplicateVersionNoForSamePrompt() {
        Prompt prompt = new Prompt();
        prompt.setTitle("Duplicate Version Test");

        PromptVersion v1 = new PromptVersion();
        v1.setPrompt(prompt);
        v1.setVersionNo(1);
        v1.setContent("erste Version");
        prompt.getVersions().add(v1);

        PromptVersion v1Again = new PromptVersion();
        v1Again.setPrompt(prompt);
        v1Again.setVersionNo(1);
        v1Again.setContent("zweite Version, gleiche Nummer");
        prompt.getVersions().add(v1Again);

        assertThatThrownBy(() -> promptRepository.saveAndFlush(prompt))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
