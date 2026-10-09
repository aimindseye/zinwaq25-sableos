//! Portable game domains for Sable Games, adapted from Rustmix Wave concepts.

pub mod sudoku {
    #[derive(Clone, Debug, Eq, PartialEq)]
    pub struct Board {
        cells: [u8; 81],
        fixed: [bool; 81],
    }

    #[derive(Clone, Copy, Debug, Eq, PartialEq)]
    pub enum Error {
        InvalidPuzzle,
        FixedCell,
        Conflict,
        OutOfRange,
    }

    impl Board {
        pub fn parse(puzzle: &str) -> Result<Self, Error> {
            if puzzle.len() != 81 || !puzzle.bytes().all(|byte| byte.is_ascii_digit()) {
                return Err(Error::InvalidPuzzle);
            }
            let mut cells = [0_u8; 81];
            let mut fixed = [false; 81];
            for (index, byte) in puzzle.bytes().enumerate() {
                let value = byte - b'0';
                cells[index] = value;
                fixed[index] = value != 0;
            }
            let board = Self { cells, fixed };
            if !board.is_consistent() {
                return Err(Error::InvalidPuzzle);
            }
            Ok(board)
        }

        pub const fn cell(&self, row: usize, col: usize) -> Option<u8> {
            if row < 9 && col < 9 {
                Some(self.cells[row * 9 + col])
            } else {
                None
            }
        }

        pub fn set(&mut self, row: usize, col: usize, value: u8) -> Result<(), Error> {
            if row >= 9 || col >= 9 || value > 9 {
                return Err(Error::OutOfRange);
            }
            let index = row * 9 + col;
            if self.fixed[index] {
                return Err(Error::FixedCell);
            }
            let old = self.cells[index];
            self.cells[index] = value;
            if !self.is_consistent() {
                self.cells[index] = old;
                return Err(Error::Conflict);
            }
            Ok(())
        }

        pub fn solved(&self) -> bool {
            self.cells.iter().all(|&value| value != 0) && self.is_consistent()
        }

        fn is_consistent(&self) -> bool {
            for row in 0..9 {
                if !valid_group((0..9).map(|col| self.cells[row * 9 + col])) {
                    return false;
                }
            }
            for col in 0..9 {
                if !valid_group((0..9).map(|row| self.cells[row * 9 + col])) {
                    return false;
                }
            }
            for box_row in 0..3 {
                for box_col in 0..3 {
                    let values = (0..9).map(|offset| {
                        let row = box_row * 3 + offset / 3;
                        let col = box_col * 3 + offset % 3;
                        self.cells[row * 9 + col]
                    });
                    if !valid_group(values) {
                        return false;
                    }
                }
            }
            true
        }
    }

    fn valid_group(values: impl Iterator<Item = u8>) -> bool {
        let mut seen = [false; 10];
        for value in values {
            if value == 0 {
                continue;
            }
            if value > 9 || seen[value as usize] {
                return false;
            }
            seen[value as usize] = true;
        }
        true
    }
}

pub mod minesweeper {
    #[derive(Clone, Copy, Debug, Eq, PartialEq)]
    pub enum CellState {
        Hidden,
        Revealed,
        Flagged,
    }

    #[derive(Clone, Copy, Debug, Eq, PartialEq)]
    pub enum Reveal {
        Safe(u8),
        Mine,
        Ignored,
    }

    pub struct Board {
        width: usize,
        height: usize,
        mines: Vec<bool>,
        state: Vec<CellState>,
    }

    impl Board {
        pub fn new(
            width: usize,
            height: usize,
            mine_count: usize,
            seed: u64,
        ) -> Result<Self, &'static str> {
            let count = width.checked_mul(height).ok_or("board too large")?;
            if width == 0 || height == 0 || mine_count >= count {
                return Err("invalid board");
            }
            let mut mines = vec![false; count];
            let mut placed = 0;
            let mut rng = seed.max(1);
            while placed < mine_count {
                rng ^= rng << 13;
                rng ^= rng >> 7;
                rng ^= rng << 17;
                let index = (rng as usize) % count;
                if !mines[index] {
                    mines[index] = true;
                    placed += 1;
                }
            }
            Ok(Self {
                width,
                height,
                mines,
                state: vec![CellState::Hidden; count],
            })
        }

