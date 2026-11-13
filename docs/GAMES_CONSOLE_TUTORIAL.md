# Make your own game for Sable Games Console

This tutorial builds a small game, **Star Catch**, one step at a time and puts it on the phone. In
the game you lean the phone to move a basket and catch falling stars. Along the way it covers
everything a game can do: drawing, tilt and keys, scores, saving, pictures, see-through colours
and shapes. The last part lists what a game can't do, and the limits.

The reference for every call is [GAMES_CONSOLE.md](GAMES_CONSOLE.md). How to play and add games
is in the [user guide](USER_GUIDE.md#sable-games-console).

**What you need:** a text editor, and a way to copy a folder to the phone's Download folder (a USB
cable with file transfer, `adb push`, or a cloud drive and the Files app). You don't need Android
Studio, a computer build of SableOS, or any permission on the phone.

## 1. A game is a folder

Make a folder called `star_catch` with two files in it:

```
star_catch/
  game.toml     what the game is called and how it plays
  main.lua      the game itself
```

`game.toml` describes the game to the library:

```toml
id = "star_catch"
name = "Star Catch"
entry = "main.lua"
version = "1.0"
shelf = "motion"
input = ["tilt", "keys"]
best = "high"
summary = "Lean the phone to move the basket and catch the falling stars."
controls = "Tilt, or hold Left/Right, to move. Space starts again after the game ends."
```

- `id` is 1 to 32 of a-z, 0-9 and `_`. It names the game's saved data, so keep it the same when you
  update the game. It can't be the id of a game that comes with the console.
- `shelf` is the library tab it goes on: `motion`, `puzzle` or `keyboard`.
- `input = ["tilt", ...]` makes it a **tilt game**. Only tilt games read the phone's lean, show the
  TILT tag and get "Hold it how you'll play". Without `"tilt"` the game plays with keys only.
- `best = "high"` keeps the highest score; use `"low"` for times or move counts.

## 2. Draw something

The console calls functions you define in `main.lua`. `init()` runs when the game starts, and
`draw()` runs every frame. Put this in `main.lua`:

```lua
function init()
  sable.grid(12, 12)
  sable.status("Hello from Star Catch")
end

function draw()
  sable.rect(0, 0, 12, 12, "bg")
  sable.circle(6, 4, 1, "#F5C542")
  sable.text(6, 8, "Star Catch", "text", 1.2)
end
```

- The screen is a square **grid**. `sable.grid(12, 12)` makes it 12 cells across and 12 down, and
  every position and size is in cells (fractions are fine: `0.35`). (0, 0) is the top left.
- `draw()` draws the **whole** picture every time. Nothing stays from the frame before.
- Colours are theme names (`bg`, `text`, `dim`, `accent`, `wall`, `floor`, `player`, `goal`,
  `good`, `box`, `warn`), which follow the light or dark theme, or `"#rrggbb"`.
- `sable.status` sets the line above the game.

You can add this to the phone now to check it works (step 4), or carry on.

## 3. Make it move

`update(dt)` runs every frame before `draw()`. `dt` is the time since the last frame, in seconds,
so speeds are "cells per second" whatever the frame rate. `sable.tilt()` gives the lean, x and y
from -1 to 1. Replace `main.lua` with:

```lua
-- Star Catch: lean the phone to move the basket and catch the falling stars.
local W, H = 12, 12
local basket, stars, score, lives, wait

function init()
  sable.grid(W, H)
  basket = W / 2
  stars, score, lives, wait = {}, 0, 3, 0
  sable.score(0)
end

function update(dt)
  dt = math.min(dt, 0.05)                 -- a long frame (after a pause) mustn't jump ahead
  local lean = sable.tilt()               -- -1 leaning left .. 1 leaning right
  basket = math.max(1, math.min(W - 1, basket + lean * 10 * dt))

  wait = wait - dt
  if wait <= 0 then                       -- a new star every 0.8 seconds
    stars[#stars + 1] = { x = 0.5 + math.random() * (W - 1), y = -0.5 }
    wait = 0.8
  end

  local speed = 3 + score * 0.1           -- faster as you score
  for i = #stars, 1, -1 do                -- backwards, so removing is safe
    local s = stars[i]
    s.y = s.y + speed * dt
    if s.y > H - 1.4 and s.y < H - 0.6 and math.abs(s.x - basket) < 1.2 then
      table.remove(stars, i)
      score = score + 1
      sable.score(score)
    elseif s.y > H + 0.5 then
      table.remove(stars, i)
      lives = lives - 1
    end
  end
  if lives <= 0 then
    sable.over("You caught " .. score .. " stars · Space plays again")
  end
  sable.status(string.format("Stars %d · lives %s", score, string.rep("♥", lives)))
end

function draw()
  sable.rect(0, 0, W, H, "bg")
  for _, s in ipairs(stars) do
    sable.circle(s.x, s.y, 0.35, "#F5C542")
  end
  sable.rect(basket - 1, H - 1.2, 2, 0.8, "accent")
end
```

