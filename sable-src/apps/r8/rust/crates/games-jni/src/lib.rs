use jni::objects::{JIntArray, JObject, JString};
use jni::sys::{jboolean, jint, jlong, jlongArray, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use sable_games_core::{game2048, minesweeper, sudoku};

const SUDOKU_PREFIX: &str = "S1";
const MINESWEEPER_PREFIX: &str = "M1";

fn jni_string(env: &JNIEnv<'_>, value: String) -> jstring {
    match env.new_string(value) {
        Ok(value) => value.into_raw(),
        Err(_) => core::ptr::null_mut(),
    }
}

fn sudoku_error(error: sudoku::Error) -> String {
    match format!("{error:?}").as_str() {
        "InvalidPuzzle" => "ERR:INVALID_PUZZLE",
        "OutOfBounds" => "ERR:OUT_OF_BOUNDS",
        "InvalidValue" => "ERR:INVALID_VALUE",
        "FixedCell" => "ERR:FIXED_CELL",
        "Conflict" => "ERR:CONFLICT",
        _ => "ERR:SUDOKU",
    }
    .to_owned()
}

fn sudoku_start(puzzle: &str) -> String {
    match sudoku::Board::parse(puzzle) {
        Ok(_) => format!("OK:{SUDOKU_PREFIX}:{puzzle}:"),
        Err(error) => sudoku_error(error),
    }
}

fn decode_sudoku_state(state: &str) -> Result<(&str, &str), &'static str> {
    let mut fields = state.splitn(3, ':');

    if fields.next() != Some(SUDOKU_PREFIX) {
        return Err("ERR:INVALID_STATE");
    }

    let puzzle = fields.next().ok_or("ERR:INVALID_STATE")?;
    let operations = fields.next().ok_or("ERR:INVALID_STATE")?;

    Ok((puzzle, operations))
}

fn replay_sudoku(state: &str) -> Result<(sudoku::Board, String, String), &'static str> {
    let (puzzle, operations) = decode_sudoku_state(state)?;

    let mut board = sudoku::Board::parse(puzzle).map_err(|_| "ERR:INVALID_STATE")?;

    if !operations.is_empty() {
        for encoded in operations.split(';') {
            let mut fields = encoded.split(',');

            let row = fields
                .next()
                .and_then(|value| value.parse::<usize>().ok())
                .ok_or("ERR:INVALID_STATE")?;

            let col = fields
                .next()
                .and_then(|value| value.parse::<usize>().ok())
                .ok_or("ERR:INVALID_STATE")?;

            let value = fields
                .next()
                .and_then(|value| value.parse::<u8>().ok())
                .ok_or("ERR:INVALID_STATE")?;

            if fields.next().is_some() {
                return Err("ERR:INVALID_STATE");
            }

            board
                .set(row, col, value)
                .map_err(|_| "ERR:INVALID_STATE")?;
        }
    }

    Ok((board, puzzle.to_owned(), operations.to_owned()))
}

fn sudoku_board_string(board: &sudoku::Board) -> String {
    let mut out = String::with_capacity(81);

    for row in 0..9 {
        for col in 0..9 {
            let value = board.cell(row, col).unwrap_or(0);
            out.push(char::from(b'0' + value));
        }
    }

    out
}

fn sudoku_set_protocol(state: &str, row: i32, col: i32, value: i32) -> String {
    if row < 0 || col < 0 {
        return "ERR:OUT_OF_BOUNDS".to_owned();
    }

    if !(0..=9).contains(&value) {
        return "ERR:INVALID_VALUE".to_owned();
    }

    let Ok((mut board, puzzle, operations)) = replay_sudoku(state) else {
        return "ERR:INVALID_STATE".to_owned();
    };

    if let Err(error) = board.set(row as usize, col as usize, value as u8) {
        return sudoku_error(error);
    }

    let encoded = format!("{row},{col},{value}");

    let operations = if operations.is_empty() {
        encoded
    } else {
        format!("{operations};{encoded}")
    };

    format!("OK:{SUDOKU_PREFIX}:{puzzle}:{operations}")
}

