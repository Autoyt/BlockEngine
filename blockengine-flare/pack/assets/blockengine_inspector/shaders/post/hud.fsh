#version 330
#extension GL_ARB_separate_shader_objects : require
#include <flare:schemas/blockengine_inspector/target.glsl>
#include <flare:schemas/blockengine_inspector/widget_inspector/scoped.glsl>
#include <flare:widget_bounds.glsl>
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

const vec3 GRAPHITE = vec3(0x19, 0x1c, 0x25) / 255.0;
const vec3 PEARL = vec3(0xf0, 0xee, 0xeb) / 255.0;
const vec3 GOLD = vec3(0xd9, 0xbc, 0x82) / 255.0;
const vec3 BLUE = vec3(0x89, 0xa7, 0xd2) / 255.0;
const vec3 VIOLET = vec3(0xad, 0x9b, 0xcb) / 255.0;
const vec3 SEA = vec3(0x8f, 0xbf, 0xc3) / 255.0;
const ivec2 GLYPH_CELL = ivec2(6, 9);
const ivec2 PADDING = ivec2(8);
const ivec2 DESIGN_SIZE = ivec2(TEXT_COLUMNS, TEXT_ROWS) * GLYPH_CELL + 2 * PADDING;

const uint VANILLA = 0u;
const uint CUSTOM = 1u;
const int NO_TARGET = 3;
const int YES = 4;
const int NO = 5;
const int NOT_APPLICABLE = 6;
const int LOOK_AT_BLOCK = 7;

int unsignedAt(uint value, int column, int width) {
    if (column < 0 || column >= width) return 32;
    int place = width - 1 - column;
    for (int index = 0; index < place; index++) value /= 10u;
    if (value == 0u && place > 0) return 32;
    return 48 + int(value % 10u);
}

int signedAt(int value, int column, int width, bool rightAligned) {
    uint magnitude = value < 0 ? 0u - uint(value) : uint(value);
    int digits = 1;
    for (uint rest = magnitude / 10u; rest > 0u; rest /= 10u) digits++;

    int sign = value < 0 ? 1 : 0;
    int start = rightAligned ? width - digits - sign : 0;
    int position = column - start;
    if (sign == 1 && position == 0) return 45;
    return unsignedAt(magnitude, position - sign, digits);
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
    return signedAt(value < 0.0 ? -whole : whole, column, 5, true);
}

int wordAt(int word, int column) {
    if (column < 0 || column >= 16) return 32;
    // VANILLA / CUSTOM / PLACEHOLDER / NO TARGET / YES / NO / N/A / LOOK AT A BLOCK
    const int WORDS[128] = int[128](
        86,65,78,73,76,76,65,32,32,32,32,32,32,32,32,32,
        67,85,83,84,79,77,32,32,32,32,32,32,32,32,32,32,
        80,76,65,67,69,72,79,76,68,69,82,32,32,32,32,32,
        78,79,32,84,65,82,71,69,84,32,32,32,32,32,32,32,
        89,69,83,32,32,32,32,32,32,32,32,32,32,32,32,32,
        78,79,32,32,32,32,32,32,32,32,32,32,32,32,32,32,
        78,47,65,32,32,32,32,32,32,32,32,32,32,32,32,32,
        76,79,79,75,32,65,84,32,65,32,66,76,79,67,75,32);
    return WORDS[clamp(word, 0, LOOK_AT_BLOCK) * 16 + column];
}