What's new here:

- **Keep your game's state in `local` variables** at the top of the file, and set them in
  `init()`. The console calls `init()` again for a new game, so everything starts fresh.
- `sable.score(n)` reports the score. The library keeps the best one for you.
- `sable.over(text)` ends the game with that message. `update` stops being called, and Space (or
  New game in the pause menu) calls `init()` again.
- `math.random()` works. There is no clock or date, so use `dt` to time things.
- With keys instead of tilt, holding Left/Right (or A/D) gives a lean of -1 or 1, so the same code
  plays both ways.

## 4. Put it on the phone

1. Copy the `star_catch` folder into a folder on the phone, for example `Download/games`:
   `adb push star_catch /sdcard/Download/games/star_catch`
2. Open **Sable Games Console** and press **A** (Add games).
3. In the folder picker, pick `Download/games` (or the `star_catch` folder itself) and allow it.
4. Each game found shows a row. A good one shows **Add**; press it, and the game is in the library.
   A problem shows on the row instead, with the file and line for a script error:
   `main.lua:12: '=' expected near 'end'`.
5. Pick Star Catch in the library and press Space.

**To update a game**, change the files and add the folder again. The new copy replaces the old
one, and the saved data and best score stay. **To remove it**, press I on it in the library and
choose Remove.

While playing: Back (or P in a tilt game) pauses, and the pause menu can start a new game,
re-level, change the tilt sensitivity or switch to keys only (K).

## 5. Keys, steps and saving

A game can also take single steps and other keys:

- `tilt(step)` gets `"left"`, `"right"`, `"up"` or `"down"`: one step per lean (the phone must come
  back near level before the next), and one per press of an arrow or the trackpad. Use it for
  games that move one square at a time, like a maze or 2048.
- `key(name)` gets `"space"`, `"enter"`, `"delete"`, and letters and digits in lower case. In a
  tilt game W A S D are arrows and P pauses, so they never reach `key`.

`sable.save(key, value)` and `sable.load(key)` keep small text for your game only, between games
and after the phone restarts. Values come back as **text**, so turn numbers back with `tonumber`.
Add a colour choice to Star Catch that is remembered:

```lua
local COLORS = { "#F5C542", "#7FD3FF", "#FF7A9A" }
local color = tonumber(sable.load("color") or "1") or 1

function key(name)
  if name == "c" then
    color = color % #COLORS + 1
    sable.save("color", tostring(color))
  end
end
```

and draw the stars with `COLORS[color]` instead of `"#F5C542"`. Put the two `local` lines near
the top of the file, before `draw`. `sable.save(key, nil)` removes a value.

## 6. Pictures

A game can bring up to 32 PNG pictures in its folder. Make a `basket.png` (128 by 64 pixels is
plenty; a transparent background works) and put it next to `main.lua`. Then draw the basket with:

```lua
sable.image(basket - 1, H - 1.4, 2, 1, "basket.png")
```

- The picture is stretched over the box: x, y, width and height in cells. Keep the box the same
  shape as the picture, or it looks squashed.
- An optional last number turns it clockwise, in degrees, about the box's centre:
  `sable.image(x, y, 1, 1, "star.png", angle)`.
- Names are 1 to 32 of a-z, 0-9 and `_`, then `.png`. A name that isn't in the folder stops the
  game with an error, so a typo shows up straight away.
- Each picture can be up to 512 by 512 pixels and 256 KB. Bigger isn't sharper on the Q25's
  square screen: a cell is about 60 pixels across at 12 by 12.
- There are no sprite sheets: draw each frame of an animation as its own picture.

## 7. Shapes and see-through colours

`sable.poly` fills a shape through any 3 to 64 corners, with sharp corners. Here is a five-pointed
star to replace the round one:

```lua
local function star(x, y, r, color)
  local points = {}
  for i = 0, 9 do
    local a = math.pi * i / 5 - math.pi / 2
    local d = (i % 2 == 0) and r or r * 0.45
    points[#points + 1] = x + math.cos(a) * d
    points[#points + 1] = y + math.sin(a) * d
  end
  sable.poly(points, color)
end
```

A colour with two more digits, `"#rrggbbaa"`, is see-through: `aa` goes from `00` (clear) to `ff`
(solid). A soft shadow under each star:

```lua
star(s.x + 0.1, s.y + 0.15, 0.45, "#00000033")
star(s.x, s.y, 0.45, COLORS[color])
```

