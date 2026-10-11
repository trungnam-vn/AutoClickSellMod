# Auto Fighter AI — Optimized release 0.6.0 (Fabric 1.21.4)

## Open the GUI
Press **H** or enter `/autofighterai`, `/afai`, or `/autofightergui` in chat to open the control panel.
Press **G** to toggle the master AI.
Commands: `/autofighterai on`, `/autofighterai off`, and `/autofighterai feature <name> <true|false>`.
Feature names: `attack`, `approach`, `jump`, `dodge`, `eat`, `alerts`, `ai`.

## Features
Each feature can be turned on or off independently. Settings persist in `.minecraft/config/autofighterai.properties`.
- Master AI
- Auto attack
- Approach target
- Jump attack chance (30%)
- Sideways dodge
- Auto eat at 10 HP or lower (up to two uses per enable cycle)
- Mob / health alerts

## Version 0.6.0 — Optimization only
- Reduced repeated world-entity scans by caching the target and refreshing selection every four ticks.
- Discards dead or out-of-range targets immediately.
- Avoids repeatedly writing unchanged movement-key states.
- No new gameplay features added.

The movement logic does not implement full pathfinding and can still get stuck on obstacles. The build pipeline verifies compilation and required JAR contents; test in a single-player world first.
