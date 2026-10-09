package com.jamesdpeters.chestsplusplus.testing;

/** JUnit tag names. Run a single layer with {@code ./gradlew unitTest} or {@code ./gradlew integrationTest}. */
public final class Tags {

    /** Pure logic, no server. */
    public static final String UNIT = "unit";

    /** MockBukkit-backed plugin wiring. */
    public static final String INTEGRATION = "integration";

    private Tags() {}
}
