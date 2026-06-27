package dev.auto.turtle.types;

import java.util.UUID;

public record BlockLocationKey(UUID worldId, int x, int y, int z) {}