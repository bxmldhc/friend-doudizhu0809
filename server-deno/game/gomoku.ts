export const GOMOKU_BOARD_SIZE = 15;

export type GomokuPhase = "PLAYING" | "FINISHED";
export type GomokuCell = 0 | 1 | 2;

export interface GomokuPoint {
  row: number;
  col: number;
}

export interface GomokuState {
  phase: GomokuPhase;
  board: GomokuCell[][];
  turnIndex: number;
  winnerIndex: number | null;
  winningLine: GomokuPoint[];
  moveCount: number;
  gameNumber: number;
  message: string;
}

export class GomokuEngine {
  state: GomokuState = {
    phase: "FINISHED",
    board: emptyBoard(),
    turnIndex: 0,
    winnerIndex: null,
    winningLine: [],
    moveCount: 0,
    gameNumber: 0,
    message: "",
  };

  startNewGame() {
    const gameNumber = this.state.gameNumber + 1;
    // Alternate the first black stone between the two players each round.
    const turnIndex = (gameNumber - 1) % 2;
    this.state = {
      phase: "PLAYING",
      board: emptyBoard(),
      turnIndex,
      winnerIndex: null,
      winningLine: [],
      moveCount: 0,
      gameNumber,
      message: `Player ${turnIndex + 1} to move`,
    };
  }

  submitMove(seat: number, row: number, col: number) {
    if (this.state.phase !== "PLAYING") {
      throw new Error("The Gomoku round is not in progress");
    }
    if (seat !== this.state.turnIndex) {
      throw new Error("It is not your turn");
    }
    if (!isBoardPosition(row, col)) {
      throw new Error("Move is outside the board");
    }
    if (this.state.board[row][col] !== 0) {
      throw new Error("This position is already occupied");
    }

    const stone = (seat + 1) as GomokuCell;
    const board = this.state.board.map((line) => [...line]);
    board[row][col] = stone;
    const moveCount = this.state.moveCount + 1;
    const winningLine = findWinningLine(board, row, col, stone);
    if (winningLine.length > 0) {
      this.state = {
        ...this.state,
        board,
        moveCount,
        winnerIndex: seat,
        winningLine,
        phase: "FINISHED",
        message: `Player ${seat + 1} wins`,
      };
      return;
    }
    if (moveCount === GOMOKU_BOARD_SIZE * GOMOKU_BOARD_SIZE) {
      this.state = {
        ...this.state,
        board,
        moveCount,
        winnerIndex: null,
        winningLine: [],
        phase: "FINISHED",
        message: "Draw",
      };
      return;
    }

    const turnIndex = nextSeat(seat);
    this.state = {
      ...this.state,
      board,
      moveCount,
      turnIndex,
      message: `Player ${turnIndex + 1} to move`,
    };
  }
}

export function isBoardPosition(row: number, col: number): boolean {
  return Number.isInteger(row) && Number.isInteger(col) &&
    row >= 0 && row < GOMOKU_BOARD_SIZE &&
    col >= 0 && col < GOMOKU_BOARD_SIZE;
}

function emptyBoard(): GomokuCell[][] {
  return Array.from(
    { length: GOMOKU_BOARD_SIZE },
    () => Array<GomokuCell>(GOMOKU_BOARD_SIZE).fill(0),
  );
}

function nextSeat(seat: number): number {
  return seat === 0 ? 1 : 0;
}

function findWinningLine(
  board: GomokuCell[][],
  row: number,
  col: number,
  stone: GomokuCell,
): GomokuPoint[] {
  const directions = [
    [0, 1],
    [1, 0],
    [1, 1],
    [1, -1],
  ];
  for (const [rowStep, colStep] of directions) {
    const before = collect(board, row, col, -rowStep, -colStep, stone);
    const after = collect(board, row, col, rowStep, colStep, stone);
    const line = [...before.reverse(), { row, col }, ...after];
    if (line.length >= 5) {
      const movedIndex = before.length;
      const start = Math.max(
        0,
        Math.min(movedIndex - 4, line.length - 5),
      );
      return line.slice(start, start + 5);
    }
  }
  return [];
}

function collect(
  board: GomokuCell[][],
  startRow: number,
  startCol: number,
  rowStep: number,
  colStep: number,
  stone: GomokuCell,
): GomokuPoint[] {
  const points: GomokuPoint[] = [];
  let row = startRow + rowStep;
  let col = startCol + colStep;
  while (
    isBoardPosition(row, col) &&
    board[row][col] === stone
  ) {
    points.push({ row, col });
    row += rowStep;
    col += colStep;
  }
  return points;
}
