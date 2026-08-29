import { GomokuCell, GomokuState, GOMOKU_BOARD_SIZE } from "./gomoku.ts";

export interface GomokuAiMove {
  row: number;
  col: number;
}

interface Candidate extends GomokuAiMove {
  score: number;
}

export function chooseGomokuAiMove(
  state: GomokuState,
  aiSeat: number,
): GomokuAiMove | null {
  const ownStone = (aiSeat + 1) as GomokuCell;
  const opponentStone = (aiSeat === 0 ? 2 : 1) as GomokuCell;
  const candidates = collectCandidates(state.board);
  if (candidates.length === 0) return null;

  const winningMove = candidates.find((point) =>
    longestLine(state.board, point.row, point.col, ownStone) >= 5
  );
  if (winningMove) return winningMove;

  const blockingMove = candidates.find((point) =>
    longestLine(state.board, point.row, point.col, opponentStone) >= 5
  );
  if (blockingMove) return blockingMove;

  const ranked = candidates
    .map((point): Candidate => ({
      ...point,
      score:
        scorePosition(state.board, point.row, point.col, ownStone) +
        scorePosition(state.board, point.row, point.col, opponentStone) * 0.82 +
        centerWeight(point.row, point.col),
    }))
    .sort((left, right) => right.score - left.score);
  const bestScore = ranked[0]?.score;
  const best = ranked.filter((point) => point.score === bestScore);
  return best[Math.floor(Math.random() * best.length)] ?? null;
}

function collectCandidates(board: GomokuCell[][]): GomokuAiMove[] {
  const occupied: GomokuAiMove[] = [];
  board.forEach((line, row) => line.forEach((cell, col) => {
    if (cell !== 0) occupied.push({ row, col });
  }));
  if (occupied.length === 0) return [{ row: 7, col: 7 }];

  const candidates = new Map<string, GomokuAiMove>();
  for (const point of occupied) {
    for (let row = point.row - 2; row <= point.row + 2; row++) {
      for (let col = point.col - 2; col <= point.col + 2; col++) {
        if (
          row >= 0 && row < GOMOKU_BOARD_SIZE &&
          col >= 0 && col < GOMOKU_BOARD_SIZE &&
          board[row][col] === 0
        ) {
          candidates.set(`${row}:${col}`, { row, col });
        }
      }
    }
  }
  return [...candidates.values()];
}

function scorePosition(
  board: GomokuCell[][],
  row: number,
  col: number,
  stone: GomokuCell,
): number {
  return [[0, 1], [1, 0], [1, 1], [1, -1]].reduce(
    (score, [rowStep, colStep]) => {
      const forward = count(board, row, col, rowStep, colStep, stone);
      const backward = count(board, row, col, -rowStep, -colStep, stone);
      const length = 1 + forward.count + backward.count;
      const openEnds = Number(forward.open) + Number(backward.open);
      const lineScore = length >= 4
        ? 10_000
        : length === 3 && openEnds === 2
        ? 1_200
        : length === 3
        ? 280
        : length === 2 && openEnds === 2
        ? 90
        : length === 2
        ? 24
        : openEnds * 3;
      return score + lineScore;
    },
    0,
  );
}

function longestLine(
  board: GomokuCell[][],
  row: number,
  col: number,
  stone: GomokuCell,
): number {
  return Math.max(
    ...[[0, 1], [1, 0], [1, 1], [1, -1]].map(([rowStep, colStep]) =>
      1 +
      count(board, row, col, rowStep, colStep, stone).count +
      count(board, row, col, -rowStep, -colStep, stone).count
    ),
  );
}

function count(
  board: GomokuCell[][],
  row: number,
  col: number,
  rowStep: number,
  colStep: number,
  stone: GomokuCell,
): { count: number; open: boolean } {
  let nextRow = row + rowStep;
  let nextCol = col + colStep;
  let total = 0;
  while (
    nextRow >= 0 && nextRow < GOMOKU_BOARD_SIZE &&
    nextCol >= 0 && nextCol < GOMOKU_BOARD_SIZE &&
    board[nextRow][nextCol] === stone
  ) {
    total += 1;
    nextRow += rowStep;
    nextCol += colStep;
  }
  return {
    count: total,
    open:
      nextRow >= 0 && nextRow < GOMOKU_BOARD_SIZE &&
      nextCol >= 0 && nextCol < GOMOKU_BOARD_SIZE &&
      board[nextRow][nextCol] === 0,
  };
}

function centerWeight(row: number, col: number): number {
  return 14 - (Math.abs(row - 7) + Math.abs(col - 7));
}
