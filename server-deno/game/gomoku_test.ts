import { GomokuEngine, GOMOKU_BOARD_SIZE } from "./gomoku.ts";

function expectThrows(action: () => void, expected: string) {
  try {
    action();
  } catch (error) {
    if (String(error).includes(expected)) return;
    throw error;
  }
  throw new Error(`Expected error containing: ${expected}`);
}

Deno.test("Gomoku server engine validates turns and occupied positions", () => {
  const engine = new GomokuEngine();
  engine.startNewGame();
  const firstSeat = engine.state.turnIndex;
  const secondSeat = firstSeat === 0 ? 1 : 0;

  expectThrows(
    () => engine.submitMove(secondSeat, 7, 7),
    "not your turn",
  );
  engine.submitMove(firstSeat, 7, 7);
  expectThrows(
    () => engine.submitMove(secondSeat, 7, 7),
    "already occupied",
  );
  expectThrows(
    () => engine.submitMove(secondSeat, -1, 7),
    "outside the board",
  );
  if (engine.state.board[7][7] !== firstSeat + 1) {
    throw new Error("The accepted move was not stored authoritatively");
  }
});

Deno.test("Gomoku server engine confirms exactly one five-in-a-row winner", () => {
  const engine = new GomokuEngine();
  engine.startNewGame();
  const winner = engine.state.turnIndex;
  const other = winner === 0 ? 1 : 0;

  for (let col = 0; col < 4; col++) {
    engine.submitMove(winner, 7, col);
    engine.submitMove(other, 8, col);
  }
  engine.submitMove(winner, 7, 4);

  if (
    engine.state.phase !== "FINISHED" ||
    engine.state.winnerIndex !== winner ||
    engine.state.winningLine.length !== 5
  ) {
    throw new Error("The engine did not finish with the authoritative winner");
  }
  expectThrows(
    () => engine.submitMove(other, 8, 4),
    "not in progress",
  );
});

Deno.test("Gomoku board remains fixed at 15 by 15", () => {
  const engine = new GomokuEngine();
  engine.startNewGame();
  if (
    engine.state.board.length !== GOMOKU_BOARD_SIZE ||
    engine.state.board.some((row) => row.length !== GOMOKU_BOARD_SIZE)
  ) {
    throw new Error("Unexpected Gomoku board dimensions");
  }
});
