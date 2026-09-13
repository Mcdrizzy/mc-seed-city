/**
 * Interface: Cell. One side is structure NBT plus a JSON sidecar (ports, size, truth model, weights, cost); the other is anything that places blocks. Owns the cell loader, the port schema, footprint and port rotation. Signature sketch: load(id), footprint(origin, rotation), ports(rotation).
 */
package net.tabor.seedcity.cell;
