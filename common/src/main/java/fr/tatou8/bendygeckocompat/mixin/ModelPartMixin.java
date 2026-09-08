package fr.tatou8.bendygeckocompat.mixin;

import fr.tatou8.bendygeckocompat.CustomModelPartCompat;
import fr.tatou8.bendygeckocompat.GeckoLibCompat.LimbType;
import io.github.kosmx.bendylib.impl.BendableCuboid;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.class)
public abstract class ModelPartMixin {

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("HEAD"), cancellable = true)
    private void bendy$onModelPartRender(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        CustomModelPartCompat.RenderingContext ctx = CustomModelPartCompat.getCurrentContext();
        if (ctx == null || !ctx.hasAnyBend) {
            return;
        }

        ModelPart self = (ModelPart) (Object) this;
        LimbType limb = ctx.getLimbType(self);
        if (limb != null) {
            BendableCuboid bendable = ctx.getBendableForLimb(limb);
            if (bendable != null && (bendable.getBend() != 0 || bendable.getBendAxis() != 0)) {
                CustomModelPartCompat.renderBendableModelPart(self, matrices, vertices, light, overlay, red, green, blue, alpha, limb, bendable);
                ci.cancel();
            }
        }
    }
}
