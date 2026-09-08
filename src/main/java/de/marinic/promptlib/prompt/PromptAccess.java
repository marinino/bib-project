package de.marinic.promptlib.prompt;

import de.marinic.promptlib.common.error.ForbiddenException;
import de.marinic.promptlib.common.error.NotFoundException;
import java.util.UUID;

/**
 * Shared ownership/visibility rules, used by PromptService, PromptVersionService and
 * ExecutionService (execution package) alike so a private prompt's content can't be read (as a
 * version) or run (as an execution) by anyone but its owner, even though those two services
 * never call PromptService directly.
 */
public final class PromptAccess {

    private PromptAccess() {}

    /**
     * A missing prompt and a private prompt owned by someone else deliberately return the exact
     * same 404 - a 403 would leak that a private prompt with this id exists at all.
     */
    public static void requireReadable(Prompt prompt, UUID requesterId) {
        boolean visible = prompt.getVisibility() == Visibility.PUBLIC || requesterId.equals(prompt.getOwnerId());
        if (!visible) {
            throw new NotFoundException("Prompt %s not found".formatted(prompt.getId()));
        }
    }

    public static void requireOwner(Prompt prompt, UUID requesterId) {
        if (!requesterId.equals(prompt.getOwnerId())) {
            throw new ForbiddenException("You do not own prompt %s".formatted(prompt.getId()));
        }
    }
}
