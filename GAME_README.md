# Ghostly Road

Arcade traffic-driving prototype for Android.

## Current prototype

- Long straight two-lane road.
- Player drives forward.
- Traffic travels in both directions.
- Simple low-poly-style cars are rendered procedurally, so the prototype needs no external 3D assets.
- Same-direction AI can detect a slower car ahead.
- Before overtaking, AI checks the opposite lane for traffic.
- AI changes into the opposite lane, passes the slower vehicle, then checks again before returning.
- Incoming cars travel in the opposite direction.
- Collision, distance score, best score and restart flow are included.
- Touch/swipe controls work without external libraries.

## Branch

This prototype lives on `traffic-ai` so the existing Ghostly Booster implementation on `main` remains intact.

## Next development targets

1. Replace procedural cars with optimized 3D/2.5D models.
2. Add lane-change indicators and better AI safety gaps.
3. Add traffic density, difficulty and speed progression.
4. Add weather, day/night and road scenery.
5. Add menus, pause, settings and sound.
6. Move the renderer to a proper game engine if the project grows beyond the prototype.
