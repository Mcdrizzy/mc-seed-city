package net.tabor.seedcity.client;

import net.minecraft.client.model.animal.allay.AllayModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.AllayRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.tabor.seedcity.entity.BuilderEntity;

/**
 * Placeholder look for the Builder: the vanilla allay model and texture. The mob aesthetic
 * (mechanical vs organic) is an open question in the design doc; swap the model here when it
 * is decided.
 */
public final class BuilderRenderer extends MobRenderer<BuilderEntity, AllayRenderState, AllayModel> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/allay/allay.png");

	public BuilderRenderer(EntityRendererProvider.Context context) {
		super(context, new AllayModel(context.bakeLayer(ModelLayers.ALLAY)), 0.4F);
	}

	@Override
	public Identifier getTextureLocation(AllayRenderState state) {
		return TEXTURE;
	}

	@Override
	public AllayRenderState createRenderState() {
		return new AllayRenderState();
	}

	@Override
	public void extractRenderState(BuilderEntity entity, AllayRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.isDancing = false;
		state.isSpinning = false;
		state.spinningProgress = 0;
		state.holdingAnimationProgress = 0;
	}

	@Override
	protected int getBlockLightLevel(BuilderEntity entity, BlockPos pos) {
		return 15;
	}
}
