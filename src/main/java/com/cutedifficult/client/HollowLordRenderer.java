package com.cutedifficult.client;

import com.cutedifficult.entity.HollowLordEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Renders the Hollow Lord as a fallen monk: a humanoid (player-model) figure,
 * scaled to twice player size, in a custom void-themed skin. Unlike the dragon
 * model, the player/biped model isn't bound to a specific entity type, so it
 * renders cleanly on our FlyingEntity-based boss.
 *
 * <p>The skin texture lives at
 * {@code assets/cutedifficult/textures/entity/hollow_lord.png} — a 64×64 player
 * skin layout (Steve / wide-arms model). Supply your own art there.
 */
public class HollowLordRenderer extends MobEntityRenderer<HollowLordEntity, PlayerEntityModel<HollowLordEntity>> {

    private static final Identifier TEXTURE =
        Identifier.of("cutedifficult", "textures/entity/hollow_lord.png");
    private static final float SCALE = 2.0f;

    public HollowLordRenderer(EntityRendererFactory.Context ctx) {
        // false = use the wide-arms (Steve) player model layer.
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 1.0f);
    }

    @Override
    public Identifier getTexture(HollowLordEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(HollowLordEntity entity, MatrixStack matrices, float amount) {
        matrices.scale(SCALE, SCALE, SCALE);
    }
}