The same trick darkens the screen behind a message: `sable.rect(0, 4, 12, 3, "#00000066")`, then
`sable.text(...)` on top. Things drawn later cover things drawn earlier, so draw from back to front.

## 8. When something goes wrong

- **A script error** stops the game and shows the message and line, for example
  `main.lua:31: attempt to index ? (a nil value)`, which usually means a variable was never set. Fix the file and add the folder again.
- **"The game took too long and was stopped."** means one call ran more than about 300,000 Lua
  instructions: usually a loop that never ends. `pcall` can't catch this.
- **Lua version:** the console runs Lua 5.2. It has no `//` (use `math.floor(a / b)`), no
  integer type and no `utf8` library. `math.atan2` and `goto` are there.
- **Nothing moves with tilt:** check that `input` in `game.toml` includes `"tilt"`, and use
  `sable.tilt()` inside `update`, not `init`.
- **Shapes missing:** a frame draws at most 4,000 shapes and stops drawing after that. Skip what is
  off screen, as Sky Roll and Ramp Rush do.

The games that come with the console are good examples to read:
`sable-src/apps/r8/android/games/src/console/assets/console/`. Tilt Maze and Motion 2048 are
short; Ball Maze, Sky Roll and Ramp Rush show pictures, physics and a 3D road.

## What a game can do

| Can | How |
| --- | --- |
| Draw rectangles, circles, filled shapes, text and its own pictures | `sable.rect`, `sable.circle`, `sable.poly`, `sable.text`, `sable.image` |
| Turn pictures, and use see-through colours | `sable.image(..., angle)`, `"#rrggbbaa"` |
| Read the lean of the phone smoothly, or as single steps | `sable.tilt()` in `update`, `tilt(step)` |
| Read keys: Space, Enter, Delete, letters and digits | `key(name)` |
| Run in real time, at the screen's frame rate | `update(dt)` |
| Keep a best score in the library | `sable.score(n)` and `best` in `game.toml` |
| End a game with a message; start over | `sable.over(text)`, then `init()` |
| Remember settings and progress | `sable.save`, `sable.load` |
| Use random numbers, maths, strings and tables | Lua's `math`, `string`, `table` and `bit32` |
| Follow the light or dark theme | theme colour names |

## What a game can't do

| Can't | Why |
| --- | --- |
| Read or write files, or see photos, contacts or other data | No `io`, `os` or Java in the sandbox, and the app has no storage permissions |
| Use the internet | The sandbox has no network calls, and the app holds no network permission |
| Play sound or music, or vibrate | Not part of the `sable` API |
| Use the touch screen | Games are played with tilt and keys |
| Load other Lua files or modules | One script per game; no `require`, `load` or `dofile` |
| Read the clock or date | No `os`; time things with `dt` |
| Draw lines, outlines or gradients directly | Use thin rectangles or `sable.poly`, and pictures for gradients |
| Crop a picture or use a sprite sheet | Each picture is drawn whole; use one file per frame |
| Choose fonts or text alignment | Text is centred, in the console's bold font |
| Read another game's saved data | Each game's `sable.save` store is its own |
| Run in the background | The game pauses when the app leaves the screen, and the tilt sensor stops |
| Use coroutines or `print` | Not in the sandbox; show values with `sable.status` while you test |
| Install, hide or run anything outside the console | A game is only `game.toml`, one `.lua` script and PNGs, copied into the console's own storage and run in the sandbox |
| Freeze or fill up the phone | Loops, runaway patterns and memory floods stop the game with a message |
| Change another game, or how its strings behave | Each game has its own tables and saves; the shared string metatable is hidden |

## Limits

| What | Limit |
| --- | --- |
| Pictures per game | 32 PNGs, each up to 512 by 512 pixels and 256 KB |
| Script and `game.toml` | 256 KB each |
| Shapes per frame | 4,000 |
| Corners in one `sable.poly` | 3 to 64 |
| Grid | 1 to 200 cells each way (default 10 by 10) |
| Text | 120 characters per `sable.text`, `sable.status` or `sable.over` |
| Lua work per call | about 300,000 instructions per `init`, `update`, `draw`, `tilt` or `key` |
| Pattern matching per call | about 3 million steps across `find`, `match`, `gmatch` and `gsub`; patterns up to 512 characters |
| `string.rep` | up to 1,000,000 characters |
| Memory | a game holding more than 60% of the app's memory is stopped |
| Saved data | 64 keys per game; a key up to 40 characters, a value up to 2,000 |
| `game.toml` | name up to 40 characters; summary and controls up to 200 |
| Add games | up to 50 game folders at a time |
