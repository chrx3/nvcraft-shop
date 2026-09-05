package cl.nvcraft.lootshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Convierte texto con codigos '&' en Components de Adventure. */
public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Text() {}

    public static Component of(String raw) {
        // La cursiva por defecto de los lore se ve mal; la apagamos explicitamente.
        return LEGACY.deserialize(raw == null ? "" : raw).decoration(TextDecoration.ITALIC, false);
    }
}
