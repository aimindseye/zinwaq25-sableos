-- Tilt Racer for Sable Games Console: a top-down road. Lean the phone to steer, keep clear of the
-- traffic, and see how far you get; the road speeds up as you go. Space gives a short boost.
local W, H = 12, 12
local ROAD_LEFT, ROAD_RIGHT = 1.5, 10.5
local LANES = { 3, 6, 9 }
local CAR_W, CAR_H, CAR_Y = 1.4, 2.0, 9.4
local STEER = 9            -- cells a second at a full lean
local START_SPEED, TOP_SPEED, SPEED_UP = 6, 18, 0.12
local METRES_PER_CELL = 4
local COLORS = { "#E5493A", "#D9A92E", "#6B5BB5", "#3D7A8C", "#B5465B" }

local x, speed, distance, traffic, spawn_in, stripes, boost, cooldown, crashed

local function report()
  local km = distance * METRES_PER_CELL / 1000
  local text = string.format("%.2f km · %d km/h", km, math.floor(speed * 12))
  if boost > 0 then text = text .. " · boost"
  elseif cooldown <= 0 then text = text .. " · Space boosts" end
  sable.status(text)
  sable.score(math.floor(distance * METRES_PER_CELL))
end

function init()
  sable.grid(W, H)
  x, speed, distance, traffic = (W - CAR_W) / 2, START_SPEED, 0, {}
  spawn_in, stripes, boost, cooldown, crashed = 1.0, 0, 0, 0, false
  report()
end

local function spawn()
  local first = math.random(#LANES)
  traffic[#traffic + 1] = { lane = first, y = -CAR_H, color = COLORS[math.random(#COLORS)] }
  -- Now and then a second car, never in all three lanes, so there is always a way through.
  if speed > 9 and math.random() < 0.35 then
    local second = (first % #LANES) + 1
    traffic[#traffic + 1] = { lane = second, y = -CAR_H - 0.5, color = COLORS[math.random(#COLORS)] }
  end
end

local function hits(car)
  local cx = LANES[car.lane] - CAR_W / 2
  local m = 0.15
  return x + m < cx + CAR_W - m and x + CAR_W - m > cx + m and
    CAR_Y + m < car.y + CAR_H - m and CAR_Y + CAR_H - m > car.y + m
end

function update(dt)
  if crashed then return end
  dt = math.min(dt, 0.1)
  local lean = sable.tilt()
  x = math.max(ROAD_LEFT, math.min(ROAD_RIGHT - CAR_W, x + lean * STEER * dt))
  speed = math.min(TOP_SPEED, speed + SPEED_UP * dt)
  local now = speed * (boost > 0 and 1.5 or 1)
  boost = math.max(0, boost - dt)
  cooldown = math.max(0, cooldown - dt)
  distance = distance + now * dt
  stripes = (stripes + now * dt) % 2
  spawn_in = spawn_in - dt
  if spawn_in <= 0 then
    spawn()
    spawn_in = math.max(0.45, 2.2 - speed * 0.09) + math.random() * 0.4
  end
  local kept = {}
  for _, car in ipairs(traffic) do
    car.y = car.y + now * 0.55 * dt
    if hits(car) then
      crashed = true
      sable.over(string.format("Crash! %.2f km · Space drives again", distance * METRES_PER_CELL / 1000))
    end
    if car.y < H then kept[#kept + 1] = car end
  end
  traffic = kept
  report()
end

function key(name)
  if name == "space" and cooldown <= 0 and not crashed then
    boost, cooldown = 1.2, 4.0
  end
end

local function car(cx, cy, color)
  sable.rect(cx, cy, CAR_W, CAR_H, color)
  sable.rect(cx + 0.2, cy + 0.35, CAR_W - 0.4, 0.45, "#1C1C1C")
  sable.rect(cx + 0.2, cy + 1.3, CAR_W - 0.4, 0.35, "#1C1C1C")
end

function draw()
  sable.rect(0, 0, W, H, "#2F5B3A")
  sable.rect(ROAD_LEFT - 0.2, 0, ROAD_RIGHT - ROAD_LEFT + 0.4, H, "#3A3A3A")
  sable.rect(ROAD_LEFT - 0.2, 0, 0.15, H, "#F2F2F2")
  sable.rect(ROAD_RIGHT + 0.05, 0, 0.15, H, "#F2F2F2")
  for _, divider in ipairs({ 4.5, 7.5 }) do
    for i = -1, H do
      sable.rect(divider - 0.06, i * 2 + stripes, 0.12, 1, "#BFBFBF")
    end
  end
  for _, c in ipairs(traffic) do car(LANES[c.lane] - CAR_W / 2, c.y, c.color) end
  car(x, CAR_Y, crashed and "warn" or "accent")
end
