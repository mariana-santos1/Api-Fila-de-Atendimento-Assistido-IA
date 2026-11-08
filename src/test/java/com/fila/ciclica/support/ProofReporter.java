package com.fila.ciclica.support;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

/**
 * Prints a machine-readable proof line whenever a test succeeds.
 */
public class ProofReporter implements TestWatcher {

    @Override
    public void testSuccessful(ExtensionContext context) {
        System.out.println("PROOF PASSED: " + context.getDisplayName());
    }
}
