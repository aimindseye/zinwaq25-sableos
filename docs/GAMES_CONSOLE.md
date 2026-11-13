# Writing games for Sable Games Console

Sable Games Console (`org.sableos.gamesconsole`, sable-src 0207) runs games written in Lua, so a
game can be added to the phone without rebuilding SableOS. The idea comes from rustmix-wave's SD
card Lua apps. This page is for people writing a game; how to play and add games is in the
[user guide](USER_GUIDE.md#sable-games-console).

## A game's folder

A game is one folder holding `game.toml`, one `.lua` script and, if it wants them, up to 24 PNG
pictures for `sable.image`. Each file may be up to 256 KB, and a picture at most 512 by 512 pixels.
A picture's name is 1-32 of a-z, 0-9 and _ then `.png` (`car_red.png`); other files are ignored.

```toml
id = "tilt_maze"          # required: 1-32 of a-z, 0-9 and _; must not be a bundled game's id
name = "Tilt Maze"        # required: 1-40 characters
entry = "main.lua"        # the script, in the same folder (default main.lua)
version = "1.0"
shelf = "motion"          # library tab: motion, puzzle (default) or keyboard
input = ["tilt", "keys"]  # "tilt" (or rustmix-wave's "imu") makes it a tilt game
best = "high"             # "high" (points) or "low" (seconds, moves) is the better score
summary = "Tilt the phone to roll the ball through the maze to the green square."
controls = "Tilt, the trackpad or W A S D roll the ball. Space starts the level over."
```

Only plain `key = value` lines are read. A value is quoted text, a whole number or a list of
quoted text; `#` starts a comment, and `[section]` lines are skipped.

In Add games the console reads the folder the person picks (or each folder inside it), checks the
manifest, the pictures and compiles the script. A problem is shown on the game's row instead of
adding it, for example `id must be 1-32 of a-z, 0-9 and _`, `car.png is bigger than 512 by 512` or
`main.lua:12: '=' expected near 'end'`.

## The script

Define any of these global functions; the console calls them.

| Function | When |
| --- | --- |
| `init()` | When the game starts and each time it starts over (New game, or Space after it ends) |
| `update(dt)` | Every frame while the game runs; `dt` is the time since the last frame in seconds |
| `draw()` | Every frame; draw the whole picture with the `sable` calls below |
| `tilt(step)` | One step: `"left"`, `"right"`, `"up"` or `"down"`. Sent by a tilt (one per lean; the next needs the phone back near level), the arrow keys and trackpad, and W A S D in a tilt game |
| `key(name)` | Other keys: `"space"`, `"enter"`, `"delete"`, and letters and digits in lower case. In a tilt game W A S D and P are controls and never reach `key` |

The `sable` table is the only way out of the script:

| Call | What it does |
| --- | --- |
| `sable.grid(cols, rows)` | Sets the drawing grid (default 10 by 10, at most 200); it is scaled to fill the square screen |
| `sable.rect(x, y, w, h, color)` | A rectangle with slightly rounded corners, in grid cells |
| `sable.circle(x, y, r, color)` | A circle centred on x, y |
| `sable.image(x, y, w, h, name)` | One of the game's pictures (`"car_red.png"`), stretched over that box; a name that isn't in the folder stops the game with an error |
| `sable.text(x, y, text, color, size)` | Text centred on x, y; `size` is its height in cells (default 1) |
| `sable.status(text)` | The line above the game |
| `sable.score(n)` | The score; the library keeps the best one (higher or lower, from `best`) |
| `sable.over(text)` | Ends the game with that message; `sable.over()` clears it |
| `sable.tilt()` | The current lean as two numbers, x and y from -1 to 1 (both 0 when playing with keys). Use it in `update` for games that steer |
| `sable.save(key, value)`, `sable.load(key)` | Small saved text (a level reached, a setting), kept for this game only; `sable.save(key, nil)` removes it |

Colours are Sable theme names, which follow the light or dark theme: `bg`, `text`, `dim`,
`accent`, `wall`, `floor`, `player`, `goal`, `good`, `box`, `warn`. `"#rrggbb"` also works.

A frame draws at most 4,000 shapes, and text is cut at 120 characters.

## The sandbox

The script has Lua 5.2's base functions and the `string`, `table`, `math` and `bit32` libraries
(LuaJ 3.0.1). It has no `io`, `os`, `require`, `load`, `dofile`, `debug`, coroutines, `print` or
Java: it cannot read or write files, use the network, start other apps or see the person's data.
The console app itself holds no storage, network or other permissions for games to borrow.

Each call into the script may run about 300,000 Lua instructions. A game that goes past that, or
recurses too deeply, is stopped with a message ("The game took too long and was stopped."), and a
`pcall` in the script can't catch it. A Lua error stops the game with its message and line.

## A small example

```lua
-- Move a dot with tilts or arrows; reach the green square.
local x, y, moves

function init()
  sable.grid(8, 8)
  x, y, moves = 1, 1, 0
  sable.status("Reach the green square")
end

function tilt(step)
  local dx = (step == "right" and 1) or (step == "left" and -1) or 0
  local dy = (step == "down" and 1) or (step == "up" and -1) or 0
  x = math.max(0, math.min(7, x + dx))
  y = math.max(0, math.min(7, y + dy))
  moves = moves + 1
  sable.score(moves)
  if x == 6 and y == 6 then sable.over("Done in " .. moves .. " moves") end
end

function draw()
  sable.rect(0, 0, 8, 8, "floor")
  sable.rect(6, 6, 1, 1, "goal")
  sable.circle(x + 0.5, y + 0.5, 0.4, "player")
end
```

With `best = "low"` in its `game.toml`, the library keeps the fewest moves.

## Bundled games

`sable-src/apps/r8/android/games/src/console/assets/console/` holds the four games that come with
the console: `tilt_maze`, `motion_2048` and `sokoban_tilt` (ported from rustmix-wave, MIT) and
`tilt_racer`, whose F1 car pictures are EdSquare's "Top Down F1 Cars" (CC0). `BundledGamesTest` plays each through a long random session in the sandbox, and
every maze and Sokoban level was checked solvable.

## rustmix-wave games

rustmix-wave's `APP.TOM` and its Lua API differ from this one. Its `input = ["imu"]` is read as a
tilt game, but the script needs porting: rename the manifest to `game.toml` with the keys above,
and draw with the `sable` calls.
