package com.grafkom.viewing3d;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import static org.lwjgl.opengl.GL11.*;

/**
 * Praktikum 4 Minggu 10 + Integrasi Matriks JOML Minggu 11: Kelas ProjectionTaxonomyManager.
 * Mendukung 7 jenis sistem proyeksi baik untuk pipeline matriks Modern OpenGL (GLSL Uniform)
 * maupun pipeline kompatibilitas.
 */
public class ProjectionTaxonomyManager {

    public enum ProjectionType {
        ORTHOGRAPHIC("1: Paralel Orthographic (Tampak Depan/Bebas)"),
        ISOMETRIC("2: Paralel Axonometric Isometric (35.264 deg, 45 deg)"),
        OBLIQUE_CAVALIER("3: Paralel Oblique Cavalier (Shear 45 deg, L=1.0)"),
        PERSPECTIVE_1POINT("4: Perspektif 1 Titik Hilang (1-Point Perspective)"),
        PERSPECTIVE_2POINT("5: Perspektif 2 Titik Hilang (2-Point Perspective)"),
        PERSPECTIVE_3POINT("6: Perspektif 3 Titik Hilang (3-Point Perspective)"),
        OBLIQUE_CABINET("7: Paralel Oblique Cabinet (Shear 45 deg, L=0.5)");

        private final String label;

        ProjectionType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * Menghasilkan matriks proyeksi JOML Matrix4f untuk dikirim ke uniform 'projection' pada GLSL Shader.
     */
    public static Matrix4f calculateProjectionMatrix(
            ProjectionType type,
            float aspect,
            PerspectiveProjection perspectiveProjection) {

        float orthoScale = 4.8f;
        Matrix4f proj = new Matrix4f().identity();

        switch (type) {
            case ORTHOGRAPHIC:
                proj.ortho(-orthoScale * aspect, orthoScale * aspect, -orthoScale, orthoScale, -30.0f, 50.0f);
                break;

            case ISOMETRIC:
                proj.ortho(-orthoScale * aspect, orthoScale * aspect, -orthoScale, orthoScale, -30.0f, 50.0f);
                proj.rotateX((float) Math.toRadians(35.26439f));
                proj.rotateY((float) Math.toRadians(45.0f));
                break;

            case OBLIQUE_CAVALIER:
                proj.ortho(-orthoScale * aspect, orthoScale * aspect, -orthoScale, orthoScale, -30.0f, 50.0f);
                proj.mul(createObliqueShearMatrix4f(45.0f, 1.0f));
                break;

            case OBLIQUE_CABINET:
                proj.ortho(-orthoScale * aspect, orthoScale * aspect, -orthoScale, orthoScale, -30.0f, 50.0f);
                proj.mul(createObliqueShearMatrix4f(45.0f, 0.5f));
                break;

            case PERSPECTIVE_1POINT:
            case PERSPECTIVE_2POINT:
            case PERSPECTIVE_3POINT:
            default:
                perspectiveProjection.setAspectRatio(aspect);
                proj.set(perspectiveProjection.calculateProjectionMatrix());
                break;
        }
        return proj;
    }

    /**
     * Menghasilkan matriks View JOML Matrix4f untuk dikirim ke uniform 'view' pada GLSL Shader.
     */
    public static Matrix4f calculateTaxonomyViewMatrix(
            ProjectionType type,
            Camera3D camera,
            boolean lockCanonicalAngles) {

        if (!lockCanonicalAngles) {
            return camera.calculateLookAtMatrix();
        }

        float r = camera.getRadius();
        switch (type) {
            case ORTHOGRAPHIC:
                return camera.calculateLookAtMatrixForEye(
                        new Vector3f(0.0f, 1.2f, r),
                        new Vector3f(0.0f, 1.0f, 0.0f),
                        new Vector3f(0.0f, 1.0f, 0.0f)
                );

            case ISOMETRIC:
            case OBLIQUE_CAVALIER:
            case OBLIQUE_CABINET:
                return new Matrix4f().identity();

            case PERSPECTIVE_1POINT:
                return camera.calculateLookAtMatrixForEye(
                        new Vector3f(0.0f, 1.2f, r),
                        new Vector3f(0.0f, 1.0f, 0.0f),
                        new Vector3f(0.0f, 1.0f, 0.0f)
                );

            case PERSPECTIVE_2POINT:
                float azRad = (float) Math.toRadians(camera.getAzimuthDeg());
                Vector3f eye2P = new Vector3f(
                        r * (float) Math.sin(azRad),
                        1.2f,
                        r * (float) Math.cos(azRad)
                );
                return camera.calculateLookAtMatrixForEye(
                        eye2P,
                        new Vector3f(0.0f, 1.2f, 0.0f),
                        new Vector3f(0.0f, 1.0f, 0.0f)
                );

            case PERSPECTIVE_3POINT:
            default:
                return camera.calculateLookAtMatrix();
        }
    }

    public static void applyProjection(ProjectionType type, float aspect) {
        PerspectiveProjection defaultPersp = new PerspectiveProjection(45.0f, aspect, 0.2f, 50.0f);
        applyProjection(type, aspect, defaultPersp);
    }

    public static void applyProjection(
            ProjectionType type,
            float aspect,
            PerspectiveProjection perspectiveProjection) {
        glMatrixMode(GL_PROJECTION);
        glLoadMatrixf(toFloatArray(calculateProjectionMatrix(type, aspect, perspectiveProjection)));
    }

    public static void applyTaxonomyViewMatrix(
            ProjectionType type,
            Camera3D camera,
            boolean lockCanonicalAngles) {
        glMatrixMode(GL_MODELVIEW);
        glLoadMatrixf(toFloatArray(calculateTaxonomyViewMatrix(type, camera, lockCanonicalAngles)));
    }

    public static Matrix4f createObliqueShearMatrix4f(float angleDegree, float depthScaleL) {
        float alphaRad = (float) Math.toRadians(angleDegree);
        float cotX = -depthScaleL * (float) Math.cos(alphaRad);
        float cotY = -depthScaleL * (float) Math.sin(alphaRad);

        Matrix4f shear = new Matrix4f().identity();
        shear.m20(cotX);
        shear.m21(cotY);
        return shear;
    }

    public static float[] createObliqueShearMatrix(float angleDegree, float depthScaleL) {
        return toFloatArray(createObliqueShearMatrix4f(angleDegree, depthScaleL));
    }

    private static float[] toFloatArray(Matrix4f mat) {
        float[] arr = new float[16];
        mat.get(arr);
        return arr;
    }
}
