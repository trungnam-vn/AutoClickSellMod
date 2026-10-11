# Auto Fighter AI V2 — Fabric 1.21.3

## Features
- Press **G** to toggle AI.
- Finds the nearest hostile mob within 20 blocks.
- Turns toward the mob's torso and walks toward melee range.
- Attacks when the cooldown is ready.
- Has a 30% chance to jump for a falling strike; otherwise it attacks normally.
- Briefly strafes sideways after taking damage while a hostile is close.
- At 10 HP or lower, tries to eat from the hotbar, at most twice per enable cycle.
- Selects a sword from hotbar, falling back to an axe.
- Direct movement only; no full navigation/pathfinding around obstacles.

## Build and install
Build with Java 21 and Gradle 9.6: `gradle --no-daemon clean build`.
Place `build/libs/AutoFighterAI-1.21.3-0.4.0.jar` in `.minecraft/mods`.
Minecraft 1.21.3, Fabric Loader and matching Fabric API are required.

CI build success proves compilation, not full in-game testing. Test first in a single-player world.
