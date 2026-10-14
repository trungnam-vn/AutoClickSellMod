# Auto Fighter AI 1.21.11 — Survival Utilities

Fabric client mod for Minecraft Java Edition 1.21.11.

## Features
- Auto-eat when health is at or below 10 HP and hunger is not full.
- Uses food from the hotbar; if a food cycle begins, it may use up to two items before resetting after health recovers.
- Tries to begin eating through the item interaction when the vanilla inventory screen (E) is open.
- Configurable global, movement, navigation, and auto-eat toggles.
- Waypoint navigation to a block targeted by the crosshair.
- Auto-sprint and a one-block obstacle jump while travelling to a waypoint.
- Movement-input mixin to retain waypoint movement while the inventory screen is open.
- G opens/closes the whole mod; H opens settings.
- Settings saved to config/autofighterai.properties.
- Client commands: /afai, /afai eat, /afai move, /afai mark, /afai nav, /afai clear.

This version contains the survival/navigation utilities; automated combat and weapon selection are not included.
