package net.tabor.seedcity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Proves the JUnit wiring works. Replace with real tests as the card parser and grammar land. */
class ToolchainTest {
	@Test
	void modIdIsStable() {
		assertEquals("seedcity", SeedCity.MOD_ID);
	}
}
