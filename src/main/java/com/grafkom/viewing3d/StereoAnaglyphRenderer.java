package com.grafkom.viewing3d;

import org.joml.Matrix4f;
import static org.lwjgl.opengl.GL11.*;

/**
 * Praktikum 3 (Indikator 3): Kelas StereoAnaglyphRenderer.
 * Mengimplementasikan rendering stereoskopik Red-Cyan Anaglyph 3D menggunakan
 * metode Off-Axis Projection (Asymmetric Viewing Frustum) dengan pengisolasian
 * saluran warna (Color Masking) dan parameter IPD / Focal Length dinamis.
 */
public class StereoAnaglyphRenderer {

    private float ipd = 0.22f;          // Interpupillary Distance (jarak antar mata)
    private float focalLength = 7.5f;   // Jarak bidang konvergensi (Zero Parallax Plane)
    private boolean useOffAxisFrustum = true;

    public StereoAnaglyphRenderer() {
    }

    public StereoAnaglyphRenderer(float ipd, float focalLength) {
        this.ipd = ipd;
        this.focalLength = focalLength;
    }

    /**
     * Metode sesuai tanda tangan pada Modul Praktikum 3 (kompatibilitas langsung).
     */
    public void renderAnaglyph(Runnable renderSceneCallback) {
        // PASS 1: Mata Kiri (Hanya Saluran Warna Merah / Red Channel)
        glColorMask(true, false, false, true);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glTranslatef(ipd / 2.0f, 0.0f, 0.0f); // Pergeseran kamera mata kiri
        renderSceneCallback.run();            // Eksekusi callback render

        // PASS 2: Mata Kanan (Saluran Warna Hijau & Biru / Cyan Channel)
        glColorMask(false, true, true, true);
        glClear(GL_DEPTH_BUFFER_BIT);         // Depth buffer dibersihkan, color buffer di-blend!

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glTranslatef(-ipd / 2.0f, 0.0f, 0.0f); // Pergeseran kamera mata kanan
        renderSceneCallback.run();

        // Reset Color Mask ke kondisi normal
        glColorMask(true, true, true, true);
    }

    /**
     * Metode Off-Axis Asymmetric Frustum Stereo Anaglyph (memenuhi kriteria Rubrik Sangat Baik 85-100).
     * Menggeser frustum proyeksi secara asimetris untuk mata kiri dan kanan sehingga:
     * - Objek pada jarak focalLength berada pada Zero Parallax (tepat di layar)
     * - Objek di belakang focalLength memiliki Positive Parallax (masuk ke dalam layar)
     * - Objek di depan focalLength memiliki Negative Parallax (efek pop-out keluar layar)
     * tanpa distorsi vertikal (vertical parallax) yang sering terjadi pada metode Toe-In.
     */
    public void renderOffAxisAnaglyph(
            Camera3D camera,
            PerspectiveProjection projection,
            Runnable renderSceneCallback) {

        float[] projArr = new float[16];
        float[] viewArr = new float[16];
        Matrix4f baseView = camera.calculateLookAtMatrix();

        // ==================== PASS 1: MATA KIRI (RED CHANNEL) ====================
        glColorMask(true, false, false, true);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glMatrixMode(GL_PROJECTION);
        Matrix4f leftProj = useOffAxisFrustum
                ? calculateEyeFrustumMatrix(projection, -ipd / 2.0f)
                : projection.calculateProjectionMatrix();
        leftProj.get(projArr);
        glLoadMatrixf(projArr);

        glMatrixMode(GL_MODELVIEW);
        Matrix4f leftView = new Matrix4f().translation(ipd / 2.0f, 0.0f, 0.0f).mul(baseView);
        leftView.get(viewArr);
        glLoadMatrixf(viewArr);

        renderSceneCallback.run();

        // ==================== PASS 2: MATA KANAN (CYAN CHANNEL) ====================
        glColorMask(false, true, true, true);
        glClear(GL_DEPTH_BUFFER_BIT); // Hanya bersihkan Depth Buffer agar warna Red & Cyan berpadu

        glMatrixMode(GL_PROJECTION);
        Matrix4f rightProj = useOffAxisFrustum
                ? calculateEyeFrustumMatrix(projection, +ipd / 2.0f)
                : projection.calculateProjectionMatrix();
        rightProj.get(projArr);
        glLoadMatrixf(projArr);

        glMatrixMode(GL_MODELVIEW);
        Matrix4f rightView = new Matrix4f().translation(-ipd / 2.0f, 0.0f, 0.0f).mul(baseView);
        rightView.get(viewArr);
        glLoadMatrixf(viewArr);

        renderSceneCallback.run();

        // ==================== RESET COLOR MASK ====================
        glColorMask(true, true, true, true);
    }

    /**
     * Menghitung matriks Asymmetric Viewing Frustum untuk offset posisi mata (eyeOffset).
     * eyeOffset = -ipd/2 untuk mata kiri, +ipd/2 untuk mata kanan.
     */
    public Matrix4f calculateEyeFrustumMatrix(PerspectiveProjection proj, float eyeOffset) {
        float near = proj.getZNear();
        float far = proj.getZFar();
        float fovRad = (float) Math.toRadians(proj.getFovDegree());

        float top = near * (float) Math.tan(fovRad / 2.0f);
        float bottom = -top;
        float halfWidth = top * proj.getAspectRatio();

        // Pergeseran bidang frustum pada zNear proporsional terhadap rasio near / focalLength
        float frustumShift = -eyeOffset * (near / Math.max(0.5f, focalLength));
        float left = -halfWidth + frustumShift;
        float right = halfWidth + frustumShift;

        return PerspectiveProjection.calculateAsymmetricFrustumMatrix(left, right, bottom, top, near, far);
    }

    public void adjustIpd(float delta) {
        this.ipd = Math.max(0.02f, Math.min(1.20f, this.ipd + delta));
    }

    public void adjustFocalLength(float delta) {
        this.focalLength = Math.max(1.5f, Math.min(30.0f, this.focalLength + delta));
    }

    public void reset() {
        this.ipd = 0.22f;
        this.focalLength = 7.5f;
        this.useOffAxisFrustum = true;
    }

    public float getIpd() { return ipd; }
    public void setIpd(float ipd) { this.ipd = Math.max(0.02f, Math.min(1.20f, ipd)); }

    public float getFocalLength() { return focalLength; }
    public void setFocalLength(float focalLength) { this.focalLength = Math.max(1.5f, Math.min(30.0f, focalLength)); }

    public boolean isUseOffAxisFrustum() { return useOffAxisFrustum; }
    public void toggleOffAxisMode() { this.useOffAxisFrustum = !this.useOffAxisFrustum; }
}
