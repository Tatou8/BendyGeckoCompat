package fr.tatou8.bendygeckocompat.mixin;

import fr.tatou8.bendygeckocompat.CustomModelPartCompat;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArmorFeatureRenderer.class)
public abstract class HumanoidArmorLayerMixin<T extends LivingEntity, M extends BipedEntityModel<T>, A extends BipedEntityModel<T>> extends FeatureRenderer<T, M> {

    public HumanoidArmorLayerMixin(FeatureRendererContext<T, M> context) {
        super(context);
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V", at = @At("HEAD"))
    private void bendy$onRenderHead(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, LivingEntity entity, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        CustomModelPartCompat.pushContext(this.getContextModel(), entity);
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V", at = @At("RETURN"))
    private void bendy$onRenderReturn(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, LivingEntity entity, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        CustomModelPartCompat.popContext();
    }

    @Inject(method = "renderArmor", at = @At("HEAD"))
    private void bendy$onRenderArmorSlot(MatrixStack matrices, VertexConsumerProvider vertexConsumers, T entity, EquipmentSlot armorSlot, int light, A model, CallbackInfo ci) {
        CustomModelPartCompat.setCurrentArmorModel(model);
    }

    @Inject(method = "renderArmorParts", at = @At("HEAD"))
    private void bendy$onRenderArmorParts(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, ArmorItem item, A model, boolean glint, float red, float green, float blue, String overlay, CallbackInfo ci) {
        CustomModelPartCompat.setCurrentArmorModel(model);
    }

    @Inject(method = "getArmorModelHook", at = @At("RETURN"), remap = false, require = 0)
    private void bendy$onGetArmorModelHook(T entity, ItemStack itemStack, EquipmentSlot slot, A defaultModel, CallbackInfoReturnable<Model> cir) {
        Model customModel = cir.getReturnValue();
        if (customModel != null) {
            CustomModelPartCompat.setCurrentArmorModel(customModel);
        }
    }

    @Inject(method = "renderModel", at = @At("HEAD"), remap = false, require = 0)
    private void bendy$onRenderModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, ArmorItem item, Model model, boolean glint, float red, float green, float blue, Identifier texture, CallbackInfo ci) {
        if (model != null) {
            CustomModelPartCompat.setCurrentArmorModel(model);
        }
    }
}
