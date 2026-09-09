package fr.tatou8.bendygeckocompat;

import fr.tatou8.bendygeckocompat.GeckoLibCompat.LimbType;
import io.github.kosmx.bendylib.impl.BendableCuboid;
import io.github.kosmx.bendylib.impl.IPosWithOrigin;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.function.Consumer;

public interface IBendableCuboidRenderer {
    void bendy$renderBent(Matrix4f topPose, Matrix3f topNormal, Matrix4f childRelMatrix,
                          LimbType limb, BendableCuboid bendable, Consumer<IPosWithOrigin> transform,
                          VertexConsumer buffer, int light, int overlay,
                          float red, float green, float blue, float alpha);

    float bendy$getMinX();
    float bendy$getMaxX();
    float bendy$getMinZ();
    float bendy$getMaxZ();
}
