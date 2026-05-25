package com.cutedifficult.network;

import com.cutedifficult.CuteDifficult;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;

/**
 * Server → client packet carrying the bestiary entries for the receiving
 * player. Sent when they open the bestiary; the client renders the GUI
 * with this data.
 *
 * <p>Format: a single varint count followed by N UTF-8 strings (each
 * "ELEMENT:tier" key). Compact, ~10-20 bytes for typical progress.
 */
public record BestiaryOpenPayload(Set<String> entries) implements CustomPayload {

    public static final Identifier ID = Identifier.of(CuteDifficult.MOD_ID, "bestiary_open");
    public static final CustomPayload.Id<BestiaryOpenPayload> PAYLOAD_ID = new CustomPayload.Id<>(ID);

    public static final PacketCodec<PacketByteBuf, BestiaryOpenPayload> CODEC = PacketCodec.of(
        BestiaryOpenPayload::write,
        BestiaryOpenPayload::read
    );

    private static void write(BestiaryOpenPayload payload, PacketByteBuf buf) {
        buf.writeVarInt(payload.entries.size());
        for (String key : payload.entries) {
            buf.writeString(key, 64);
        }
    }

    private static BestiaryOpenPayload read(PacketByteBuf buf) {
        int n = buf.readVarInt();
        Set<String> set = new HashSet<>();
        for (int i = 0; i < n; i++) {
            set.add(buf.readString(64));
        }
        return new BestiaryOpenPayload(set);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return PAYLOAD_ID;
    }
}
