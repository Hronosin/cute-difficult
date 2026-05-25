package com.cutedifficult.network;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.Element;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;

/**
 * Per-player snapshot of spirit state for the HUD overlay.
 *
 * <p>Sent server → client at a low rate (once per second). Carries:
 * <ul>
 *   <li>All 9 element spirit values</li>
 *   <li>Karma value</li>
 *   <li>Whether the Great Blessing of Inari is currently active</li>
 * </ul>
 *
 * <p>Payload format: 9 ints (element order matches Element.values()),
 * one int karma, one byte (0 or 1) for great-blessing flag. ~42 bytes
 * per packet. Negligible bandwidth even at 1/sec for many players.
 */
public record SpiritOverlayPayload(
    Map<Element, Integer> spirits,
    int karma,
    boolean greatBlessing
) implements CustomPayload {

    public static final Identifier ID = Identifier.of(CuteDifficult.MOD_ID, "spirit_overlay");
    public static final CustomPayload.Id<SpiritOverlayPayload> PAYLOAD_ID = new CustomPayload.Id<>(ID);

    public static final PacketCodec<PacketByteBuf, SpiritOverlayPayload> CODEC = PacketCodec.of(
        SpiritOverlayPayload::write,
        SpiritOverlayPayload::read
    );

    private static void write(SpiritOverlayPayload p, PacketByteBuf buf) {
        for (Element e : Element.values()) {
            buf.writeVarInt(p.spirits.getOrDefault(e, 0));
        }
        buf.writeVarInt(p.karma);
        buf.writeBoolean(p.greatBlessing);
    }

    private static SpiritOverlayPayload read(PacketByteBuf buf) {
        Map<Element, Integer> map = new EnumMap<>(Element.class);
        for (Element e : Element.values()) {
            map.put(e, buf.readVarInt());
        }
        int karma = buf.readVarInt();
        boolean great = buf.readBoolean();
        return new SpiritOverlayPayload(map, karma, great);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return PAYLOAD_ID;
    }
}
