package net.tabor.seedcity.client;

import net.minecraft.client.model.animal.golem.IronGolemModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.resources.Identifier;
import net.tabor.seedcity.entity.WardenEntity;

/** Placeholder look for the Warden: the vanilla iron golem. Swap when the mob aesthetic is decided. */
public final class WardenRenderer extends MobRenderer<WardenEntity, IronGolemRenderState, IronGolemModel> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/iron_golem/iron_golem.png");

	public WardenRenderer(EntityRendererProvider.Context context) {
		super(context, new IronGolemModel(context.bakeLayer(ModelLayers.IRON_GOLEM)), 0.7F);
	}

	@Override
	public Identifier getTextureLocation(IronGolemRenderState state) {
		return TEXTURE;
	}

	@Override
	public IronGolemRenderState createRenderState() {
		return new IronGolemRenderState();
	}
}
