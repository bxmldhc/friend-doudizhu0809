import { parseCards } from "./cards.ts";
import { allPlays, canBeat, classify } from "./hand.ts";

Deno.test("classify recognizes the supported basic patterns", () => {
  const cases = [
    ["3H", "SINGLE"],
    ["3H 3S", "PAIR"],
    ["3H 3S 3D", "TRIPLE"],
    ["3H 3S 3D 4H", "TRIPLE_SINGLE"],
    ["3H 3S 3D 4H 4S", "TRIPLE_PAIR"],
    ["3H 4S 5D 6C 7H", "STRAIGHT"],
    ["3H 3S 4H 4S 5H 5S", "PAIR_STRAIGHT"],
    ["3H 3S 3D 4H 4S 4D", "AIRPLANE"],
    ["3H 3S 3D 4H 4S 4D 5H 6H", "AIRPLANE_SINGLE"],
    ["3H 3S 3D 4H 4S 4D 5H 5S 6H 6S", "AIRPLANE_PAIR"],
    ["7H 7S 7D 7C 3H 4H", "FOUR_TWO_SINGLE"],
    ["7H 7S 7D 7C 3H 3S 4H 4S", "FOUR_TWO_PAIR"],
    ["9H 9S 9D 9C", "BOMB"],
    ["X B", "ROCKET"],
  ] as const;

  for (const [notation, expected] of cases) {
    assertEquals(classify(cards(notation))?.type, expected, notation);
  }
});

Deno.test("sequence bodies reject rank two and jokers", () => {
  assertEquals(classify(cards("JH QS KD AC 2H")), null);
  assertEquals(classify(cards("QH QS KH KS AH AS 2H 2S")), null);
  assertEquals(
    classify(cards("KH KS KD AH AS AD 2H 2S 2D")),
    null,
  );
});

Deno.test("four cards need two distinct kickers or two distinct pairs", () => {
  assertEquals(classify(cards("7H 7S 7D 7C 3H")), null);
  assertEquals(classify(cards("7H 7S 7D 7C 3H 3S")), null);
});

Deno.test("canBeat enforces shape and bomb hierarchy", () => {
  const straight5 = classify(cards("3H 4S 5D 6C 7H"))!;
  const straight6 = classify(cards("4H 5S 6D 7C 8H 9S"))!;
  const pair7 = classify(cards("7H 7S"))!;
  const pair8 = classify(cards("8H 8S"))!;
  const bomb = classify(cards("3H 3S 3D 3C"))!;
  const rocket = classify(cards("X B"))!;

  assertEquals(canBeat(straight6, straight5), false);
  assertEquals(canBeat(pair8, pair7), true);
  assertEquals(canBeat(pair7, pair8), false);
  assertEquals(canBeat(bomb, straight5), true);
  assertEquals(canBeat(rocket, bomb), true);
  assertEquals(canBeat(bomb, rocket), false);
});

Deno.test("allPlays only emits valid patterns and includes all wing choices", () => {
  const hand = cards(
    "3H 3S 3D 4H 4S 4D 5H 5S 6H 6S 7H 8H 9H X B",
  );
  const plays = allPlays(hand);

  assertNotEquals(plays.length, 0);
  for (const play of plays) {
    assertNotEquals(classify(play), null, play.map((card) => card.r).join(","));
  }

  const airplaneSingles = plays.filter((play) =>
    classify(play)?.type === "AIRPLANE_SINGLE"
  );
  const airplanePairs = plays.filter((play) =>
    classify(play)?.type === "AIRPLANE_PAIR"
  );
  assertEquals(airplaneSingles.length, 21);
  assertEquals(airplanePairs.length, 1);
  assertEquals(
    airplaneSingles.some((play) => play.some((card) => card.r === 16)),
    true,
  );
  assertEquals(
    airplaneSingles.some((play) => play.some((card) => card.r === 17)),
    true,
  );
});

function cards(notation: string) {
  return parseCards(notation.split(/\s+/));
}

function assertEquals(actual: unknown, expected: unknown, label = "") {
  if (actual !== expected) {
    throw new Error(
      `${label ? `${label}: ` : ""}expected ${String(expected)}, got ${
        String(actual)
      }`,
    );
  }
}

function assertNotEquals(actual: unknown, expected: unknown, label = "") {
  if (actual === expected) {
    throw new Error(
      `${label ? `${label}: ` : ""}did not expect ${String(expected)}`,
    );
  }
}
