#version 330
#extension GL_ARB_separate_shader_objects : require
#include <flare:schemas/blockengine_inspector/target.glsl>
#include <blockengine_inspector:text.glsl>

uniform sampler2D SceneSampler;
layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 SceneSize;
    vec2 FlareBusSize;
    vec2 FlareStatusSize;
};
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

const vec3 GRAPHITE = vec3(0x33, 0x39, 0x44) / 255.0;
const vec3 PEARL = vec3(0xf0, 0xee, 0xeb) / 255.0;
const vec3 GOLD = vec3(0xd9, 0xbc, 0x82) / 255.0;
const vec3 BLUE = vec3(0x89, 0xa7, 0xd2) / 255.0;
const vec3 VIOLET = vec3(0xad, 0x9b, 0xcb) / 255.0;
const vec3 SEA = vec3(0x8f, 0xbf, 0xc3) / 255.0;

int unsignedAt(uint value, int column, int width) {
    if (column < 0 || column >= width) return 32;
    int place = width - 1 - column;
    for (int index = 0; index < place; index++) value /= 10u;
    if (value == 0u && place > 0) return 32;
    return 48 + int(value % 10u);
}

int signedAt(int value, int column, int width) {
    uint magnitude = value < 0 ? 0u - uint(value) : uint(value);
    int digits = 1;
    for (uint rest = magnitude / 10u; rest > 0u; rest /= 10u) digits++;
    if (value < 0 && column == width - digits - 1) return 45;
    return unsignedAt(magnitude, column, width);
}

int fixedAt(float value, int column) {
    // Eight columns: sign/integer, decimal point, two fractional places.
    if (column < 0 || column >= 8) return 32;
    if (isnan(value) || isinf(value) || abs(value) > 9999.99) return 63;
    int hundredths = int(round(abs(value) * 100.0));
    if (column == 5) return 46;
    if (column >= 6) return 48 + (column == 6 ? hundredths / 10 : hundredths) % 10;
    int whole = hundredths / 100;
    if (value < 0.0 && whole == 0 && column == 3) return 45;
    return signedAt(value < 0.0 ? -whole : whole, column, 5);
}

int wordAt(int word, int column) {
    if (column < 0 || column >= 16) return 32;
    // VANILLA / CUSTOM / PLACEHOLDER / NO TARGET / YES / NO / N/A
    const int WORDS[112] = int[112](
        86,65,78,73,76,76,65,32,32,32,32,32,32,32,32,32,
        67,85,83,84,79,77,32,32,32,32,32,32,32,32,32,32,
        80,76,65,67,69,72,79,76,68,69,82,32,32,32,32,32,
        78,79,32,84,65,82,71,69,84,32,32,32,32,32,32,32,
        89,69,83,32,32,32,32,32,32,32,32,32,32,32,32,32,
        78,79,32,32,32,32,32,32,32,32,32,32,32,32,32,32,
        78,47,65,32,32,32,32,32,32,32,32,32,32,32,32,32);
    return WORDS[clamp(word, 0, 6) * 16 + column];
}

int characterAt(int row, int column) {
    if (row == 2 && column >= 10) return wordAt(getTarget() ? int(getKind()) : 3, column - 10);
    if (!getTarget() && row >= 3 && row <= 15) return 32;
    if (row == 3 && column >= 10 && column < 42) return int(getBlockId(column - 10));
    if (row == 4 && column >= 10 && column < 34) return int(getStateId(column - 10));
    if (row == 5 && column >= 10 && column < 34) return int(getMaterial(column - 10));
    if (row == 6 && column >= 10 && column < 26) return int(getWorld(column - 10));
    if (row >= 7 && row <= 9 && column >= 10 && column < 22) {
        int value = row == 7 ? getX() : row == 8 ? getY() : getZ();
        return signedAt(value, column - 10, 12);
    }
    if (row == 10 && column >= 8 && column < 19) return signedAt(getX() >> 4, column - 8, 11);
    if (row == 10 && column >= 23 && column < 34) return signedAt(getZ() >> 4, column - 23, 11);
    if (row == 11 && column >= 10 && column < 16) return unsignedAt(getDisplays(), column - 10, 6);
    if (row == 11 && column >= 29) return wordAt(getKind() == 1u ? (getExposed() ? 4 : 5) : 6, column - 29);
    if (row == 12 && column >= 16 && column < 27) return unsignedAt(getPayload(), column - 16, 11);
    if ((row == 13 || row == 14) && column >= 16 && column < 24) {
        if (getKind() != 1u) return wordAt(6, column - 16);
        return fixedAt(row == 13 ? getHardness() : getMiningSpeed(), column - 16);
    }
    if (row == 15 && column >= 16) return wordAt(getKind() == 1u ? (getUnbreakable() ? 4 : 5) : 6, column - 16);
    return label(row, column);
}

void main() {
    fragColor = texture(SceneSampler, texCoord);
    if (!flareReady() || !getVisible()) return;

    float scale = float(max(1, min(int(OutSize.x) / 600, int(OutSize.y) / 440)));
    ivec2 p = ivec2(floor(vec2(gl_FragCoord.x, OutSize.y - gl_FragCoord.y) / scale)) - ivec2(12);
    const ivec2 panel = ivec2(280, 187);
    if (any(lessThan(p, ivec2(0))) || any(greaterThanEqual(p, panel))) return;

    vec3 color = mix(fragColor.rgb, GRAPHITE * 0.48, 0.94);
    if (p.x == 0 || p.y == 0 || p.x == panel.x - 1 || p.y == panel.y - 1) {
        color = mix(VIOLET, BLUE, float(p.x) / float(panel.x));
    }
    if (p.y == 16 && p.x >= 8 && p.x < panel.x - 8) color = mix(VIOLET, BLUE, float(p.x) / float(panel.x));

    ivec2 text = p - ivec2(8, 8);
    if (all(greaterThanEqual(text, ivec2(0)))) {
        int row = text.y / 9;
        int column = text.x / 6;
        int code = characterAt(row, column);
        if (glyph(code, ivec2(text.x % 6, text.y % 9))) {
            color = row == 0 ? GOLD : row == 1 ? SEA : row >= 17 ? BLUE : column < 10 ? VIOLET : PEARL;
        }
    }

    // Preserve the alpha carrier; Flare's final present_view pass restores it for later consumers.
    fragColor.rgb = color;
}
