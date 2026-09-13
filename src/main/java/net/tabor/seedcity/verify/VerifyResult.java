package net.tabor.seedcity.verify;

import net.minecraft.resources.Identifier;

/**
 * Outcome of one verification. On failure {@code message} names the offending port whenever the
 * model can identify one (design doc 25, phase 0: "reports fail with a port name").
 */
public record VerifyResult(Identifier cell, boolean pass, String message) {
	public static VerifyResult pass(Identifier cell) {
		return new VerifyResult(cell, true, "ok");
	}

	public static VerifyResult fail(Identifier cell, String message) {
		return new VerifyResult(cell, false, message);
	}

	@Override
	public String toString() {
		return cell + ": " + (pass ? "PASS" : "FAIL") + (pass ? "" : " (" + message + ")");
	}
}
