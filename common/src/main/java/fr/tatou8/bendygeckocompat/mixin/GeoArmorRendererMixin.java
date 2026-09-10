package fr.tatou8.bendygeckocompat.mixin;

import fr.tatou8.bendygeckocompat.GeckoLibCompat;
import fr.tatou8.bendygeckocompat.GeckoLibCompat.DynamicLimbOffsets;
import fr.tatou8.bendygeckocompat.GeckoLibCompat.LimbType;
import io.github.kosmx.bendylib.impl.BendableCuboid;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.util.RenderUtils;

@Mixin(value = GeoArmorRenderer.class, remap = false)
public abstract class GeoArmorRendererMixin<T extends GeoAnimatable> implements GeoRenderer<T> {

    @Shadow
    protected BipedEntityModel<?> baseModel;

    @Inject(method = "applyBaseTransformations", at = @At("RETURN"))
    private void applyBendyBaseTransformations(BipedEntityModel<?> baseModel, CallbackInfo ci) {
        GeckoLibCompat.applyTorsoBendToArmor((GeoArmorRenderer<?>) (Object) this, baseModel);
        if (GeckoLibCompat.DEBUG) {
            GeckoLibCompat.debugLogArmorState((GeoArmorRenderer<?>) (Object) this, baseModel);
        }
    }

    @Inject(method = "renderRecursively", at = @At("HEAD"), cancellable = true)
    private void bendyPreRenderRecursively(MatrixStack poseStack, net.minecraft.item.Item animatable, GeoBone bone, RenderLayer renderType, VertexConsumerProvider bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (GeckoLibCompat.isTopLevelUpperBodyLimb((GeoArmorRenderer<?>) (Object) this, bone)) {
            GeckoLibCompat.applyWaistRotation((GeoArmorRenderer<?>) (Object) this, this.baseModel, poseStack, true);
        }
        if (GeckoLibCompat.isTopLevelBootLimb((GeoArmorRenderer<?>) (Object) this, bone)) {
            ci.cancel();
            renderBootRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }

    @Inject(method = "renderRecursively", at = @At("RETURN"))
    private void bendyPostRenderRecursively(MatrixStack poseStack, net.minecraft.item.Item animatable, GeoBone bone, RenderLayer renderType, VertexConsumerProvider bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (GeckoLibCompat.isTopLevelUpperBodyLimb((GeoArmorRenderer<?>) (Object) this, bone)) {
            GeckoLibCompat.applyWaistRotation((GeoArmorRenderer<?>) (Object) this, this.baseModel, poseStack, false);
        }
    }

    private void renderBootRecursively(MatrixStack poseStack, net.minecraft.item.Item animatable, GeoBone bone, RenderLayer renderType, VertexConsumerProvider bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        poseStack.push();
        RenderUtils.prepMatrixForBone(poseStack, bone);

        LimbType limb = GeckoLibCompat.getLimbType((GeoArmorRenderer<?>) (Object) this, bone);
        ModelPart part = GeckoLibCompat.getModelPartForLimb(this.baseModel, limb);
        BendableCuboid bendable = GeckoLibCompat.getBendable(part);

        DynamicLimbOffsets offsets = (limb != null) ? GeckoLibCompat.getDynamicOffsets((GeoArmorRenderer<?>) (Object) this, bone, limb) : GeckoLibCompat.ZERO_OFFSETS;
        if (offsets.lateralOffset != 0 || offsets.forwardOffset != 0) {
            float latDir = (limb == LimbType.RIGHT_LEG) ? -1.0f : 1.0f;
            poseStack.translate(latDir * offsets.lateralOffset / 16.0f, 0, -offsets.forwardOffset / 16.0f);
        }

        if (bendable != null && (bendable.getBend() != 0 || bendable.getBendAxis() != 0)) {
            float kneeX = (limb == LimbType.RIGHT_LEG ? 1.9f : -1.9f) / 16.0f;
            float kneeY = 6.0f / 16.0f;
            float kneeZ = 0.0f;
            float axisf = -bendable.getBendAxis();
            Vector3f axis = new Vector3f((float) Math.cos(axisf), 0, (float) Math.sin(axisf));
            Quaternionf qKnee = new Quaternionf().rotateAxis(-bendable.getBend(), axis);

            poseStack.translate(kneeX, kneeY, kneeZ);
            poseStack.multiply(qKnee);
            poseStack.translate(-kneeX, -kneeY, -kneeZ);
        }

        renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);

        if (!isReRender) {
            applyRenderLayersForBone(poseStack, getAnimatable(), bone, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
        }

        renderChildBones(poseStack, (T) (Object) animatable, bone, renderType, bufferSource, buffer, false, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
        poseStack.pop();
    }

    @Override
    public void renderCubesOfBone(MatrixStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        if (bone.isHidden()) {
            return;
        }

        LimbType limb = GeckoLibCompat.getLimbType((GeoArmorRenderer<?>) (Object) this, bone);
        boolean isBoot = GeckoLibCompat.isBootLimb((GeoArmorRenderer<?>) (Object) this, bone);

        // If the bone belongs to an articulated limb (arms, legs, body) and has an active bend, deform its cubes.
        // Boot limbs are excluded from non-linear deformation because they are rigid and already have knee rotation applied on poseStack.
        if (limb != null && !isBoot) {
            ModelPart part = GeckoLibCompat.getModelPartForLimb(this.baseModel, limb);
            BendableCuboid bendable = GeckoLibCompat.getBendable(part);

            if (bendable != null && (bendable.getBend() != 0 || bendable.getBendAxis() != 0)) {
                GeckoLibCompat.renderBendableBoneCubes((GeoArmorRenderer<?>) (Object) this, poseStack, bone, limb, bendable, buffer,
                        packedLight, packedOverlay, red, green, blue, alpha);
                return;
            } else if (GeckoLibCompat.DEBUG && GeckoLibCompat.isLoggingActive()) {
                GeckoLibCompat.log("[BendyDebug] Bone '" + bone.getName() + "' matched limb " + limb + " but bendable is " + (bendable == null ? "null" : "bend=" + bendable.getBend()));
            }
        } else if (GeckoLibCompat.DEBUG && GeckoLibCompat.isLoggingActive() && !bone.getCubes().isEmpty()) {
            GeckoLibCompat.log("[BendyDebug] Bone '" + bone.getName() + "' (cubes=" + bone.getCubes().size() + ", limb=" + limb + ", isBoot=" + isBoot + ") rendering with standard renderCube");
        }

        DynamicLimbOffsets offsets = (limb != null) ? GeckoLibCompat.getDynamicOffsets((GeoArmorRenderer<?>) (Object) this, bone, limb) : GeckoLibCompat.ZERO_OFFSETS;
        boolean hasOffset = (offsets.lateralOffset != 0 || offsets.forwardOffset != 0);
        float latDir = (limb == LimbType.RIGHT_ARM || limb == LimbType.RIGHT_LEG) ? -1.0f : 1.0f;

        for (GeoCube cube : bone.getCubes()) {
            poseStack.push();
            if (hasOffset && !isBoot) {
                poseStack.translate(latDir * offsets.lateralOffset / 16.0f, 0, -offsets.forwardOffset / 16.0f);
            }
            renderCube(poseStack, cube, buffer, packedLight, packedOverlay, red, green, blue, alpha);
            poseStack.pop();
        }
    }
}
