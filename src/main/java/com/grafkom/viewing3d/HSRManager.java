package com.grafkom.viewing3d;

import static org.lwjgl.opengl.GL11.*;

/**
 * Praktikum 2 (Indikator 3): Kelas HSRManager (Hidden Surface Removal).
 * Mengelola algoritma Z-Buffer (Depth-Buffer) dan Back-Face Culling pada pipeline OpenGL
 * untuk mengeliminasi permukaan tersembunyi (occluded surfaces) dan mencegah kesalahan overlap.
 */
public class HSRManager {

    private static boolean depthTestEnabled = true;
    private static boolean backFaceCullingEnabled = true;
    private static int frontFaceWinding = GL_CCW;

    /**
     * Metode sesuai spesifikasi Modul Praktikum 2:
     * Mengaktifkan/menonaktifkan pengujian Z-Buffer Depth Test (GL_LESS).
     */
    public static void setupDepthTest(boolean enable) {
        depthTestEnabled = enable;
        if (enable) {
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LESS); // Pixel lebih dekat (z_new < Z_Buffer[x,y]) lolos uji depth
        } else {
            glDisable(GL_DEPTH_TEST);
        }
    }

    /**
     * Metode sesuai spesifikasi Modul Praktikum 2:
     * Mengaktifkan/menonaktifkan Back-Face Culling dengan orientasi Counter-Clockwise (GL_CCW).
     */
    public static void setupBackFaceCulling(boolean enable) {
        backFaceCullingEnabled = enable;
        if (enable) {
            glEnable(GL_CULL_FACE);
            glCullFace(GL_BACK);         // Buang polygon yang membelakangi kamera (N . V >= 0)
            glFrontFace(frontFaceWinding); // Counter-Clockwise adalah tampak depan standar
        } else {
            glDisable(GL_CULL_FACE);
        }
    }

    /**
     * Toggle interaktif untuk tombol Key 'Z' (Z-Buffer Depth Test On/Off).
     */
    public static void toggleDepthTest() {
        setupDepthTest(!depthTestEnabled);
    }

    /**
     * Toggle interaktif untuk tombol Key 'C' (Back-Face Culling On/Off).
     */
    public static void toggleBackFaceCulling() {
        setupBackFaceCulling(!backFaceCullingEnabled);
    }

    /**
     * Menerapkan ulang state HSR aktif setelah render HUD 2D atau Shadow Pass selesai.
     */
    public static void applyCurrentState() {
        setupDepthTest(depthTestEnabled);
        setupBackFaceCulling(backFaceCullingEnabled);
    }

    public static boolean isDepthTestEnabled() {
        return depthTestEnabled;
    }

    public static boolean isBackFaceCullingEnabled() {
        return backFaceCullingEnabled;
    }
}