fn sudoku_board_protocol(state: &str) -> String {
    match replay_sudoku(state) {
        Ok((board, _, _)) => format!("OK:{}", sudoku_board_string(&board)),
        Err(error) => error.to_owned(),
    }
}

fn sudoku_solved_protocol(state: &str) -> bool {
    replay_sudoku(state)
        .map(|(board, _, _)| board.solved())
        .unwrap_or(false)
}

#[derive(Clone, Debug)]
struct MineState {
    width: usize,
    height: usize,
    mines: usize,
    seed: u64,
    operations: String,
}

fn mine_error(error: &str) -> String {
    match error {
        "invalid board" => "ERR:INVALID_BOARD",
        "board too large" => "ERR:BOARD_TOO_LARGE",
        "out of bounds" => "ERR:OUT_OF_BOUNDS",
        _ => "ERR:MINESWEEPER",
    }
    .to_owned()
}

fn encode_mine_state(state: &MineState) -> String {
    format!(
        "{MINESWEEPER_PREFIX}:{}:{}:{}:{}:{}",
        state.width, state.height, state.mines, state.seed, state.operations
    )
}

fn decode_mine_state(encoded: &str) -> Result<MineState, &'static str> {
    let mut fields = encoded.splitn(6, ':');

    if fields.next() != Some(MINESWEEPER_PREFIX) {
        return Err("ERR:INVALID_STATE");
    }

    let width = fields
        .next()
        .and_then(|value| value.parse::<usize>().ok())
        .ok_or("ERR:INVALID_STATE")?;

    let height = fields
        .next()
        .and_then(|value| value.parse::<usize>().ok())
        .ok_or("ERR:INVALID_STATE")?;

    let mines = fields
        .next()
        .and_then(|value| value.parse::<usize>().ok())
        .ok_or("ERR:INVALID_STATE")?;

    let seed = fields
        .next()
        .and_then(|value| value.parse::<u64>().ok())
        .ok_or("ERR:INVALID_STATE")?;

    let operations = fields.next().ok_or("ERR:INVALID_STATE")?.to_owned();

    Ok(MineState {
        width,
        height,
        mines,
        seed,
        operations,
    })
}

fn mine_index(state: &MineState, x: usize, y: usize) -> Result<usize, &'static str> {
    if x >= state.width || y >= state.height {
        Err("ERR:OUT_OF_BOUNDS")
    } else {
        Ok(y * state.width + x)
    }
}

fn replay_minesweeper(encoded: &str) -> Result<(minesweeper::Board, MineState, Vec<char>), String> {
    let state = decode_mine_state(encoded).map_err(str::to_owned)?;

    let mut board = minesweeper::Board::new(state.width, state.height, state.mines, state.seed)
        .map_err(mine_error)?;

    let count = state
        .width
        .checked_mul(state.height)
        .ok_or_else(|| "ERR:BOARD_TOO_LARGE".to_owned())?;

    let mut view = vec!['H'; count];

    if !state.operations.is_empty() {
        for operation in state.operations.split(';') {
            let mut fields = operation.split(',');

            let kind = fields.next().ok_or("ERR:INVALID_STATE")?;

            let x = fields
                .next()
                .and_then(|value| value.parse::<usize>().ok())
                .ok_or("ERR:INVALID_STATE")?;

            let y = fields
                .next()
                .and_then(|value| value.parse::<usize>().ok())
                .ok_or("ERR:INVALID_STATE")?;

            if fields.next().is_some() {
                return Err("ERR:INVALID_STATE".to_owned());
            }

            let index = mine_index(&state, x, y).map_err(str::to_owned)?;

            match kind {
                "R" => {
                    let result = board.reveal(x, y).map_err(mine_error)?;

                    match result {
                        minesweeper::Reveal::Safe(neighbors) => {
                            view[index] = char::from(b'0' + neighbors);
                        }
                        minesweeper::Reveal::Mine => {
                            view[index] = '*';
                        }
                        minesweeper::Reveal::Ignored => {}
                    }
                }
                "F" => {
                    let result = board.toggle_flag(x, y).map_err(mine_error)?;

                    match result {
                        minesweeper::CellState::Hidden => view[index] = 'H',
                        minesweeper::CellState::Flagged => view[index] = 'F',
                        minesweeper::CellState::Revealed => {}
                    }
                }
                _ => return Err("ERR:INVALID_STATE".to_owned()),
            }
        }
    }

    Ok((board, state, view))
}

