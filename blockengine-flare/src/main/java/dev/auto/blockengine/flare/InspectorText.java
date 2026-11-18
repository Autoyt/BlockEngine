package dev.auto.blockengine.flare;

/** Bounded, zero-padded display text. Transport getters remain ordinary typed GLSL methods. */
final class InspectorText {
    private InspectorText() { }

    static byte[] encode(String text, int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Text capacity must be positive");
        byte[] result = new byte[capacity];
        if (text == null) return result;

        int[] characters = text.codePoints().toArray();
        for (int index = 0; index < Math.min(capacity, characters.length); index++) {
            int character = Character.toUpperCase(characters[index]);
            result[index] = (byte) (character >= 32 && character <= 126 ? character : '?');
        }
        if (characters.length > capacity) result[capacity - 1] = '~';
        return result;
    }
}
