package de.marinic.promptlib.prompt;

import de.marinic.promptlib.common.error.ForbiddenException;
import de.marinic.promptlib.common.error.NotFoundException;
import java.util.UUID;

/**
 * Shared ownership/visibility rules, used by PromptService, PromptVersionService and
 * ExecutionService (execution package) alike so a private prompt's content can't be read (as a
 * version) or run (as an execution) by anyone but its owner, even though those two services
 * never call PromptService directly.
 *
 * <p>The two checks deliberately answer differently, and are deliberately NOT layered on top of
 * each other: reads hide a foreign private prompt behind a 404, writes reject it with a 403.
 * See {@link #requireOwner} for why that asymmetry is intentional rather than an oversight.
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

    /**
     * Answers 403 for any prompt the requester does not own - including a private one, where
     * this does reveal that the id exists, unlike {@link #requireReadable}.
     *
     * <p>That is a conscious trade-off, not a gap. Prompt ids are random UUIDs, so there is
     * nothing to enumerate: an attacker can only learn "this id exists" for an id they already
     * possess, which is not a meaningful escalation. In exchange, every write path gets one
     * honest answer - "you are not the owner" - instead of a 404 that would also be returned for
     * genuinely mistyped ids, which is materially harder to debug against a live API.
     *
     * <p>Callers must therefore NOT prefix this with {@link #requireReadable} to "harden" it;
     * that would silently turn the tested 403 into a 404. The current behaviour is pinned by
     * PromptOwnershipIntegrationTest (PATCH/DELETE on a foreign private prompt expect 403).
     * Revisit only if prompt ids ever stop being unguessable.
     */
    public static void requireOwner(Prompt prompt, UUID requesterId) {
        requireReadable(prompt, requesterId)
        if (!requesterId.equals(prompt.getOwnerId())) {
            throw new ForbiddenException("You do not own prompt %s".formatted(prompt.getId()));
        }
    }
}