// An empty character in a value field remains blank. Only -1 falls back to a static label.
int valueAt(int row, int column, uint kind, bool hasTarget) {
    if (row == 0) return column >= 31 ? wordAt(hasTarget ? int(kind) : NO_TARGET, column - 31) : -1;
    if (!hasTarget) return row == 1 ? wordAt(LOOK_AT_BLOCK, column) : 32;

    if (row == 1 && column >= 8 && column < 40) return int(getBlockId(column - 8));
    if (row == 2 && column >= 8 && column < 32) {
        return kind == VANILLA ? wordAt(NOT_APPLICABLE, column - 8) : int(getStateId(column - 8));
    }
    if (row == 3 && column >= 8 && column < 32) return int(getMaterial(column - 8));
    if (row == 4 && column >= 8 && column < 24) return int(getWorld(column - 8));

    if (row == 5) {
        int coordinate = column / 14;
        int value = coordinate == 0 ? getX() : coordinate == 1 ? getY() : getZ();
        int digit = column % 14 - 2;
        if (digit >= 0 && digit < 11) return signedAt(value, digit, 11, false);
    }
    if (row == 6) {
        if (column >= 8 && column < 18) return signedAt(getX() >> 4, column - 8, 10, false);
        if (column >= 21 && column < 31) return signedAt(getZ() >> 4, column - 21, 10, false);
        if (column >= 37 && column < 39) return unsignedAt(uint(getX() & 15), column - 37, 2);
        if (column == 39) return 47;
        if (column >= 40) return unsignedAt(uint(getZ() & 15), column - 40, 2);
    }

    bool custom = kind == CUSTOM;
    if (row == 7) {
        if (column >= 9 && column < 14) {
            return custom ? unsignedAt(getDisplays(), column - 9, 5) : wordAt(NOT_APPLICABLE, column - 9);
        }
        if (column >= 31 && column < 41) {
            return custom ? unsignedAt(getPayload(), column - 31, 10) : wordAt(NOT_APPLICABLE, column - 31);
        }
    }
    if (row == 8) {
        if (column >= 9 && column < 17) return custom ? fixedAt(getHardness(), column - 9) : wordAt(NOT_APPLICABLE, column - 9);
        if (column >= 33 && column < 41) return custom ? fixedAt(getMiningSpeed(), column - 33) : wordAt(NOT_APPLICABLE, column - 33);
    }
    if (row == 9) {
        if (column >= 9 && column < 12) return wordAt(custom ? (getExposed() ? YES : NO) : NOT_APPLICABLE, column - 9);
        if (column >= 33 && column < 36) return wordAt(custom ? (getUnbreakable() ? YES : NO) : NOT_APPLICABLE, column - 33);
    }
    return -1;
}

void main() {
    fragColor = texture(SceneSampler, texCoord);
    if (!flareReady() || !getVisible()) return;

    vec2 pixel = vec2(gl_FragCoord.x, OutSize.y - gl_FragCoord.y);
    vec2 point = flareWidgetCanvasPoint(pixel, OutSize);
    vec2 topLeft = flareWidgetPoint(blockengine_inspector_widget_inspector_getTopLeft());
    vec2 bottomRight = flareWidgetPoint(blockengine_inspector_widget_inspector_getBottomRight());
    if (!flareWidgetContains(point, topLeft, bottomRight)) return;

    // Fit the text in the actual rectangle without stretching glyphs when placement rounds to the grid.
    float cell = flareWidgetCanvasCell(OutSize);
    vec2 panel = (bottomRight - topLeft) * cell;
    vec2 local = (point - topLeft) * cell;
    vec2 fit = panel / vec2(DESIGN_SIZE);
    float scale = min(fit.x, fit.y);
    vec2 inset = (panel - vec2(DESIGN_SIZE) * scale) * 0.5;
    ivec2 design = ivec2(floor((local - inset) / scale));

    bool hasTarget = getTarget();
    vec3 color = mix(fragColor.rgb, GRAPHITE, 0.94);
    if (design.y < 16) color = mix(color, BLUE, 0.14);
    if (hasTarget && (design.y == 16 || design.y == 52 || design.y == 70)) {
        color = mix(color, BLUE, 0.32);
    }
    vec2 edge = min(local, panel - local);
    if (min(edge.x, edge.y) < max(1.0, floor(scale))) {
        color = mix(VIOLET, BLUE, local.x / panel.x);
    }

    ivec2 text = design - PADDING;
    ivec2 textSize = ivec2(TEXT_COLUMNS, TEXT_ROWS) * GLYPH_CELL;
    if (all(greaterThanEqual(text, ivec2(0))) && all(lessThan(text, textSize))) {
        int row = text.y / GLYPH_CELL.y;
        int column = text.x / GLYPH_CELL.x;
        int value = valueAt(row, column, getKind(), hasTarget);
        int code = value < 0 ? label(row, column) : value;
        if (glyph(code, text % GLYPH_CELL)) {
            color = row == 0 ? (value < 0 ? GOLD : SEA) : (value < 0 ? VIOLET : PEARL);
        }
    }

    // Preserve the alpha carrier; Flare's final present_view pass restores it for later consumers.
    fragColor.rgb = color;
}
