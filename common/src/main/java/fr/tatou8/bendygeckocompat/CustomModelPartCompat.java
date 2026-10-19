package fr.tatou8.bendygeckocompat;

import fr.tatou8.bendygeckocompat.GeckoLibCompat.LimbType;
import io.github.kosmx.bendylib.ModelPartAccessor;
import io.github.kosmx.bendylib.impl.BendableCuboid;
import io.github.kosmx.bendylib.impl.IPosWithOrigin;
import io.github.kosmx.bendylib.impl.RememberingPos;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class CustomModelPartCompat {

    public static class RenderingContext {
        public final BipedEntityModel<?> playerModel;
        public final LivingEntity entity;
        public final Map<ModelPart, LimbType> limbMap = new IdentityHashMap<>();

        public final BendableCuboid rightArmBend;
        public final BendableCuboid leftArmBend;
        public final BendableCuboid rightLegBend;
        public final BendableCuboid leftLegBend;
        public final BendableCuboid bodyBend;
        public final boolean hasAnyBend;

        public RenderingContext(BipedEntityModel<?> playerModel, LivingEntity entity) {
            this.playerModel = playerModel;
            this.entity = entity;

            this.rightArmBend = GeckoLibCompat.getBendable(playerModel.rightArm);
            this.leftArmBend = GeckoLibCompat.getBendable(playerModel.leftArm);
            this.rightLegBend = GeckoLibCompat.getBendable(playerModel.rightLeg);
            this.leftLegBend = GeckoLibCompat.getBendable(playerModel.leftLeg);
            this.bodyBend = GeckoLibCompat.getBendable(playerModel.body);

            this.hasAnyBend = isBendActive(rightArmBend)
                    || isBendActive(leftArmBend)
                    || isBendActive(rightLegBend)
                    || isBendActive(leftLegBend)
                    || isBendActive(bodyBend);
        }

        private static boolean isBendActive(BendableCuboid b) {
            return b != null && (b.getBend() != 0 || b.getBendAxis() != 0);
        }

        public LimbType getLimbType(ModelPart part) {
            return limbMap.get(part);
        }

        public BendableCuboid getBendableForLimb(LimbType limb) {
            if (limb == null) return null;
            return switch (limb) {
                case RIGHT_ARM -> rightArmBend;
                case LEFT_ARM -> leftArmBend;
                case RIGHT_LEG -> rightLegBend;
                case LEFT_LEG -> leftLegBend;
                case BODY -> bodyBend;
            };
        }

        public void registerLimb(ModelPart part, LimbType limb) {
            if (part != null && limb != null) {
                limbMap.put(part, limb);
            }
        }
    }

    private static final ThreadLocal<Deque<RenderingContext>> CONTEXT_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    public static void pushContext(EntityModel<?> contextModel, LivingEntity entity) {
        if (contextModel instanceof BipedEntityModel<?> biped) {
            CONTEXT_STACK.get().push(new RenderingContext(biped, entity));
        }
    }

    public static void popContext() {
        Deque<RenderingContext> stack = CONTEXT_STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    public static RenderingContext getCurrentContext() {
        Deque<RenderingContext> stack = CONTEXT_STACK.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    /**
     * Inspects and registers all limbs of any armor model (BipedEntityModel, MCreator, Blockbench, etc.).
     */
    public static void setCurrentArmorModel(Model armorModel) {
        if (armorModel == null) return;
        RenderingContext ctx = getCurrentContext();
        if (ctx == null || !ctx.hasAnyBend) return;

        // 1. Check BipedEntityModel fields
        if (armorModel instanceof BipedEntityModel<?> biped) {
            ctx.registerLimb(biped.rightArm, LimbType.RIGHT_ARM);
            ctx.registerLimb(biped.leftArm, LimbType.LEFT_ARM);
            ctx.registerLimb(biped.rightLeg, LimbType.RIGHT_LEG);
            ctx.registerLimb(biped.leftLeg, LimbType.LEFT_LEG);
            ctx.registerLimb(biped.body, LimbType.BODY);
        }

        // 2. Reflectively inspect all declared and inherited fields for custom ModelPart fields
        Class<?> clazz = armorModel.getClass();
        while (clazz != null && clazz != Model.class && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (ModelPart.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        ModelPart part = (ModelPart) field.get(armorModel);
                        if (part != null) {
                            LimbType limb = matchLimbByName(field.getName());
                            if (limb != null) {
                                ctx.registerLimb(part, limb);
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    public static LimbType matchLimbByName(String rawName) {
        if (rawName == null) return null;
        String name = rawName.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "").replace("-", "");
        if (name.contains("rightarm") || name.contains("rightsleeve") || name.contains("rightglove") || name.contains("righthand")) {
            return LimbType.RIGHT_ARM;
        }
        if (name.contains("leftarm") || name.contains("leftsleeve") || name.contains("leftglove") || name.contains("lefthand")) {
            return LimbType.LEFT_ARM;
        }
        if (name.contains("rightleg") || name.contains("rightboot") || name.contains("rightshoe") || name.contains("rightfoot") || name.contains("rightpants")) {
            return LimbType.RIGHT_LEG;
        }
        if (name.contains("leftleg") || name.contains("leftboot") || name.contains("leftshoe") || name.contains("leftfoot") || name.contains("leftpants")) {
            return LimbType.LEFT_LEG;
        }
        if (name.contains("body") || name.contains("torso") || name.contains("chest") || name.contains("skirt") || name.contains("robe") || name.contains("jacket") || name.contains("coat") || name.contains("tunic")) {
            return LimbType.BODY;
        }
        return null;
    }

    /**
     * Renders a ModelPart hierarchy with dynamic bendy deformation.
     */
    public static void renderBendableModelPart(ModelPart rootPart, MatrixStack matrices, VertexConsumer buffer,
                                               int light, int overlay, float red, float green, float blue, float alpha,
                                               LimbType limb, BendableCuboid bendable) {
        // Not isEmpty(): it only checks the part's own cubes, and Blockbench armors keep theirs in child parts
        if (!rootPart.visible) {
            return;
        }

        AtomicReference<Consumer<IPosWithOrigin>> transformRef = new AtomicReference<>();
        Matrix4f transformMatrix = bendable.applyBend(bendable.getBendAxis(), bendable.getBend(), transformRef::set);
        if (transformMatrix == null) {
            transformMatrix = new Matrix4f();
        }
        Consumer<IPosWithOrigin> transform = transformRef.get();
        if (transform == null) {
            return;
        }

        // No lateral/forward offset: armors are modelled around the limb, shifting them exposed the arm (Ghillie vest)
        matrices.push();
        rootPart.rotate(matrices);

        Matrix4f topPose = new Matrix4f(matrices.peek().getPositionMatrix());
        Matrix3f topNormal = new Matrix3f(matrices.peek().getNormalMatrix());
        Matrix4f rootRelMatrix = new Matrix4f().identity();

        renderPartRecursive(rootPart, rootRelMatrix, topPose, topNormal, limb, bendable, transform,
                buffer, light, overlay, red, green, blue, alpha, true);

        matrices.pop();
    }

    private static void renderPartRecursive(ModelPart part, Matrix4f parentRelMatrix,
                                            Matrix4f topPose, Matrix3f topNormal,
                                            LimbType limb, BendableCuboid bendable,
                                            Consumer<IPosWithOrigin> transform,
                                            VertexConsumer buffer, int light, int overlay,
                                            float red, float green, float blue, float alpha,
                                            boolean isRoot) {
        if (!part.visible) {
            return;
        }

        Matrix4f currentRelMatrix;
        if (isRoot) {
            currentRelMatrix = parentRelMatrix;
        } else {
            currentRelMatrix = new Matrix4f(parentRelMatrix);
            currentRelMatrix.translate(part.pivotX, part.pivotY, part.pivotZ);
            if (part.roll != 0.0F) currentRelMatrix.rotateZ(part.roll);
            if (part.yaw != 0.0F) currentRelMatrix.rotateY(part.yaw);
            if (part.pitch != 0.0F) currentRelMatrix.rotateX(part.pitch);
            if (part.xScale != 1.0F || part.yScale != 1.0F || part.zScale != 1.0F) {
                currentRelMatrix.scale(part.xScale, part.yScale, part.zScale);
            }
        }

        if (!part.hidden) {
            List<ModelPart.Cuboid> cuboids = ModelPartAccessor.getCuboids(part);
            if (cuboids != null) {
                for (ModelPart.Cuboid cuboid : cuboids) {
                    if (cuboid instanceof IBendableCuboidRenderer bRenderer) {
                        bRenderer.bendy$renderBent(topPose, topNormal, currentRelMatrix,
                                limb, bendable, transform, buffer, light, overlay, red, green, blue, alpha);
                    }
                }
            }
        }

        Map<String, ModelPart> children = ModelPartAccessor.getChildren(part);
        if (children != null && !children.isEmpty()) {
            for (ModelPart child : children.values()) {
                renderPartRecursive(child, currentRelMatrix, topPose, topNormal, limb, bendable, transform,
                        buffer, light, overlay, red, green, blue, alpha, false);
            }
        }
    }

    public static List<float[]> getSliceIntervals(float py0, float py1, LimbType limb) {
        List<float[]> intervals = new ArrayList<>();
        float minY = Math.min(py0, py1);
        float maxY = Math.max(py0, py1);
        float height = maxY - minY;

        float jointY = (limb == LimbType.RIGHT_ARM || limb == LimbType.LEFT_ARM) ? 4.0f : 6.0f;
        // A small cube straddling the elbow/knee must still be split there, or its corners bend apart
        boolean crossesJoint = limb != LimbType.BODY && minY < jointY && maxY > jointY;
        if (height <= 1.0f && !crossesJoint) {
            intervals.add(new float[]{0.0f, 1.0f});
            return intervals;
        }

        if (limb == LimbType.BODY) {
            int slices = 12;
            for (int i = 0; i < slices; i++) {
                intervals.add(new float[]{(float) i / slices, (float) (i + 1) / slices});
            }
            return intervals;
        }

        if (crossesJoint) {
            float tJoint = Math.abs(py1 - py0) > 0.001f ? (jointY - py0) / (py1 - py0) : 0.5f;
            tJoint = Math.max(0.05f, Math.min(0.95f, tJoint));
            float eps = 0.0002f;

            int slicesHalf = 6;
            for (int i = 0; i < slicesHalf; i++) {
                float t0 = ((float) i / slicesHalf) * tJoint;
                float t1 = ((float) (i + 1) / slicesHalf) * tJoint;
                if (i == slicesHalf - 1) t1 = tJoint - eps;
                intervals.add(new float[]{t0, t1});
            }
            for (int i = 0; i < slicesHalf; i++) {
                float start = tJoint + eps;
                float t0 = start + ((float) i / slicesHalf) * (1.0f - start);
                float t1 = start + ((float) (i + 1) / slicesHalf) * (1.0f - start);
                intervals.add(new float[]{t0, t1});
            }
        } else {
            int slices = (int) Math.min(8, Math.max(3, Math.ceil(height)));
            for (int i = 0; i < slices; i++) {
                intervals.add(new float[]{(float) i / slices, (float) (i + 1) / slices});
            }
        }

        return intervals;
    }

    public static void renderBentQuad(Vector3f raw0, float u0, float v0,
                                      Vector3f raw1, float u1, float v1,
                                      Vector3f raw2, float u2, float v2,
                                      Vector3f raw3, float u3, float v3,
                                      LimbType limb, BendableCuboid bendable,
                                      Consumer<IPosWithOrigin> transform,
                                      Matrix4f childRelMatrix,
                                      Vector3f defaultNormal,
                                      Matrix4f topPose, Matrix3f topNormal,
                                      VertexConsumer buffer, int light, int overlay,
                                      float red, float green, float blue, float alpha) {

        Vector3f bent0 = transformPixelVertex(raw0, transform);
        Vector3f bent1 = transformPixelVertex(raw1, transform);
        Vector3f bent2 = transformPixelVertex(raw2, transform);
        Vector3f bent3 = transformPixelVertex(raw3, transform);

        // Compute local normal reference from defaultNormal transformed by childRelMatrix
        Vector4f qNorm = childRelMatrix.transform(new Vector4f(defaultNormal.x(), defaultNormal.y(), defaultNormal.z(), 0.0f));
        Vector3f baseNorm = new Vector3f(qNorm.x, qNorm.y, qNorm.z).normalize();

        // Calculate face normal from deformed quad geometry (vA = raw1 - raw0, vB = raw3 - raw0 in Y-down coords: vA x vB points outward)
        Vector3f vA = new Vector3f(bent1).sub(bent0);
        Vector3f vB = new Vector3f(bent3).sub(bent0);
        Vector3f normalLocal = new Vector3f(vA).cross(vB).normalize();
        if (!normalLocal.isFinite() || normalLocal.lengthSquared() < 0.001f) {
            normalLocal = new Vector3f(baseNorm);
        } else if (baseNorm.lengthSquared() > 0.001f && normalLocal.dot(baseNorm) < 0) {
            normalLocal.mul(-1.0f);
        }

        Vector3f worldNormal = topNormal.transform(new Vector3f(normalLocal));

        // Transform vertices to camera view space
        Vector4f w0 = topPose.transform(new Vector4f(bent0.x / 16.0f, bent0.y / 16.0f, bent0.z / 16.0f, 1.0f));
        Vector4f w1 = topPose.transform(new Vector4f(bent1.x / 16.0f, bent1.y / 16.0f, bent1.z / 16.0f, 1.0f));
        Vector4f w2 = topPose.transform(new Vector4f(bent2.x / 16.0f, bent2.y / 16.0f, bent2.z / 16.0f, 1.0f));
        Vector4f w3 = topPose.transform(new Vector4f(bent3.x / 16.0f, bent3.y / 16.0f, bent3.z / 16.0f, 1.0f));

        buffer.vertex(w0.x, w0.y, w0.z, red, green, blue, alpha, u0, v0, overlay, light, worldNormal.x, worldNormal.y, worldNormal.z);
        buffer.vertex(w1.x, w1.y, w1.z, red, green, blue, alpha, u1, v1, overlay, light, worldNormal.x, worldNormal.y, worldNormal.z);
        buffer.vertex(w2.x, w2.y, w2.z, red, green, blue, alpha, u2, v2, overlay, light, worldNormal.x, worldNormal.y, worldNormal.z);
        buffer.vertex(w3.x, w3.y, w3.z, red, green, blue, alpha, u3, v3, overlay, light, worldNormal.x, worldNormal.y, worldNormal.z);
    }

    private static Vector3f transformPixelVertex(Vector3f raw, Consumer<IPosWithOrigin> transform) {
        RememberingPos pos = new RememberingPos(new Vector3f(raw));
        transform.accept(pos);
        return pos.getPos();
    }

    public static Vector3f lerpVec(Vector4f a, Vector4f b, float t) {
        return new Vector3f(
                lerp(a.x, b.x, t),
                lerp(a.y, b.y, t),
                lerp(a.z, b.z, t)
        );
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
