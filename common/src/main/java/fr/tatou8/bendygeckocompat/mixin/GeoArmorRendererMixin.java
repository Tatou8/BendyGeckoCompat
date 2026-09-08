package fr.tatou8.bendygeckocompat.mixin;

import fr.tatou8.bendygeckocompat.GeckoLibCompat;
import fr.tatou8.bendygeckocompat.GeckoLibCompat.LimbType;
import io.github.kosmx.bendylib.impl.BendableCuboid;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
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

    @Inject(method = "renderRecursively", at = @At("HEAD"))
    private void bendyPreRenderRecursively(MatrixStack poseStack, net.minecraft.item.Item animatable, GeoBone bone, RenderLayer renderType, VertexConsumerProvider bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (GeckoLibCompat.isTopLevelUpperBodyLimb((GeoArmorRenderer<?>) (Object) this, bone)) {
            GeckoLibCompat.applyWaistRotation((GeoArmorRenderer<?>) (Object) this, this.baseModel, poseStack, true);
        }
    }

    @Inject(method = "renderRecursively", at = @At("RETURN"))
    private void bendyPostRenderRecursively(MatrixStack poseStack, net.minecraft.item.Item animatable, GeoBone bone, RenderLayer renderType, VertexConsumerProvider bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (GeckoLibCompat.isTopLevelUpperBodyLimb((GeoArmorRenderer<?>) (Object) this, bone)) {
            GeckoLibCompat.applyWaistRotation((GeoArmorRenderer<?>) (Object) this, this.baseModel, poseStack, false);
        }
    }

    @Override
    public void renderCubesOfBone(MatrixStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        if (bone.isHidden()) {
            return;
        }

        LimbType limb = GeckoLibCompat.getLimbType((GeoArmorRenderer<?>) (Object) this, bone);
        // If the bone belongs to an articulated limb (arms, legs, body) and has an active bend, deform its cubes.
        if (limb != null) {
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
            GeckoLibCompat.log("[BendyDebug] Bone '" + bone.getName() + "' (cubes=" + bone.getCubes().size() + ", limb=" + limb + ") rendering with standard renderCube");
        }

        for (GeoCube cube : bone.getCubes()) {
            poseStack.push();
            renderCube(poseStack, cube, buffer, packedLight, packedOverlay, red, green, blue, alpha);
            poseStack.pop();
        }
    }
}
