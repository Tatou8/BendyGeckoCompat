package fr.tatou8.bendygeckocompat.mixin;

import fr.tatou8.bendygeckocompat.CustomModelPartCompat;
import fr.tatou8.bendygeckocompat.GeckoLibCompat.LimbType;
import fr.tatou8.bendygeckocompat.IBendableCuboidRenderer;
import io.github.kosmx.bendylib.impl.BendableCuboid;
import io.github.kosmx.bendylib.impl.IPosWithOrigin;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Consumer;

@Mixin(ModelPart.Cuboid.class)
public abstract class ModelPartCuboidMixin implements IBendableCuboidRenderer {

    @Shadow
    @Final
    private ModelPart.Quad[] sides;

    @Override
    public void bendy$renderBent(Matrix4f topPose, Matrix3f topNormal, Matrix4f childRelMatrix,
                                  LimbType limb, BendableCuboid bendable, Consumer<IPosWithOrigin> transform,
                                  VertexConsumer buffer, int light, int overlay,
                                  float red, float green, float blue, float alpha) {
        if (this.sides == null || this.sides.length == 0) {
            return;
        }

        for (ModelPart.Quad quad : this.sides) {
            if (quad == null || quad.vertices == null || quad.vertices.length != 4) {
                continue;
            }

            ModelPart.Vertex v0 = quad.vertices[0];
            ModelPart.Vertex v1 = quad.vertices[1];
            ModelPart.Vertex v2 = quad.vertices[2];
            ModelPart.Vertex v3 = quad.vertices[3];

            // Transform vertices from local cuboid space to top limb pixel space
            Vector4f p0 = childRelMatrix.transform(new Vector4f(v0.pos.x(), v0.pos.y(), v0.pos.z(), 1.0f));
            Vector4f p1 = childRelMatrix.transform(new Vector4f(v1.pos.x(), v1.pos.y(), v1.pos.z(), 1.0f));
            Vector4f p2 = childRelMatrix.transform(new Vector4f(v2.pos.x(), v2.pos.y(), v2.pos.z(), 1.0f));
            Vector4f p3 = childRelMatrix.transform(new Vector4f(v3.pos.x(), v3.pos.y(), v3.pos.z(), 1.0f));

            float dY03 = Math.abs(p3.y - p0.y);
            float dY01 = Math.abs(p1.y - p0.y);

            // If the quad has almost zero vertical span (e.g. top/bottom caps), render it directly without slicing
            if (dY03 < 0.001f && dY01 < 0.001f) {
                Vector3f raw0 = new Vector3f(p0.x, p0.y, p0.z);
                Vector3f raw1 = new Vector3f(p1.x, p1.y, p1.z);
                Vector3f raw2 = new Vector3f(p2.x, p2.y, p2.z);
                Vector3f raw3 = new Vector3f(p3.x, p3.y, p3.z);
                CustomModelPartCompat.renderBentQuad(raw0, v0.u, v0.v, raw1, v1.u, v1.v, raw2, v2.u, v2.v, raw3, v3.u, v3.v,
                        limb, bendable, transform, childRelMatrix, quad.direction,
                        topPose, topNormal, buffer, light, overlay, red, green, blue, alpha);
                continue;
            }

            boolean v0v3Vertical = dY03 >= dY01;
            float py0 = p0.y;
            float py1 = v0v3Vertical ? p3.y : p1.y;

            var intervals = CustomModelPartCompat.getSliceIntervals(py0, py1, limb);

            for (float[] interval : intervals) {
                float t0 = interval[0];
                float t1 = interval[1];

                Vector3f raw0, raw1, raw2, raw3;
                float u0, u1, u2, u3;
                float texV0, texV1, texV2, texV3;

                if (v0v3Vertical) {
                    // Standard vanilla Quad: Edge 0 is v0 -> v3 (Left), Edge 1 is v1 -> v2 (Right)
                    // Top edge of slice is lerp at t0: raw0 = lerp(p0, p3, t0), raw1 = lerp(p1, p2, t0)
                    // Bottom edge of slice is lerp at t1: raw3 = lerp(p0, p3, t1), raw2 = lerp(p1, p2, t1)
                    // Order raw0, raw1, raw2, raw3 preserves exact vanilla winding order (v0, v1, v2, v3)
                    raw0 = CustomModelPartCompat.lerpVec(p0, p3, t0);
                    u0 = CustomModelPartCompat.lerp(v0.u, v3.u, t0);
                    texV0 = CustomModelPartCompat.lerp(v0.v, v3.v, t0);

                    raw1 = CustomModelPartCompat.lerpVec(p1, p2, t0);
                    u1 = CustomModelPartCompat.lerp(v1.u, v2.u, t0);
                    texV1 = CustomModelPartCompat.lerp(v1.v, v2.v, t0);

                    raw2 = CustomModelPartCompat.lerpVec(p1, p2, t1);
                    u2 = CustomModelPartCompat.lerp(v1.u, v2.u, t1);
                    texV2 = CustomModelPartCompat.lerp(v1.v, v2.v, t1);

                    raw3 = CustomModelPartCompat.lerpVec(p0, p3, t1);
                    u3 = CustomModelPartCompat.lerp(v0.u, v3.u, t1);
                    texV3 = CustomModelPartCompat.lerp(v0.v, v3.v, t1);
                } else {
                    // Rotated quad where v0 -> v1 is vertical
                    raw0 = CustomModelPartCompat.lerpVec(p0, p1, t0);
                    u0 = CustomModelPartCompat.lerp(v0.u, v1.u, t0);
                    texV0 = CustomModelPartCompat.lerp(v0.v, v1.v, t0);

                    raw1 = CustomModelPartCompat.lerpVec(p0, p1, t1);
                    u1 = CustomModelPartCompat.lerp(v0.u, v1.u, t1);
                    texV1 = CustomModelPartCompat.lerp(v0.v, v1.v, t1);

                    raw2 = CustomModelPartCompat.lerpVec(p3, p2, t1);
                    u2 = CustomModelPartCompat.lerp(v3.u, v2.u, t1);
                    texV2 = CustomModelPartCompat.lerp(v3.v, v2.v, t1);

                    raw3 = CustomModelPartCompat.lerpVec(p3, p2, t0);
                    u3 = CustomModelPartCompat.lerp(v3.u, v2.u, t0);
                    texV3 = CustomModelPartCompat.lerp(v3.v, v2.v, t0);
                }

                CustomModelPartCompat.renderBentQuad(raw0, u0, texV0, raw1, u1, texV1, raw2, u2, texV2, raw3, u3, texV3,
                        limb, bendable, transform, childRelMatrix, quad.direction,
                        topPose, topNormal, buffer, light, overlay, red, green, blue, alpha);
            }
        }
    }
}

