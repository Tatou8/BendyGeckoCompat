package fr.tatou8.bendygeckocompat;

import io.github.kosmx.bendylib.ModelPartAccessor;
import io.github.kosmx.bendylib.impl.BendableCuboid;
import io.github.kosmx.bendylib.impl.IPosWithOrigin;
import io.github.kosmx.bendylib.impl.RememberingPos;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.RenderUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class GeckoLibCompat {

    public static boolean DEBUG = true;
    private static Logger LOGGER = null;
    private static long lastDebugLogTime = 0;
    private static long loggingActiveUntil = 0;

    private static final Map<LimbType, Matrix4f> TOP_LIMB_POSE_MATRICES = new EnumMap<>(LimbType.class);
    private static final Map<LimbType, Matrix3f> TOP_LIMB_NORMAL_MATRICES = new EnumMap<>(LimbType.class);

    public static void saveTopLimbMatrices(LimbType limb, Matrix4f pose, Matrix3f normal) {
        TOP_LIMB_POSE_MATRICES.put(limb, new Matrix4f(pose));
        TOP_LIMB_NORMAL_MATRICES.put(limb, new Matrix3f(normal));
    }

    public static Matrix4f getTopLimbPoseMatrix(LimbType limb, Matrix4f fallback) {
        Matrix4f m = TOP_LIMB_POSE_MATRICES.get(limb);
        return m != null ? m : fallback;
    }

    public static Matrix3f getTopLimbNormalMatrix(LimbType limb, Matrix3f fallback) {
        Matrix3f m = TOP_LIMB_NORMAL_MATRICES.get(limb);
        return m != null ? m : fallback;
    }

    public static GeoBone getTopLimbBone(GeoArmorRenderer<?> renderer, GeoBone bone) {
        if (renderer == null || bone == null) return null;
        GeoBone current = bone;
        GeoBone match = null;
        while (current != null) {
            if (isSpecificLimbBone(renderer, current)) {
                return current;
            }
            if (match == null && isMatchingLimbName(current.getName())) {
                match = current;
            }
            current = current.getParent();
        }
        return match;
    }

    private static boolean isSpecificLimbBone(GeoArmorRenderer<?> renderer, GeoBone bone) {
        return bone == renderer.getRightArmBone()
                || bone == renderer.getLeftArmBone()
                || bone == renderer.getRightLegBone()
                || bone == renderer.getLeftLegBone()
                || bone == renderer.getRightBootBone()
                || bone == renderer.getLeftBootBone()
                || bone == renderer.getBodyBone();
    }

    private static boolean isMatchingLimbName(String name) {
        if (name == null) return false;
        if (isMatching(name, "armorrightarm") || isMatching(name, "armorleftarm")
                || isMatching(name, "armorrightleg") || isMatching(name, "armorleftleg")
                || isMatching(name, "armorrightboot") || isMatching(name, "armorleftboot")
                || isMatching(name, "armorbody") || isMatching(name, "armortorso") || isMatching(name, "armorchest")) {
            return true;
        }
        return isMatching(name, "rightarm") || isMatching(name, "leftarm")
                || isMatching(name, "rightleg") || isMatching(name, "leftleg")
                || isMatching(name, "rightboot") || isMatching(name, "leftboot")
                || isMatching(name, "body") || isMatching(name, "torso") || isMatching(name, "chest");
    }

    public static Vector3f transformChildToTop(Vector3f pos, GeoBone child, GeoBone top) {
        if (child == null || top == null || child == top) {
            return pos;
        }

        java.util.List<GeoBone> path = new java.util.ArrayList<>();
        GeoBone curr = child;
        while (curr != null && curr != top) {
            path.add(curr);
            curr = curr.getParent();
        }

        Vector3f p = new Vector3f(pos);
        for (GeoBone b : path) {
            float pivX = b.getPivotX() / 16.0f;
            float pivY = b.getPivotY() / 16.0f;
            float pivZ = b.getPivotZ() / 16.0f;

            p.sub(pivX, pivY, pivZ);

            if (b.getScaleX() != 1.0f || b.getScaleY() != 1.0f || b.getScaleZ() != 1.0f) {
                p.mul(b.getScaleX(), b.getScaleY(), b.getScaleZ());
            }

            if (b.getRotX() != 0 || b.getRotY() != 0 || b.getRotZ() != 0) {
                Quaternionf q = new Quaternionf().rotationZYX(b.getRotZ(), b.getRotY(), b.getRotX());
                p.rotate(q);
            }

            p.add(pivX, pivY, pivZ);

            float posX = -b.getPosX() / 16.0f;
            float posY = b.getPosY() / 16.0f;
            float posZ = b.getPosZ() / 16.0f;
            p.add(posX, posY, posZ);
        }

        return p;
    }

    public enum LimbType {
        RIGHT_ARM(-5.0f, 2.0f, 0.0f),
        LEFT_ARM(5.0f, 2.0f, 0.0f),
        RIGHT_LEG(-1.9f, 12.0f, 0.0f),
        LEFT_LEG(1.9f, 12.0f, 0.0f),
        BODY(0.0f, 0.0f, 0.0f);

        public final float vanillaPivotX;
        public final float vanillaPivotY;
        public final float vanillaPivotZ;

        LimbType(float vanillaPivotX, float vanillaPivotY, float vanillaPivotZ) {
            this.vanillaPivotX = vanillaPivotX;
            this.vanillaPivotY = vanillaPivotY;
            this.vanillaPivotZ = vanillaPivotZ;
        }
    }

    public static void init(Logger logger) {
        LOGGER = logger;
        log("Initializing BendyGeckoCompat (DEBUG=" + DEBUG + ")");
    }

    public static void log(String message) {
        if (LOGGER != null) {
            LOGGER.info(message);
        }
        System.out.println(message);
    }

    public static boolean isLoggingActive() {
        return DEBUG && System.currentTimeMillis() < loggingActiveUntil;
    }

    public static boolean shouldTriggerLog() {
        if (!DEBUG) return false;
        long now = System.currentTimeMillis();
        if (now - lastDebugLogTime > 4000) {
            lastDebugLogTime = now;
            loggingActiveUntil = now + 200; // active for next 200ms
            return true;
        }
        return false;
    }

    /**
     * Dumps the full structure of the armor model and bending parameters to latest.log / STDOUT.
     */
    public static void debugLogArmorState(GeoArmorRenderer<?> renderer, BipedEntityModel<?> baseModel) {
        if (!shouldTriggerLog()) return;

        log("========== [BendyGeckoCompat Debug Dump] ==========");
        log("Armor Entity: " + renderer.getCurrentEntity() + ", Slot: " + renderer.getCurrentSlot() + ", Stack: " + renderer.getCurrentStack());
        if (baseModel != null) {
            BendableCuboid bodyB = getBendable(baseModel.body);
            BendableCuboid rightArmB = getBendable(baseModel.rightArm);
            BendableCuboid leftArmB = getBendable(baseModel.leftArm);
            BendableCuboid rightLegB = getBendable(baseModel.rightLeg);
            BendableCuboid leftLegB = getBendable(baseModel.leftLeg);

            log("BaseModel State:");
            log("  head: pivot=[" + baseModel.head.pivotX + ", " + baseModel.head.pivotY + ", " + baseModel.head.pivotZ + "], rot=[" + baseModel.head.pitch + ", " + baseModel.head.yaw + ", " + baseModel.head.roll + "]");
            log("  body: pivot=[" + baseModel.body.pivotX + ", " + baseModel.body.pivotY + ", " + baseModel.body.pivotZ + "], rot=[" + baseModel.body.pitch + ", " + baseModel.body.yaw + ", " + baseModel.body.roll + "], bendable=" + (bodyB != null ? ("bend=" + bodyB.getBend() + ", axis=" + bodyB.getBendAxis()) : "none"));
            log("  rightArm: pivot=[" + baseModel.rightArm.pivotX + ", " + baseModel.rightArm.pivotY + ", " + baseModel.rightArm.pivotZ + "], rot=[" + baseModel.rightArm.pitch + ", " + baseModel.rightArm.yaw + ", " + baseModel.rightArm.roll + "], bendable=" + (rightArmB != null ? ("bend=" + rightArmB.getBend() + ", axis=" + rightArmB.getBendAxis()) : "none"));
            log("  leftArm: pivot=[" + baseModel.leftArm.pivotX + ", " + baseModel.leftArm.pivotY + ", " + baseModel.leftArm.pivotZ + "], rot=[" + baseModel.leftArm.pitch + ", " + baseModel.leftArm.yaw + ", " + baseModel.leftArm.roll + "], bendable=" + (leftArmB != null ? ("bend=" + leftArmB.getBend() + ", axis=" + leftArmB.getBendAxis()) : "none"));
            log("  rightLeg: pivot=[" + baseModel.rightLeg.pivotX + ", " + baseModel.rightLeg.pivotY + ", " + baseModel.rightLeg.pivotZ + "], bendable=" + (rightLegB != null ? ("bend=" + rightLegB.getBend() + ", axis=" + rightLegB.getBendAxis()) : "none"));
            log("  leftLeg: pivot=[" + baseModel.leftLeg.pivotX + ", " + baseModel.leftLeg.pivotY + ", " + baseModel.leftLeg.pivotZ + "], bendable=" + (leftLegB != null ? ("bend=" + leftLegB.getBend() + ", axis=" + leftLegB.getBendAxis()) : "none"));
        }

        try {
            if (renderer.getGeoModel() != null && renderer.getAnimatable() != null) {
                software.bernie.geckolib.model.GeoModel geoModel = (software.bernie.geckolib.model.GeoModel) renderer.getGeoModel();
                var modelResource = geoModel.getModelResource((GeoAnimatable) renderer.getAnimatable());
                if (modelResource != null) {
                    BakedGeoModel bakedModel = geoModel.getBakedModel(modelResource);
                    if (bakedModel != null) {
                        log("--- Full BakedGeoModel Bone Tree ---");
                        for (GeoBone root : bakedModel.topLevelBones()) {
                            debugLogBoneTree(root, "  ", renderer);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            log("[BendyDebug] Error dumping baked model tree: " + t);
        }
        log("====================================================");
    }

    public static void debugLogBoneTree(GeoBone bone, String indent, GeoArmorRenderer<?> renderer) {
        if (bone == null) return;
        LimbType limb = getLimbType(renderer, bone);
        log(indent + "- Bone: '" + bone.getName() + "' (hidden=" + bone.isHidden() + ", detectedLimb=" + limb + ", cubes=" + bone.getCubes().size()
                + ", pivot=[" + bone.getPivotX() + ", " + bone.getPivotY() + ", " + bone.getPivotZ() + "]"
                + ", pos=[" + bone.getPosX() + ", " + bone.getPosY() + ", " + bone.getPosZ() + "]"
                + ", rot=[" + bone.getRotX() + ", " + bone.getRotY() + ", " + bone.getRotZ() + "])");

        for (int i = 0; i < bone.getCubes().size(); i++) {
            GeoCube cube = bone.getCubes().get(i);
            log(indent + "    Cube " + i + ": size=" + cube.size() + ", pivot=" + cube.pivot() + ", rot=" + cube.rotation());
        }
        for (GeoBone child : bone.getChildBones()) {
            debugLogBoneTree(child, indent + "  ", renderer);
        }
    }

    public static void applyTorsoBendToArmor(GeoArmorRenderer<?> renderer, BipedEntityModel<?> baseModel) {
        // Base transformations are handled directly by GeckoLib's applyBaseTransformations.
    }

    /**
     * Applies or reverts the waist rotation on the MatrixStack for upper body armor limbs.
     */
    public static void applyWaistRotation(GeoArmorRenderer<?> renderer, BipedEntityModel<?> baseModel, MatrixStack poseStack, boolean pre) {
        if (renderer == null || baseModel == null) return;
        BendableCuboid bodyBendable = getBendable(baseModel.body);
        if (bodyBendable != null && (bodyBendable.getBend() != 0 || bodyBendable.getBendAxis() != 0)) {
            if (pre) {
                poseStack.push();
                poseStack.translate(0, 18.0f / 16.0f, 0);
                float axisf = -bodyBendable.getBendAxis();
                Vector3f axis = new Vector3f((float) Math.cos(axisf), 0, (float) Math.sin(axisf));
                poseStack.multiply(new Quaternionf().rotateAxis(-bodyBendable.getBend(), axis));
                poseStack.translate(0, -18.0f / 16.0f, 0);
            } else {
                poseStack.pop();
            }
        }
    }

    /**
     * Checks if a bone represents an upper body limb (head or arms) that attaches to the torso
     * and should follow the waist rotation.
     */
    public static boolean isUpperBodyLimb(GeoArmorRenderer<?> renderer, GeoBone bone) {
        if (renderer == null || bone == null) {
            return false;
        }
        if (bone == renderer.getHeadBone() || isMatching(bone.getName(), "head")) {
            return true;
        }
        if (bone == renderer.getBodyBone() || isMatching(bone.getName(), "body") || isMatching(bone.getName(), "torso") || isMatching(bone.getName(), "chest")) {
            return true;
        }
        if (bone == renderer.getRightArmBone() || isMatching(bone.getName(), "rightarm")) {
            return true;
        }
        if (bone == renderer.getLeftArmBone() || isMatching(bone.getName(), "leftarm")) {
            return true;
        }
        return false;
    }

    /**
     * Checks if a bone is the highest ancestor among upper body limbs.
     * Prevents applying waist rotation multiple times to nested child bones.
     */
    public static boolean isTopLevelUpperBodyLimb(GeoArmorRenderer<?> renderer, GeoBone bone) {
        if (!isUpperBodyLimb(renderer, bone)) {
            return false;
        }
        GeoBone parent = bone.getParent();
        while (parent != null) {
            if (isUpperBodyLimb(renderer, parent)) {
                return false;
            }
            parent = parent.getParent();
        }
        return true;
    }

    /**
     * Identifies the limb type for a given GeoBone, searching ancestors if necessary.
     */
    public static LimbType getLimbType(GeoArmorRenderer<?> renderer, GeoBone bone) {
        if (renderer == null || bone == null) {
            return null;
        }

        GeoBone current = bone;
        while (current != null) {
            if (current == renderer.getRightArmBone() || isMatching(current.getName(), "rightarm")) {
                return LimbType.RIGHT_ARM;
            }
            if (current == renderer.getLeftArmBone() || isMatching(current.getName(), "leftarm")) {
                return LimbType.LEFT_ARM;
            }
            if (current == renderer.getRightLegBone() || current == renderer.getRightBootBone() || isMatching(current.getName(), "rightleg") || isMatching(current.getName(), "rightboot")) {
                return LimbType.RIGHT_LEG;
            }
            if (current == renderer.getLeftLegBone() || current == renderer.getLeftBootBone() || isMatching(current.getName(), "leftleg") || isMatching(current.getName(), "leftboot")) {
                return LimbType.LEFT_LEG;
            }
            if (current == renderer.getBodyBone() || isMatching(current.getName(), "body") || isMatching(current.getName(), "torso") || isMatching(current.getName(), "chest")) {
                return LimbType.BODY;
            }
            current = current.getParent();
        }

        return null;
    }

    public static ModelPart getModelPartForLimb(BipedEntityModel<?> baseModel, LimbType limb) {
        if (baseModel == null || limb == null) {
            return null;
        }
        return switch (limb) {
            case RIGHT_ARM -> baseModel.rightArm;
            case LEFT_ARM -> baseModel.leftArm;
            case RIGHT_LEG -> baseModel.rightLeg;
            case LEFT_LEG -> baseModel.leftLeg;
            case BODY -> baseModel.body;
        };
    }

    private static boolean isMatching(String name, String pattern) {
        if (name == null) return false;
        String clean = name.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "").replace("-", "");
        return clean.contains(pattern);
    }

    public static boolean isBendableBone(GeoArmorRenderer<?> renderer, GeoBone bone, LimbType limb) {
        return renderer != null && bone != null && limb != null;
    }

    /**
     * Retrieves the active BendableCuboid from a ModelPart, if present.
     */
    public static BendableCuboid getBendable(ModelPart modelPart) {
        if (modelPart == null) return null;
        var cuboidOptional = ModelPartAccessor.optionalGetCuboid(modelPart, 0);
        if (cuboidOptional.isPresent()
                && cuboidOptional.get().getActiveMutator() != null
                && cuboidOptional.get().getActiveMutator().getRight() instanceof BendableCuboid bendable) {
            return bendable;
        }
        return null;
    }

    public static class DynamicLimbOffsets {
        public final float lateralOffset;
        public final float forwardOffset;

        public DynamicLimbOffsets(float lateralOffset, float forwardOffset) {
            this.lateralOffset = lateralOffset;
            this.forwardOffset = forwardOffset;
        }
    }

    public static final DynamicLimbOffsets ZERO_OFFSETS = new DynamicLimbOffsets(0.0f, 0.0f);
    private static final Map<GeoBone, DynamicLimbOffsets> GECKO_OFFSETS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static DynamicLimbOffsets getDynamicOffsets(GeoBone top, LimbType limb) {
        if (top == null || limb == null) {
            return ZERO_OFFSETS;
        }
        return GECKO_OFFSETS_CACHE.computeIfAbsent(top, bone -> calculateGeckoLimbOffsets(bone, limb));
    }

    private static void collectBones(GeoBone root, List<GeoBone> out) {
        if (root == null) return;
        out.add(root);
        for (GeoBone child : root.getChildBones()) {
            collectBones(child, out);
        }
    }

    private static DynamicLimbOffsets calculateGeckoLimbOffsets(GeoBone top, LimbType limb) {
        if (limb != LimbType.RIGHT_ARM && limb != LimbType.LEFT_ARM) {
            return ZERO_OFFSETS;
        }

        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        int vertexCount = 0;

        List<GeoBone> bones = new ArrayList<>();
        collectBones(top, bones);

        for (GeoBone b : bones) {
            for (GeoCube cube : b.getCubes()) {
                if (cube.quads() == null) continue;
                for (GeoQuad quad : cube.quads()) {
                    if (quad == null || quad.vertices() == null) continue;
                    for (GeoVertex v : quad.vertices()) {
                        if (v == null || v.position() == null) continue;
                        Vector3f pos = v.position();
                        Vector3f topPos = transformChildToTop(pos, b, top);
                        float px = topPos.x * 16.0f;
                        float pz = topPos.z * 16.0f;
                        if (px < minX) minX = px;
                        if (px > maxX) maxX = px;
                        if (pz < minZ) minZ = pz;
                        if (pz > maxZ) maxZ = pz;
                        vertexCount++;
                    }
                }
            }
        }

        if (vertexCount == 0) {
            return ZERO_OFFSETS;
        }

        float lateral = 0.0f;
        float forward = 0.0f;

        if (limb == LimbType.RIGHT_ARM) {
            // In GeckoLib's BakedGeoModel, right arm pivot is at +5.0f (negated from json -5.0f).
            // Normal arm cube inner edge is +3.5f (with standard 0.5f armor thickness).
            if (minX < 3.5f) {
                float encroachment = 3.5f - minX;
                if (encroachment > 0.1f) {
                    lateral += encroachment;
                }
            }
            float pivotInwardDelta = 5.0f - top.getPivotX();
            if (pivotInwardDelta > 0.1f) {
                lateral += pivotInwardDelta;
            }

            // Standard right arm front edge is -2.0f, with standard 0.5f thickness = -2.5f.
            if (minZ < -2.5f) {
                float frontExcess = (-2.5f) - minZ;
                if (frontExcess > 0.1f) {
                    forward -= frontExcess;
                }
            }
            if (top.getPivotZ() < -0.1f) {
                forward += top.getPivotZ();
            }
        } else if (limb == LimbType.LEFT_ARM) {
            // In GeckoLib's BakedGeoModel, left arm pivot is at -5.0f (negated from json +5.0f).
            // Normal arm cube inner edge is -3.5f (with standard 0.5f armor thickness).
            if (maxX > -3.5f) {
                float encroachment = maxX - (-3.5f);
                if (encroachment > 0.1f) {
                    lateral += encroachment;
                }
            }
            float pivotInwardDelta = top.getPivotX() - (-5.0f);
            if (pivotInwardDelta > 0.1f) {
                lateral += pivotInwardDelta;
            }

            if (minZ < -2.5f) {
                float frontExcess = (-2.5f) - minZ;
                if (frontExcess > 0.1f) {
                    forward -= frontExcess;
                }
            }
            if (top.getPivotZ() < -0.1f) {
                forward += top.getPivotZ();
            }
        }

        // Deadband to ensure standard models (like Wolf Armor) have strictly 0.0f
        if (lateral < 0.2f) lateral = 0.0f;
        if (Math.abs(forward) < 0.2f) forward = 0.0f;

        // Strictly clamp to prevent any drift
        lateral = Math.min(1.75f, Math.max(0.0f, lateral));
        forward = Math.min(0.0f, Math.max(-1.75f, forward));

        return new DynamicLimbOffsets(lateral, forward);
    }

    /**
     * Renders the cubes of a GeoBone with bendy deformation applied.
     */
    public static void renderBendableBoneCubes(GeoArmorRenderer<?> renderer, MatrixStack matrices, GeoBone bone,
                                               LimbType limb, BendableCuboid bendable, VertexConsumer buffer,
                                               int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        GeoBone top = getTopLimbBone(renderer, bone);
        boolean isTop = (top == null || bone == top);

        DynamicLimbOffsets offsets = getDynamicOffsets(top, limb);
        float bendWeight = Math.min(1.0f, Math.abs(bendable.getBend()) * 1.5f);
        float lateral = offsets.lateralOffset * bendWeight;
        float forward = offsets.forwardOffset * bendWeight;

        Matrix4f topPose = new Matrix4f(matrices.peek().getPositionMatrix());
        if (limb == LimbType.RIGHT_ARM) {
            topPose.translate(-lateral / 16.0f, 0, -forward / 16.0f);
        } else if (limb == LimbType.LEFT_ARM) {
            topPose.translate(lateral / 16.0f, 0, -forward / 16.0f);
        }

        if (isTop) {
            saveTopLimbMatrices(limb, topPose, matrices.peek().getNormalMatrix());
        }

        if (bone.isHidden() || bone.getCubes().isEmpty()) {
            return;
        }

        // Obtain bend transformation function and matrix from BendableCuboid
        AtomicReference<Consumer<IPosWithOrigin>> transformRef = new AtomicReference<>();
        Matrix4f transformMatrix = bendable.applyBend(bendable.getBendAxis(), bendable.getBend(), transformRef::set);
        if (transformMatrix == null) {
            transformMatrix = new Matrix4f();
        }
        Consumer<IPosWithOrigin> transform = transformRef.get();
        if (transform == null) {
            return;
        }

        if (DEBUG && isLoggingActive()) {
            log("[BendyDebug] -> renderBendableBoneCubes: bone='" + bone.getName() + "', isTop=" + isTop + ", limb=" + limb + ", cubes=" + bone.getCubes().size() + ", bend=" + bendable.getBend());
        }

        Matrix4f bonePoseState = isTop
                ? topPose
                : getTopLimbPoseMatrix(limb, matrices.peek().getPositionMatrix());
        Matrix3f boneNormalState = isTop
                ? new Matrix3f(matrices.peek().getNormalMatrix())
                : getTopLimbNormalMatrix(limb, matrices.peek().getNormalMatrix());

        for (GeoCube cube : bone.getCubes()) {
            renderBendableCube(cube, bone, top, limb, transform, transformMatrix, bendable, bonePoseState, boneNormalState, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }

    private static void renderBendableCube(GeoCube cube, GeoBone bone, GeoBone top, LimbType limb, Consumer<IPosWithOrigin> transform, Matrix4f transformMatrix, BendableCuboid bendable,
                                           Matrix4f bonePoseState, Matrix3f boneNormalState, VertexConsumer buffer,
                                           int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        Quaternionf qCube = new Quaternionf()
                .rotationXYZ(0, 0, (float) cube.rotation().z)
                .rotateY((float) cube.rotation().y)
                .rotateX((float) cube.rotation().x);

        float cubePivotX = (float) (cube.pivot().x / 16.0);
        float cubePivotY = (float) (cube.pivot().y / 16.0);
        float cubePivotZ = (float) (cube.pivot().z / 16.0);

        if (DEBUG && isLoggingActive()) {
            log("[BendyDebug]    renderBendableCube: size=" + cube.size() + ", pivot=" + cube.pivot());
        }

        for (GeoQuad quad : cube.quads()) {
            if (quad == null || quad.vertices().length != 4) {
                continue;
            }

            GeoVertex[] v = quad.vertices();

            float py0 = toVanillaPixelY(v[0].position(), cubePivotX, cubePivotY, cubePivotZ, qCube, bone, top, limb);
            float py3 = toVanillaPixelY(v[3].position(), cubePivotX, cubePivotY, cubePivotZ, qCube, bone, top, limb);

            List<float[]> intervals = getSliceIntervals(py0, py3, limb);
            for (float[] interval : intervals) {
                float t0 = interval[0];
                float t1 = interval[1];

                // Interpolate edge 0 (v0 to v3) and edge 1 (v1 to v2)
                Vector3f pos0 = lerpVec(v[0].position(), v[3].position(), t0);
                float u0 = lerp(v[0].texU(), v[3].texU(), t0);
                float texV0 = lerp(v[0].texV(), v[3].texV(), t0);

                Vector3f pos1 = lerpVec(v[1].position(), v[2].position(), t0);
                float u1 = lerp(v[1].texU(), v[2].texU(), t0);
                float texV1 = lerp(v[1].texV(), v[2].texV(), t0);

                Vector3f pos2 = lerpVec(v[1].position(), v[2].position(), t1);
                float u2 = lerp(v[1].texU(), v[2].texU(), t1);
                float texV2 = lerp(v[1].texV(), v[2].texV(), t1);

                Vector3f pos3 = lerpVec(v[0].position(), v[3].position(), t1);
                float u3 = lerp(v[0].texU(), v[3].texU(), t1);
                float texV3 = lerp(v[0].texV(), v[3].texV(), t1);

                renderBentQuad(pos0, u0, texV0, pos1, u1, texV1, pos2, u2, texV2, pos3, u3, texV3,
                        cubePivotX, cubePivotY, cubePivotZ, qCube, bone, top, limb,
                        transform, transformMatrix, bendable, bonePoseState, boneNormalState, buffer, packedLight, packedOverlay,
                        red, green, blue, alpha, cube, quad);
            }
        }
    }

    private static List<float[]> getSliceIntervals(float py0, float py3, LimbType limb) {
        List<float[]> intervals = new ArrayList<>();
        float minY = Math.min(py0, py3);
        float maxY = Math.max(py0, py3);
        float height = maxY - minY;

        if (height <= 2.0f) {
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

        float jointY = (limb == LimbType.RIGHT_ARM || limb == LimbType.LEFT_ARM) ? 4.0f : 6.0f;
        if (minY < jointY && maxY > jointY) {
            float tJoint = Math.abs(py3 - py0) > 0.001f ? (jointY - py0) / (py3 - py0) : 0.5f;
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

    private static void renderBentQuad(Vector3f rawPos0, float u0, float v0,
                                       Vector3f rawPos1, float u1, float v1,
                                       Vector3f rawPos2, float u2, float v2,
                                       Vector3f rawPos3, float u3, float v3,
                                       float cubePivotX, float cubePivotY, float cubePivotZ, Quaternionf qCube,
                                       GeoBone bone, GeoBone top, LimbType limb, Consumer<IPosWithOrigin> transform, Matrix4f transformMatrix, BendableCuboid bendable,
                                       Matrix4f bonePoseState, Matrix3f boneNormalState, VertexConsumer buffer,
                                       int packedLight, int packedOverlay,
                                       float red, float green, float blue, float alpha,
                                       GeoCube cube, GeoQuad quad) {

        Vector3f bentPos0 = transformVertex(rawPos0, cubePivotX, cubePivotY, cubePivotZ, qCube, bone, top, limb, transform, transformMatrix, bendable);
        Vector3f bentPos1 = transformVertex(rawPos1, cubePivotX, cubePivotY, cubePivotZ, qCube, bone, top, limb, transform, transformMatrix, bendable);
        Vector3f bentPos2 = transformVertex(rawPos2, cubePivotX, cubePivotY, cubePivotZ, qCube, bone, top, limb, transform, transformMatrix, bendable);
        Vector3f bentPos3 = transformVertex(rawPos3, cubePivotX, cubePivotY, cubePivotZ, qCube, bone, top, limb, transform, transformMatrix, bendable);

        // Compute normal from bent vertices
        Vector3f vA = new Vector3f(bentPos1).sub(bentPos0);
        Vector3f vB = new Vector3f(bentPos3).sub(bentPos0);
        Vector3f normalLocal = new Vector3f(vB).cross(vA).normalize();
        if (!normalLocal.isFinite() || normalLocal.lengthSquared() < 0.001f) {
            normalLocal = new Vector3f(quad.normal());
        } else if (normalLocal.dot(quad.normal()) < 0) {
            normalLocal.mul(-1.0f);
        }

        Vector3f worldNormal = boneNormalState.transform(new Vector3f(normalLocal));
        RenderUtils.fixInvertedFlatCube(cube, worldNormal);

        // Transform vertices to camera view space
        Vector4f world0 = bonePoseState.transform(new Vector4f(bentPos0.x, bentPos0.y, bentPos0.z, 1.0f));
        Vector4f world1 = bonePoseState.transform(new Vector4f(bentPos1.x, bentPos1.y, bentPos1.z, 1.0f));
        Vector4f world2 = bonePoseState.transform(new Vector4f(bentPos2.x, bentPos2.y, bentPos2.z, 1.0f));
        Vector4f world3 = bonePoseState.transform(new Vector4f(bentPos3.x, bentPos3.y, bentPos3.z, 1.0f));

        buffer.vertex(world0.x, world0.y, world0.z, red, green, blue, alpha, u0, v0, packedOverlay, packedLight, worldNormal.x, worldNormal.y, worldNormal.z);
        buffer.vertex(world1.x, world1.y, world1.z, red, green, blue, alpha, u1, v1, packedOverlay, packedLight, worldNormal.x, worldNormal.y, worldNormal.z);
        buffer.vertex(world2.x, world2.y, world2.z, red, green, blue, alpha, u2, v2, packedOverlay, packedLight, worldNormal.x, worldNormal.y, worldNormal.z);
        buffer.vertex(world3.x, world3.y, world3.z, red, green, blue, alpha, u3, v3, packedOverlay, packedLight, worldNormal.x, worldNormal.y, worldNormal.z);
    }

    private static Vector3f transformVertex(Vector3f rawPos,
                                            float cubePivotX, float cubePivotY, float cubePivotZ, Quaternionf qCube,
                                            GeoBone bone, GeoBone top, LimbType limb,
                                            Consumer<IPosWithOrigin> transform,
                                            Matrix4f transformMatrix,
                                            BendableCuboid bendable) {
        // Rotate vertex around cube pivot
        Vector3f inCube = new Vector3f(rawPos.x - cubePivotX, rawPos.y - cubePivotY, rawPos.z - cubePivotZ);
        inCube.rotate(qCube);
        inCube.add(cubePivotX, cubePivotY, cubePivotZ);

        Vector3f inTop = transformChildToTop(inCube, bone, top);
        float rotatedX = inTop.x;
        float rotatedY = inTop.y;
        float rotatedZ = inTop.z;

        if (limb == LimbType.BODY && bendable != null) {
            return transformBodyVertex(new Vector3f(rotatedX, rotatedY, rotatedZ), bendable.getBend(), bendable.getBendAxis());
        }

        // Convert GeckoLib block coordinates to vanilla ModelPart local pixel coordinates
        float vanillaWorldX = -(rotatedX * 16.0f);
        float vanillaWorldY = 24.0f - (rotatedY * 16.0f);
        float vanillaWorldZ = rotatedZ * 16.0f;

        float vanillaX = vanillaWorldX - limb.vanillaPivotX;
        float vanillaY = vanillaWorldY - limb.vanillaPivotY;
        float vanillaZ = vanillaWorldZ - limb.vanillaPivotZ;

        // Apply bendy deformation
        RememberingPos pos = new RememberingPos(new Vector3f(vanillaX, vanillaY, vanillaZ));
        transform.accept(pos);
        Vector3f bentPixel = pos.getPos();

        // Convert back to GeckoLib block coordinates
        float bentWorldX = bentPixel.x + limb.vanillaPivotX;
        float bentWorldY = bentPixel.y + limb.vanillaPivotY;
        float bentWorldZ = bentPixel.z + limb.vanillaPivotZ;

        float bentX = -bentWorldX / 16.0f;
        float bentY = (24.0f - bentWorldY) / 16.0f;
        float bentZ = bentWorldZ / 16.0f;

        return new Vector3f(bentX, bentY, bentZ);
    }

    public static Vector3f transformBodyVertex(Vector3f inPos, float bend, float bendAxis) {
        float pixelY = inPos.y * 16.0f;
        float waistY = 12.0f;
        float neckY = 24.0f;

        // Above or at neckY (24), no counter-rotation needed: already rotated by waist rotation on poseStack
        if (pixelY >= neckY) {
            return new Vector3f(inPos);
        }

        float axisf = -bendAxis;
        Vector3f axis = new Vector3f((float) Math.cos(axisf), 0, (float) Math.sin(axisf));

        // Smoothstep progress from waist (12) to neck (24)
        float t = Math.max(0.0f, Math.min(1.0f, (pixelY - waistY) / (neckY - waistY)));
        float smoothT = t * t * (3.0f - 2.0f * t);

        // Counter-rotation angle: +bend at waist (cancels poseStack rotation so waist stays at hips), 0 at neck
        float counterAngle = bend * (1.0f - smoothT);

        float pivotY = 18.0f / 16.0f;
        Vector3f fromPivot = new Vector3f(inPos.x, inPos.y - pivotY, inPos.z);
        fromPivot.rotate(new Quaternionf().rotateAxis(counterAngle, axis));
        return new Vector3f(fromPivot.x, fromPivot.y + pivotY, fromPivot.z);
    }

    private static float toVanillaPixelY(Vector3f rawPos, float cubePivotX, float cubePivotY, float cubePivotZ, Quaternionf qCube, GeoBone bone, GeoBone top, LimbType limb) {
        Vector3f inCube = new Vector3f(rawPos.x - cubePivotX, rawPos.y - cubePivotY, rawPos.z - cubePivotZ);
        inCube.rotate(qCube);
        inCube.add(cubePivotX, cubePivotY, cubePivotZ);

        Vector3f inTop = transformChildToTop(inCube, bone, top);
        float rotatedY = inTop.y;
        if (limb == LimbType.BODY) {
            return rotatedY * 16.0f;
        }
        float vanillaWorldY = 24.0f - (rotatedY * 16.0f);
        return vanillaWorldY - limb.vanillaPivotY;
    }

    private static Vector3f lerpVec(Vector3f a, Vector3f b, float t) {
        return new Vector3f(
                lerp(a.x, b.x, t),
                lerp(a.y, b.y, t),
                lerp(a.z, b.z, t)
        );
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