fn minesweeper_start(width: i32, height: i32, mine_count: i32, seed: i64) -> String {
    if width <= 0 || height <= 0 || mine_count < 0 {
        return "ERR:INVALID_BOARD".to_owned();
    }

    if seed < 0 {
        return "ERR:INVALID_SEED".to_owned();
    }

    let state = MineState {
        width: width as usize,
        height: height as usize,
        mines: mine_count as usize,
        seed: seed as u64,
        operations: String::new(),
    };

    match minesweeper::Board::new(state.width, state.height, state.mines, state.seed) {
        Ok(_) => format!("OK:{}", encode_mine_state(&state)),
        Err(error) => mine_error(error),
    }
}

fn append_mine_operation(state: &MineState, operation: String) -> MineState {
    let operations = if state.operations.is_empty() {
        operation
    } else {
        format!("{};{operation}", state.operations)
    };

    MineState {
        width: state.width,
        height: state.height,
        mines: state.mines,
        seed: state.seed,
        operations,
    }
}

fn minesweeper_reveal_protocol(state: &str, x: i32, y: i32) -> String {
    if x < 0 || y < 0 {
        return "ERR:OUT_OF_BOUNDS".to_owned();
    }

    let Ok((mut board, state, _)) = replay_minesweeper(state) else {
        return "ERR:INVALID_STATE".to_owned();
    };

    if mine_index(&state, x as usize, y as usize).is_err() {
        return "ERR:OUT_OF_BOUNDS".to_owned();
    }

    let result = match board.reveal(x as usize, y as usize) {
        Ok(result) => result,
        Err(error) => return mine_error(error),
    };

    let state = append_mine_operation(&state, format!("R,{x},{y}"));

    match result {
        minesweeper::Reveal::Safe(neighbors) => {
            format!("OK|{}|SAFE|{neighbors}", encode_mine_state(&state))
        }
        minesweeper::Reveal::Mine => {
            format!("OK|{}|MINE", encode_mine_state(&state))
        }
        minesweeper::Reveal::Ignored => {
            format!("OK|{}|IGNORED", encode_mine_state(&state))
        }
    }
}

fn minesweeper_flag_protocol(state: &str, x: i32, y: i32) -> String {
    if x < 0 || y < 0 {
        return "ERR:OUT_OF_BOUNDS".to_owned();
    }

    let Ok((mut board, state, _)) = replay_minesweeper(state) else {
        return "ERR:INVALID_STATE".to_owned();
    };

    if mine_index(&state, x as usize, y as usize).is_err() {
        return "ERR:OUT_OF_BOUNDS".to_owned();
    }

    let result = match board.toggle_flag(x as usize, y as usize) {
        Ok(result) => result,
        Err(error) => return mine_error(error),
    };

    let state = append_mine_operation(&state, format!("F,{x},{y}"));

    let label = match result {
        minesweeper::CellState::Hidden => "HIDDEN",
        minesweeper::CellState::Flagged => "FLAGGED",
        minesweeper::CellState::Revealed => "REVEALED",
    };

    format!("OK|{}|{label}", encode_mine_state(&state))
}

