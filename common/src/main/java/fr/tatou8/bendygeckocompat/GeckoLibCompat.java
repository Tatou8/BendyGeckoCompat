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

    public static boolean DEBUG = Boolean.getBoolean("bendygeckocompat.debug"); // enable with -Dbendygeckocompat.debug=true
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
            if (match == null && isMatchingLimbName(current)) {
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

    private static boolean isMatchingLimbName(GeoBone name) {
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
        synchronizeLimbPivots(renderer);
    }

    /**
     * Synchronizes armor limb pivots with vanilla Minecraft player skeleton pivots.
     * Prevents rotation desynchronization when armors have non-standard pivots in their geo models.
     */
    public static void synchronizeLimbPivots(GeoArmorRenderer<?> renderer) {
        if (renderer == null) return;

        GeoBone rightArm = renderer.getRightArmBone();
        if (rightArm != null) {
            if (Math.abs(rightArm.getPivotX() - 5.0f) > 0.01f) rightArm.setPivotX(5.0f);
            if (rightArm.getParent() != null && Math.abs(rightArm.getParent().getPivotX() - 5.0f) > 0.01f) {
                rightArm.getParent().setPivotX(5.0f);
            }
        }

        GeoBone leftArm = renderer.getLeftArmBone();
        if (leftArm != null) {
            if (Math.abs(leftArm.getPivotX() - (-5.0f)) > 0.01f) leftArm.setPivotX(-5.0f);
            if (leftArm.getParent() != null && Math.abs(leftArm.getParent().getPivotX() - (-5.0f)) > 0.01f) {
                leftArm.getParent().setPivotX(-5.0f);
            }
        }

        GeoBone rightLeg = renderer.getRightLegBone();
        if (rightLeg != null) {
            if (Math.abs(rightLeg.getPivotX() - 1.9f) > 0.01f) rightLeg.setPivotX(1.9f);
            if (rightLeg.getParent() != null && Math.abs(rightLeg.getParent().getPivotX() - 1.9f) > 0.01f) {
                rightLeg.getParent().setPivotX(1.9f);
            }
        }

        GeoBone leftLeg = renderer.getLeftLegBone();
        if (leftLeg != null) {
            if (Math.abs(leftLeg.getPivotX() - (-1.9f)) > 0.01f) leftLeg.setPivotX(-1.9f);
            if (leftLeg.getParent() != null && Math.abs(leftLeg.getParent().getPivotX() - (-1.9f)) > 0.01f) {
                leftLeg.getParent().setPivotX(-1.9f);
            }
        }

        GeoBone rightBoot = renderer.getRightBootBone();
        if (rightBoot != null) {
            if (Math.abs(rightBoot.getPivotX() - 1.9f) > 0.01f) rightBoot.setPivotX(1.9f);
            if (rightBoot.getParent() != null && Math.abs(rightBoot.getParent().getPivotX() - 1.9f) > 0.01f) {
                rightBoot.getParent().setPivotX(1.9f);
            }
        }

        GeoBone leftBoot = renderer.getLeftBootBone();
        if (leftBoot != null) {
            if (Math.abs(leftBoot.getPivotX() - (-1.9f)) > 0.01f) leftBoot.setPivotX(-1.9f);
            if (leftBoot.getParent() != null && Math.abs(leftBoot.getParent().getPivotX() - (-1.9f)) > 0.01f) {
                leftBoot.getParent().setPivotX(-1.9f);
            }
        }
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
        if (bone == renderer.getHeadBone() || isMatching(bone, "head")) {
            return true;
        }
        if (bone == renderer.getBodyBone() || isMatching(bone, "body") || isMatching(bone, "torso") || isMatching(bone, "chest")) {
            return true;
        }
        if (bone == renderer.getRightArmBone() || isMatching(bone, "rightarm")) {
            return true;
        }
        if (bone == renderer.getLeftArmBone() || isMatching(bone, "leftarm")) {
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
     * Checks if a bone represents a boot limb (or any child bone of a boot, like toes/shoes).
     */
    public static boolean isBootLimb(GeoArmorRenderer<?> renderer, GeoBone bone) {
        if (renderer == null || bone == null) {
            return false;
        }
        GeoBone current = bone;
        while (current != null) {
            if (current == renderer.getRightBootBone() || current == renderer.getLeftBootBone()) {
                return true;
            }
            if (isMatching(current, "boot") || isMatching(current, "shoe") || isMatching(current, "toe")) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Checks if a bone is the highest ancestor among boot limbs.
     * Prevents applying knee rotation multiple times to nested child bones.
     */
    public static boolean isTopLevelBootLimb(GeoArmorRenderer<?> renderer, GeoBone bone) {
        if (!isBootLimb(renderer, bone)) {
            return false;
        }
        GeoBone parent = bone.getParent();
        while (parent != null) {
            if (isBootLimb(renderer, parent)) {
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
            if (current == renderer.getRightArmBone() || isMatching(current, "rightarm")) {
                return LimbType.RIGHT_ARM;
            }
            if (current == renderer.getLeftArmBone() || isMatching(current, "leftarm")) {
                return LimbType.LEFT_ARM;
            }
            if (current == renderer.getRightLegBone() || current == renderer.getRightBootBone() || isMatching(current, "rightleg") || isMatching(current, "rightboot") || isMatching(current, "rightshoe")) {
                return LimbType.RIGHT_LEG;
            }
            if (current == renderer.getLeftLegBone() || current == renderer.getLeftBootBone() || isMatching(current, "leftleg") || isMatching(current, "leftboot") || isMatching(current, "leftshoe")) {
                return LimbType.LEFT_LEG;
            }
            if (current == renderer.getBodyBone() || isMatching(current, "body") || isMatching(current, "torso") || isMatching(current, "chest")) {
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

    // Bone names never change: normalise each once instead of on every check, every frame
    private static final Map<GeoBone, String> CLEAN_NAMES = new java.util.WeakHashMap<>();

    private static boolean isMatching(GeoBone bone, String pattern) {
        return CLEAN_NAMES.computeIfAbsent(bone, b -> b.getName() == null ? ""
                : b.getName().toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "").replace("-", "")).contains(pattern);
    }

    public static boolean isBendableBone(GeoArmorRenderer<?> renderer, GeoBone bone, LimbType limb) {
        return renderer != null && bone != null && limb != null;
    }

    private record CachedBend(float axis, float bend, Consumer<IPosWithOrigin> transform) {}

    private static final Map<BendableCuboid, CachedBend> BEND_CACHE = new java.util.WeakHashMap<>();

    /**
     * The bendy-lib point transform for the limb's current bend.
     * applyBend() also re-bends the player's whole cuboid (about 10% of a frame when called per bone), so it only
     * runs again when the bend actually changed.
     */
    public static Consumer<IPosWithOrigin> bendTransform(BendableCuboid bendable) {
        CachedBend cached = BEND_CACHE.get(bendable);
        if (cached != null && cached.axis() == bendable.getBendAxis() && cached.bend() == bendable.getBend()) {
            return cached.transform();
        }
        AtomicReference<Consumer<IPosWithOrigin>> transformRef = new AtomicReference<>();
        bendable.applyBend(bendable.getBendAxis(), bendable.getBend(), transformRef::set);
        Consumer<IPosWithOrigin> transform = transformRef.get();
        if (transform != null) {
            BEND_CACHE.put(bendable, new CachedBend(bendable.getBendAxis(), bendable.getBend(), transform));
        }
        return transform;
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
        public final boolean isSlim;

        public DynamicLimbOffsets(float lateralOffset, float forwardOffset, boolean isSlim) {
            this.lateralOffset = lateralOffset;
            this.forwardOffset = forwardOffset;
            this.isSlim = isSlim;
        }

        public DynamicLimbOffsets(float lateralOffset, float forwardOffset) {
            this(lateralOffset, forwardOffset, false);
        }
    }

    public static final DynamicLimbOffsets ZERO_OFFSETS = new DynamicLimbOffsets(0.0f, 0.0f, false);
    private static final Map<GeoBone, DynamicLimbOffsets> GECKO_OFFSETS_CACHE = new java.util.WeakHashMap<>(); // weak: bones are rebuilt on resource reload

    public static DynamicLimbOffsets getDynamicOffsets(GeoArmorRenderer<?> renderer, GeoBone top, LimbType limb) {
        GeoBone targetBone = top;
        if (renderer != null) {
            if (limb == LimbType.RIGHT_LEG && renderer.getRightLegBone() != null) {
                targetBone = renderer.getRightLegBone();
            } else if (limb == LimbType.LEFT_LEG && renderer.getLeftLegBone() != null) {
                targetBone = renderer.getLeftLegBone();
            } else if (limb == LimbType.RIGHT_ARM && renderer.getRightArmBone() != null) {
                targetBone = renderer.getRightArmBone();
            } else if (limb == LimbType.LEFT_ARM && renderer.getLeftArmBone() != null) {
                targetBone = renderer.getLeftArmBone();
            }
        }
        if (targetBone == null || limb == null) {
            return ZERO_OFFSETS;
        }
        return GECKO_OFFSETS_CACHE.computeIfAbsent(targetBone, bone -> calculateGeckoLimbOffsets(bone, limb));
    }

    public static DynamicLimbOffsets getDynamicOffsets(GeoBone top, LimbType limb) {
        return getDynamicOffsets(null, top, limb);
    }

    private static void collectBones(GeoBone root, List<GeoBone> out) {
        if (root == null) return;
        out.add(root);
        for (GeoBone child : root.getChildBones()) {
            collectBones(child, out);
        }
    }

    private static DynamicLimbOffsets calculateGeckoLimbOffsets(GeoBone top, LimbType limb) {
        if (limb != LimbType.RIGHT_ARM && limb != LimbType.LEFT_ARM
                && limb != LimbType.RIGHT_LEG && limb != LimbType.LEFT_LEG) {
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
        float limbWidth = maxX - minX;
        float centerX = (minX + maxX) / 2.0f;
        float centerZ = (minZ + maxZ) / 2.0f;
        boolean isSlim = (limbWidth < 4.3f);

        if (limb == LimbType.RIGHT_ARM) {
            // Synchronize shoulder pivot to vanilla (+5.0f in GeckoLib)
            if (Math.abs(top.getPivotX() - 5.0f) > 0.01f) {
                top.setPivotX(5.0f);
            }
            if (top.getParent() != null && Math.abs(top.getParent().getPivotX() - 5.0f) > 0.01f) {
                top.getParent().setPivotX(5.0f);
            }

            if (Math.abs(centerZ) > 0.25f) {
                forward = centerZ;
            }
        } else if (limb == LimbType.LEFT_ARM) {
            // Synchronize shoulder pivot to vanilla (-5.0f in GeckoLib)
            if (Math.abs(top.getPivotX() - (-5.0f)) > 0.01f) {
                top.setPivotX(-5.0f);
            }
            if (top.getParent() != null && Math.abs(top.getParent().getPivotX() - (-5.0f)) > 0.01f) {
                top.getParent().setPivotX(-5.0f);
            }

            if (Math.abs(centerZ) > 0.25f) {
                forward = centerZ;
            }
        } else if (limb == LimbType.RIGHT_LEG) {
            // Synchronize hip pivot to vanilla (+1.9f in GeckoLib)
            if (Math.abs(top.getPivotX() - 1.9f) > 0.01f) {
                top.setPivotX(1.9f);
            }
            if (top.getParent() != null && Math.abs(top.getParent().getPivotX() - 1.9f) > 0.01f) {
                top.getParent().setPivotX(1.9f);
            }

            // Normal right leg center in GeckoLib space is +2.0f, inner edge is 0.0f.
            // If the model center is shifted outward (centerX > 2.05f) or inner edge leaves a crotch gap (minX > 0.1f):
            if (centerX > 2.05f) {
                float centerExcess = centerX - 2.0f;
                lateral += centerExcess;
            }
            if (minX > 0.1f) {
                lateral += minX + 0.1f;
            }

            if (Math.abs(centerZ) > 0.25f) {
                forward = centerZ;
            }
        } else if (limb == LimbType.LEFT_LEG) {
            // Synchronize hip pivot to vanilla (-1.9f in GeckoLib)
            if (Math.abs(top.getPivotX() - (-1.9f)) > 0.01f) {
                top.setPivotX(-1.9f);
            }
            if (top.getParent() != null && Math.abs(top.getParent().getPivotX() - (-1.9f)) > 0.01f) {
                top.getParent().setPivotX(-1.9f);
            }

            // Normal left leg center in GeckoLib space is -2.0f, inner edge is 0.0f.
            // If the model center is shifted outward (centerX < -2.05f) or inner edge leaves a crotch gap (maxX < -0.1f):
            if (centerX < -2.05f) {
                float centerExcess = (-2.0f) - centerX;
                lateral += centerExcess;
            }
            if (maxX < -0.1f) {
                lateral += (-maxX) + 0.1f;
            }

            if (Math.abs(centerZ) > 0.25f) {
                forward = centerZ;
            }
        }

        // Deadband to ensure standard models (like Wolf Armor) have strictly 0.0f
        if (lateral < 0.2f) lateral = 0.0f;
        if (Math.abs(forward) < 0.2f) forward = 0.0f;

        // Strictly clamp to prevent any drift
        lateral = Math.min(1.75f, Math.max(0.0f, lateral));
        forward = Math.min(1.75f, Math.max(-1.75f, forward));

        return new DynamicLimbOffsets(lateral, forward, isSlim);
    }

    /**
     * Renders the cubes of a GeoBone with bendy deformation applied.
     */
    public static void renderBendableBoneCubes(GeoArmorRenderer<?> renderer, MatrixStack matrices, GeoBone bone,
                                               LimbType limb, BendableCuboid bendable, VertexConsumer buffer,
                                               int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        GeoBone top = getTopLimbBone(renderer, bone);
        boolean isTop = (top == null || bone == top);

        DynamicLimbOffsets offsets = getDynamicOffsets(renderer, top, limb);
        float lateral = offsets.lateralOffset;
        float forward = offsets.forwardOffset;

        Matrix4f topPose = new Matrix4f(matrices.peek().getPositionMatrix());
        if (limb == LimbType.RIGHT_ARM || limb == LimbType.RIGHT_LEG) {
            topPose.translate(-lateral / 16.0f, 0, -forward / 16.0f);
        } else if (limb == LimbType.LEFT_ARM || limb == LimbType.LEFT_LEG) {
            topPose.translate(lateral / 16.0f, 0, -forward / 16.0f);
        }

        if (isTop) {
            saveTopLimbMatrices(limb, topPose, matrices.peek().getNormalMatrix());
        }

        if (bone.isHidden() || bone.getCubes().isEmpty()) {
            return;
        }

        Consumer<IPosWithOrigin> transform = bendTransform(bendable);
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

        // The child->top bone chain is the same for every vertex of this bone: build it once per frame
        Matrix4f boneToTop = childToTopMatrix(bone, top);
        for (GeoCube cube : bone.getCubes()) {
            renderBendableCube(cube, boneToTop, limb, transform, bendable, bonePoseState, boneNormalState, buffer, packedLight, packedOverlay, red, green, blue, alpha, offsets);
        }
    }

    /** Matrix form of {@link #transformChildToTop}: each bone's pivot, scale, rotation and position, from child up to top. */
    static Matrix4f childToTopMatrix(GeoBone child, GeoBone top) {
        Matrix4f m = new Matrix4f();
        if (child == null || top == null) return m;
        for (GeoBone b = child; b != null && b != top; b = b.getParent()) {
            float pivX = b.getPivotX() / 16.0f, pivY = b.getPivotY() / 16.0f, pivZ = b.getPivotZ() / 16.0f;
            new Matrix4f()
                    .translate(pivX - b.getPosX() / 16.0f, pivY + b.getPosY() / 16.0f, pivZ + b.getPosZ() / 16.0f)
                    .rotate(new Quaternionf().rotationZYX(b.getRotZ(), b.getRotY(), b.getRotX()))
                    .scale(b.getScaleX(), b.getScaleY(), b.getScaleZ())
                    .translate(-pivX, -pivY, -pivZ)
                    .mul(m, m); // m = boneTransform * m
        }
        return m;
    }

    private static void renderBendableCube(GeoCube cube, Matrix4f boneToTop, LimbType limb, Consumer<IPosWithOrigin> transform, BendableCuboid bendable,
                                           Matrix4f bonePoseState, Matrix3f boneNormalState, VertexConsumer buffer,
                                           int packedLight, int packedOverlay, float red, float green, float blue, float alpha,
                                           DynamicLimbOffsets offsets) {
        Quaternionf qCube = new Quaternionf()
                .rotationXYZ(0, 0, (float) cube.rotation().z)
                .rotateY((float) cube.rotation().y)
                .rotateX((float) cube.rotation().x);

        float cubePivotX = (float) (cube.pivot().x / 16.0);
        float cubePivotY = (float) (cube.pivot().y / 16.0);
        float cubePivotZ = (float) (cube.pivot().z / 16.0);

        // Cube rotation + bone chain is affine: corners go to top space once, slices are interpolated there
        Matrix4f cubeToTop = new Matrix4f(boneToTop)
                .translate(cubePivotX, cubePivotY, cubePivotZ)
                .rotate(qCube)
                .translate(-cubePivotX, -cubePivotY, -cubePivotZ);

        if (DEBUG && isLoggingActive()) {
            log("[BendyDebug]    renderBendableCube: size=" + cube.size() + ", pivot=" + cube.pivot());
        }

        for (GeoQuad quad : cube.quads()) {
            if (quad == null || quad.vertices().length != 4) {
                continue;
            }

            GeoVertex[] v = quad.vertices();
            Vector3f p0 = cubeToTop.transformPosition(new Vector3f(v[0].position()));
            Vector3f p1 = cubeToTop.transformPosition(new Vector3f(v[1].position()));
            Vector3f p2 = cubeToTop.transformPosition(new Vector3f(v[2].position()));
            Vector3f p3 = cubeToTop.transformPosition(new Vector3f(v[3].position()));

            List<float[]> intervals = getSliceIntervals(toVanillaPixelY(p0.y, limb), toVanillaPixelY(p3.y, limb), limb);

            // Neighbouring slices share an edge: bend each edge point once
            float prevT = Float.NaN;
            Vector3f prevEdge0 = null, prevEdge1 = null;
            for (float[] interval : intervals) {
                float t0 = interval[0];
                float t1 = interval[1];

                Vector3f bent0, bent1;
                if (t0 == prevT) {
                    bent0 = prevEdge0;
                    bent1 = prevEdge1;
                } else {
                    bent0 = bendPoint(lerpVec(p0, p3, t0), limb, transform, bendable, offsets);
                    bent1 = bendPoint(lerpVec(p1, p2, t0), limb, transform, bendable, offsets);
                }
                Vector3f bent2 = bendPoint(lerpVec(p1, p2, t1), limb, transform, bendable, offsets);
                Vector3f bent3 = bendPoint(lerpVec(p0, p3, t1), limb, transform, bendable, offsets);

                // Edge 0 is v0 -> v3, edge 1 is v1 -> v2
                renderBentQuad(bent0, lerp(v[0].texU(), v[3].texU(), t0), lerp(v[0].texV(), v[3].texV(), t0),
                        bent1, lerp(v[1].texU(), v[2].texU(), t0), lerp(v[1].texV(), v[2].texV(), t0),
                        bent2, lerp(v[1].texU(), v[2].texU(), t1), lerp(v[1].texV(), v[2].texV(), t1),
                        bent3, lerp(v[0].texU(), v[3].texU(), t1), lerp(v[0].texV(), v[3].texV(), t1),
                        bonePoseState, boneNormalState, buffer, packedLight, packedOverlay, red, green, blue, alpha, cube, quad);

                prevT = t1;
                prevEdge0 = bent3;
                prevEdge1 = bent2;
            }
        }
    }

    private static List<float[]> getSliceIntervals(float py0, float py3, LimbType limb) {
        List<float[]> intervals = new ArrayList<>();
        float minY = Math.min(py0, py3);
        float maxY = Math.max(py0, py3);
        float height = maxY - minY;

        float jointY = (limb == LimbType.RIGHT_ARM || limb == LimbType.LEFT_ARM) ? 4.0f : 6.0f;
        // A small cube straddling the elbow/knee must still be split there, or its corners bend apart
        boolean crossesJoint = limb != LimbType.BODY && minY < jointY && maxY > jointY;
        if (height <= 2.0f && !crossesJoint) {
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

    private static void renderBentQuad(Vector3f bentPos0, float u0, float v0,
                                       Vector3f bentPos1, float u1, float v1,
                                       Vector3f bentPos2, float u2, float v2,
                                       Vector3f bentPos3, float u3, float v3,
                                       Matrix4f bonePoseState, Matrix3f boneNormalState, VertexConsumer buffer,
                                       int packedLight, int packedOverlay,
                                       float red, float green, float blue, float alpha,
                                       GeoCube cube, GeoQuad quad) {
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

    /** Applies the bendy-lib deformation to a point already in top-bone space. */
    private static Vector3f bendPoint(Vector3f inTop, LimbType limb, Consumer<IPosWithOrigin> transform,
                                      BendableCuboid bendable, DynamicLimbOffsets offsets) {
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

        // For slim arm sleeves on Steve (width < 4.3px), micro-expand by 6% around arm center to prevent skin clipping/z-fighting
        if (offsets != null && offsets.isSlim && (limb == LimbType.RIGHT_ARM || limb == LimbType.LEFT_ARM)) {
            if (limb == LimbType.RIGHT_ARM) {
                vanillaX = -1.0f + (vanillaX - (-1.0f)) * 1.06f;
            } else {
                vanillaX = 1.0f + (vanillaX - 1.0f) * 1.06f;
            }
            vanillaZ = vanillaZ * 1.06f;
        }

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

    private static float toVanillaPixelY(float topY, LimbType limb) {
        if (limb == LimbType.BODY) {
            return topY * 16.0f;
        }
        return 24.0f - topY * 16.0f - limb.vanillaPivotY;
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