        pub fn toggle_flag(&mut self, x: usize, y: usize) -> Result<CellState, &'static str> {
            let index = self.index(x, y)?;
            self.state[index] = match self.state[index] {
                CellState::Hidden => CellState::Flagged,
                CellState::Flagged => CellState::Hidden,
                CellState::Revealed => CellState::Revealed,
            };
            Ok(self.state[index])
        }

        pub fn reveal(&mut self, x: usize, y: usize) -> Result<Reveal, &'static str> {
            let index = self.index(x, y)?;
            if self.state[index] != CellState::Hidden {
                return Ok(Reveal::Ignored);
            }
            self.state[index] = CellState::Revealed;
            if self.mines[index] {
                return Ok(Reveal::Mine);
            }
            Ok(Reveal::Safe(self.neighbor_mines(x, y)))
        }

        fn index(&self, x: usize, y: usize) -> Result<usize, &'static str> {
            if x >= self.width || y >= self.height {
                Err("out of bounds")
            } else {
                Ok(y * self.width + x)
            }
        }

        fn neighbor_mines(&self, x: usize, y: usize) -> u8 {
            let mut count = 0;
            for dy in -1_i32..=1 {
                for dx in -1_i32..=1 {
                    if dx == 0 && dy == 0 {
                        continue;
                    }
                    let nx = x as i32 + dx;
                    let ny = y as i32 + dy;
                    if nx >= 0
                        && ny >= 0
                        && (nx as usize) < self.width
                        && (ny as usize) < self.height
                        && self.mines[ny as usize * self.width + nx as usize]
                    {
                        count += 1;
                    }
                }
            }
            count
        }
    }
}

pub mod game2048 {
    #[derive(Clone, Copy, Debug, Eq, PartialEq)]
    pub enum Direction {
        Left,
        Right,
        Up,
        Down,
    }

    #[derive(Clone, Copy, Debug, Eq, PartialEq)]
    pub struct Board {
        pub cells: [u32; 16],
    }

    impl Board {
        pub const fn new(cells: [u32; 16]) -> Self {
            Self { cells }
        }

        pub fn slide(&mut self, direction: Direction) -> u32 {
            let mut score = 0;
            for index in 0..4 {
                let mut line = self.read_line(direction, index);
                score += merge_line(&mut line);
                self.write_line(direction, index, line);
            }
            score
        }

        fn read_line(&self, direction: Direction, index: usize) -> [u32; 4] {
            let mut out = [0; 4];
            for (position, slot) in out.iter_mut().enumerate() {
                let (row, col) = coords(direction, index, position);
                *slot = self.cells[row * 4 + col];
            }
            out
        }

        fn write_line(&mut self, direction: Direction, index: usize, line: [u32; 4]) {
            for (position, value) in line.into_iter().enumerate() {
                let (row, col) = coords(direction, index, position);
                self.cells[row * 4 + col] = value;
            }
        }
    }

    fn coords(direction: Direction, index: usize, position: usize) -> (usize, usize) {
        match direction {
            Direction::Left => (index, position),
            Direction::Right => (index, 3 - position),
            Direction::Up => (position, index),
            Direction::Down => (3 - position, index),
        }
    }

    fn merge_line(line: &mut [u32; 4]) -> u32 {
        let compact: Vec<u32> = line.iter().copied().filter(|&value| value != 0).collect();
        let mut out = [0; 4];
        let mut score = 0;
        let mut src = 0;
        let mut dst = 0;
        while src < compact.len() {
            if src + 1 < compact.len() && compact[src] == compact[src + 1] {
                let merged = compact[src] * 2;
                out[dst] = merged;
                score += merged;
                src += 2;
            } else {
                out[dst] = compact[src];
                src += 1;
            }
            dst += 1;
        }
        *line = out;
        score
    }
}

#[cfg(test)]
mod tests {
    use super::{game2048, minesweeper, sudoku};

    #[test]
    fn sudoku_rejects_conflicts_and_fixed_edits() {
        let mut board = sudoku::Board::parse(
            "530070000600195000098000060800060003400803001700020006060000280000419005000080079",
        )
        .unwrap();
        assert_eq!(board.set(0, 0, 4), Err(sudoku::Error::FixedCell));
        assert_eq!(board.set(0, 2, 5), Err(sudoku::Error::Conflict));
        assert_eq!(board.set(0, 2, 4), Ok(()));
    }

    #[test]
    fn minesweeper_is_deterministic_for_seed() {
        let mut first = minesweeper::Board::new(9, 9, 10, 1803).unwrap();
        let mut second = minesweeper::Board::new(9, 9, 10, 1803).unwrap();
        assert_eq!(first.reveal(4, 4), second.reveal(4, 4));
    }

    #[test]
    fn game_2048_merges_once_per_move() {
        let mut board = game2048::Board::new([2, 2, 4, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]);
        assert_eq!(board.slide(game2048::Direction::Left), 12);
        assert_eq!(&board.cells[0..4], &[4, 8, 0, 0]);
    }
}
