/**
 * Interface: Grammar. Given a frontier slot, its neighbours' bound ports and an optional goal, returns a legal cell choice weighted by district rules. Deterministic: all randomness comes from the one Random seeded by (world seed, seed block pos). Signature sketch: choose(slot, neighbours, goal, rng).
 */
package net.tabor.seedcity.grammar;
