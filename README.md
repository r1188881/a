# Maze Solver (Forge 1.8.9, client-side) - GUI edition

## Build
JDK 8 + ForgeGradle 2.1 (Gradle 2.14). If the Gradle setup is troublesome,
copy src/main/java/** and src/main/resources/** into a working 1.8.9 Forge
template and build there.

    gradle setupDecompWorkspace
    gradle build        # jar in build/libs/

## Use
RShift  open the Maze Solver menu (rebindable under Controls > Maze Solver)
M       quick start/stop     G  set goal to the block you're looking at

Menu: Solver ON/OFF toggle, Preview Route (plans without walking),
Set Goal: Looked-At (the block your crosshair was on when you opened the menu),
Auto-Find Exit (nearest emerald/gold/diamond block), progress bar, and a
top-down preview map (north is up): grey floor, dark walls, cyan = route ahead,
green = completed, yellow = start, red = goal, blinking white = you.
The route stays on the preview after the run finishes.

## Limits
Loaded chunks only, 128 block range, full-block floors, 1-block step-ups,
drops up to 3. The preview reads walls at the start height, so multi-level
mazes show only the current level's walls (the route is always drawn).
