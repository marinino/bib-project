package de.marinic.promptlib.execution;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Waits for the transaction that created the Execution row to actually commit before
 * kicking off the async run. Without this, publishing straight into ExecutionRunner.run()
 * from inside ExecutionService.create() could start the async thread before the INSERT is
 * committed - it might then look up an execution row that, from its point of view, doesn't
 * exist yet.
 */
@Component
public class ExecutionCreatedListener {

    private final ExecutionRunner executionRunner;

    public ExecutionCreatedListener(ExecutionRunner executionRunner) {
        this.executionRunner = executionRunner;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExecutionCreated(ExecutionCreatedEvent event) {
        executionRunner.run(event.executionId());
    }
}