fn minesweeper_view_protocol(state: &str) -> String {
    match replay_minesweeper(state) {
        Ok((_, _, view)) => {
            format!("OK:{}", view.into_iter().collect::<String>())
        }
        Err(error) => error,
    }
}

fn direction_from_code(code: i32) -> Option<game2048::Direction> {
    match code {
        0 => Some(game2048::Direction::Left),
        1 => Some(game2048::Direction::Right),
        2 => Some(game2048::Direction::Up),
        3 => Some(game2048::Direction::Down),
        _ => None,
    }
}

fn game2048_protocol(values: [i32; 16], direction: i32) -> Option<([u32; 16], u32)> {
    let direction = direction_from_code(direction)?;

    if values.iter().any(|value| *value < 0) {
        return None;
    }

    let cells = values.map(|value| value as u32);

    let mut board = game2048::Board::new(cells);
    let score = board.slide(direction);

    Some((board.cells, score))
}

fn qualification() -> String {
    let puzzle = "0".repeat(81);

    let started = sudoku_start(&puzzle);
    let Some(state) = started.strip_prefix("OK:") else {
        return "FAIL:games-core:start".to_owned();
    };

    let first = sudoku_set_protocol(state, 0, 0, 5);
    let Some(state) = first.strip_prefix("OK:") else {
        return format!("FAIL:games-core:{first}");
    };

    match sudoku_set_protocol(state, 0, 1, 5).as_str() {
        "ERR:CONFLICT" => "PASS:games-core:sudoku-conflict".to_owned(),
        result => format!("FAIL:games-core:{result}"),
    }
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_org_sableos_games_NativeBridge_selfTest(
    env: JNIEnv<'_>,
    _this: JObject<'_>,
) -> jstring {
    jni_string(&env, qualification())
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_org_sableos_games_NativeBridge_sudokuStart(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    puzzle: JString<'_>,
) -> jstring {
    let puzzle: String = match env.get_string(&puzzle) {
        Ok(value) => value.into(),
        Err(_) => return core::ptr::null_mut(),
    };

    jni_string(&env, sudoku_start(&puzzle))
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_org_sableos_games_NativeBridge_sudokuSet(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    state: JString<'_>,
    row: jint,
    col: jint,
    value: jint,
) -> jstring {
    let state: String = match env.get_string(&state) {
        Ok(value) => value.into(),
        Err(_) => return core::ptr::null_mut(),
    };

    jni_string(&env, sudoku_set_protocol(&state, row, col, value))
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_org_sableos_games_NativeBridge_sudokuBoard(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    state: JString<'_>,
) -> jstring {
    let state: String = match env.get_string(&state) {
        Ok(value) => value.into(),
        Err(_) => return core::ptr::null_mut(),
    };

    jni_string(&env, sudoku_board_protocol(&state))
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_org_sableos_games_NativeBridge_sudokuSolved(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    state: JString<'_>,
) -> jboolean {
    let state: String = match env.get_string(&state) {
        Ok(value) => value.into(),
        Err(_) => return JNI_FALSE,
    };

    if sudoku_solved_protocol(&state) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_org_sableos_games_NativeBridge_minesweeperStart(
    env: JNIEnv<'_>,
    _this: JObject<'_>,
    width: jint,
    height: jint,
    mines: jint,
    seed: jlong,
) -> jstring {
    jni_string(&env, minesweeper_start(width, height, mines, seed))
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_org_sableos_games_NativeBridge_minesweeperReveal(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    state: JString<'_>,
    x: jint,
    y: jint,
) -> jstring {
    let state: String = match env.get_string(&state) {
        Ok(value) => value.into(),
        Err(_) => return core::ptr::null_mut(),
    };

    jni_string(&env, minesweeper_reveal_protocol(&state, x, y))
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_org_sableos_games_NativeBridge_minesweeperToggleFlag(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    state: JString<'_>,
    x: jint,
    y: jint,
) -> jstring {
    let state: String = match env.get_string(&state) {
        Ok(value) => value.into(),
        Err(_) => return core::ptr::null_mut(),
    };

    jni_string(&env, minesweeper_flag_protocol(&state, x, y))
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_org_sableos_games_NativeBridge_minesweeperView(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    state: JString<'_>,
) -> jstring {
    let state: String = match env.get_string(&state) {
        Ok(value) => value.into(),
        Err(_) => return core::ptr::null_mut(),
    };

    jni_string(&env, minesweeper_view_protocol(&state))
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_org_sableos_games_NativeBridge_game2048Slide(
    env: JNIEnv<'_>,
    _this: JObject<'_>,
    board: JIntArray<'_>,
    direction: jint,
) -> jlongArray {
    let length = match env.get_array_length(&board) {
        Ok(length) => length,
        Err(_) => return core::ptr::null_mut(),
    };

    if length != 16 {
        return core::ptr::null_mut();
    }

    let mut input = [0_i32; 16];

    if env.get_int_array_region(&board, 0, &mut input).is_err() {
        return core::ptr::null_mut();
    }

    let Some((cells, score)) = game2048_protocol(input, direction) else {
        return core::ptr::null_mut();
    };

    let output_array = match env.new_long_array(17) {
        Ok(array) => array,
        Err(_) => return core::ptr::null_mut(),
    };

    let mut output = [0_i64; 17];

    for (index, value) in cells.into_iter().enumerate() {
        output[index] = i64::from(value);
    }

    output[16] = i64::from(score);

    if env
        .set_long_array_region(&output_array, 0, &output)
        .is_err()
    {
        return core::ptr::null_mut();
    }

    output_array.into_raw()
}

#[cfg(test)]
mod tests {
    use super::{
        game2048_protocol, minesweeper_reveal_protocol, minesweeper_start,
        minesweeper_view_protocol, sudoku_board_protocol, sudoku_set_protocol, sudoku_start,
    };

    #[test]
    fn sudoku_state_replays_mutable_entries() {
        let puzzle = "0".repeat(81);

        let start = sudoku_start(&puzzle);
        let state = start.strip_prefix("OK:").expect("start");

        let first = sudoku_set_protocol(state, 0, 0, 5);
        let state = first.strip_prefix("OK:").expect("first");

        let second = sudoku_set_protocol(state, 0, 0, 4);
        let state = second.strip_prefix("OK:").expect("second");

        let board = sudoku_board_protocol(state);
        assert!(board.starts_with("OK:4"));
    }

    #[test]
    fn sudoku_conflict_maps_to_protocol() {
        let puzzle = "0".repeat(81);

        let start = sudoku_start(&puzzle);
        let state = start.strip_prefix("OK:").expect("start");

        let first = sudoku_set_protocol(state, 0, 0, 5);
        let state = first.strip_prefix("OK:").expect("first");

        assert_eq!(sudoku_set_protocol(state, 0, 1, 5), "ERR:CONFLICT",);
    }

    #[test]
    fn minesweeper_state_is_replayable() {
        let start = minesweeper_start(9, 9, 10, 1803);
        let state = start.strip_prefix("OK:").expect("start");

        let reveal = minesweeper_reveal_protocol(state, 4, 4);
        let fields: Vec<&str> = reveal.split('|').collect();

        assert!(fields.len() >= 3);
        assert_eq!(fields[0], "OK");

        let view = minesweeper_view_protocol(fields[1]);
        assert!(view.starts_with("OK:"));
        assert_eq!(view.trim_start_matches("OK:").len(), 81);
    }

    #[test]
    fn game_2048_protocol_returns_board_and_score() {
        let input = [2, 2, 4, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];

        let (cells, score) = game2048_protocol(input, 0).expect("slide");

        assert_eq!(&cells[0..4], &[4, 8, 0, 0]);
        assert_eq!(score, 12);
    }
}
