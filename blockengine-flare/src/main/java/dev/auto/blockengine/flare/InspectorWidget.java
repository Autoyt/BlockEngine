package dev.auto.blockengine.flare;

import dev.auto.flare.api.widget.Widget;
import dev.auto.flare.api.widget.WidgetBounds;
import dev.auto.flare.api.widget.WidgetLimits;

/** Keeps the pixel font and the diagnostic columns proportional while resizing. */
final class InspectorWidget implements Widget {
    static final String ID = "inspector";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public WidgetBounds defaultBounds() {
        return new WidgetBounds(1, 1, 10, 4);
    }

    @Override
    public WidgetLimits limits() {
        return new WidgetLimits(8, 3, 24, 10);
    }

    @Override
    public boolean lockAspectRatio() {
        return true;
    }
}
