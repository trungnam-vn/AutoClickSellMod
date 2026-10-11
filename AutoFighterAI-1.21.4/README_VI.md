# Auto Fighter AI GUI — Fabric 1.21.4

## Open the GUI
Enter `/autofighterai` in chat to open the control panel.
Commands: `/autofighterai on`, `/autofighterai off`, and `/autofighterai feature <name> <true|false>`.
Feature names: `attack`, `approach`, `jump`, `dodge`, `eat`, `alerts`, `ai`.
Press **G** to toggle the master AI.

## Features
Each feature can be turned on or off independently. Settings persist in `.minecraft/config/autofighterai.properties`.
- Master AI
- Auto attack
- Approach target
- Jump attack chance (30%)
- Sideways dodge
- Auto eat at 10 HP or lower (up to two uses per enable cycle)
- Mob / health alerts

The movement logic does not implement full pathfinding and can get stuck on obstacles. CI build verifies compilation and JAR contents; test in a single-player world first.
