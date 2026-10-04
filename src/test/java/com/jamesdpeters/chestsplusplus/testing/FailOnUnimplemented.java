package com.jamesdpeters.chestsplusplus.testing;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.mockbukkit.mockbukkit.exception.UnimplementedOperationException;

/**
 * MockBukkit's {@link UnimplementedOperationException} is a {@code TestAbortedException}, so JUnit reports the test as
 * skipped and gaps go unnoticed. This turns it into a failure naming the missing method, so every gap is dealt with
 * explicitly (work around it in the test, or move the test to E2E).
 */
public final class FailOnUnimplemented
        implements TestExecutionExceptionHandler,
                org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler {

    @Override
    public void handleBeforeEachMethodExecutionException(ExtensionContext context, Throwable throwable)
            throws Throwable {
        handleTestExecutionException(context, throwable);
    }

    @Override
    public void handleAfterEachMethodExecutionException(ExtensionContext context, Throwable throwable)
            throws Throwable {
        handleTestExecutionException(context, throwable);
    }

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        if (throwable instanceof UnimplementedOperationException unimplemented) {
            StackTraceElement where =
                    unimplemented.getStackTrace().length > 0 ? unimplemented.getStackTrace()[0] : null;
            AssertionError error = new AssertionError("MockBukkit doesn't implement " + where, unimplemented);
            throw error;
        }
        throw throwable;
    }
}
