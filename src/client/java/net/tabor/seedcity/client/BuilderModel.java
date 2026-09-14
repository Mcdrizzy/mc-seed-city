package net.tabor.seedcity.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;

/** Compact stone/copper worker. Flight remains the existing Builder navigation. */
public final class BuilderModel extends EntityModel<BuilderRenderState> {
    private final ModelPart body, head, rightArm, leftArm, rightLeg, leftLeg, cargo, blueprint;

    public BuilderModel(ModelPart root) {
        super(root);
        body = root.getChild("body");
        head = body.getChild("head");
        rightArm = body.getChild("right_arm");
        leftArm = body.getChild("left_arm");
        rightLeg = body.getChild("right_leg");
        leftLeg = body.getChild("left_leg");
        cargo = rightArm.getChild("cargo");
        blueprint = leftArm.getChild("blueprint");
    }

    @Override
    public void setupAnim(BuilderRenderState state) {
        super.setupAnim(state);
        float t = state.ageInTicks;
        float bob = (float) Math.sin(t * 0.10F);
        float flight = Math.min(state.flightSpeed * 5.0F, 1.0F);
        body.y += bob * 0.22F;
        body.xRot = flight * 0.08F;
        head.yRot = state.yRot * ((float) Math.PI / 180F);
        head.xRot = Math.max(-0.45F, Math.min(0.6F, state.xRot * ((float) Math.PI / 180F)));
        rightLeg.xRot = 0.08F + flight * 0.22F + bob * 0.04F;
        leftLeg.xRot = 0.08F + flight * 0.22F - bob * 0.04F;
        rightArm.zRot = 0.04F;
        leftArm.zRot = -0.05F;
        leftArm.xRot = -0.12F;
        rightArm.xRot = state.carrying ? -0.32F : 0.03F + bob * 0.035F;
        cargo.visible = state.carrying;
        blueprint.yRot = -0.16F;
        if (state.building) {
            rightArm.xRot = -0.55F + (float) Math.sin(t * 0.65F) * 0.28F;
            head.xRot = Math.max(head.xRot, 0.12F);
            leftArm.xRot = -0.25F;
        }
    }
}
