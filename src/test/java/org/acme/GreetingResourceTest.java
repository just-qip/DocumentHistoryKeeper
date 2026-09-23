package org.acme;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class GreetingResourceTest {
    @Test
    @DisplayName("sanityCheck")
    void sanityCheck() {
        assertTrue(true, "Sanity check passed");
    }
}
