package com.grafkom.viewing3d;

import org.joml.Matrix4f;

/**
 * Praktikum 2 (Indikator 2): Kelas PerspectiveProjection & Frustum Tuning.
 * Menghitung matriks proyeksi perspektif simetris dan asimetris (Off-Axis Frustum)
 * secara eksplisit tanpa distorsi rasio aspek, serta mendukung fitur Dolly Zoom (Vertigo Effect).
 */
public class PerspectiveProjection {

    private float fovDegree;
    private float aspectRatio;
    private float zNear;
    private float zFar;

    public PerspectiveProjection(float fovDegree, float aspectRatio, float zNear, float zFar) {
        this.fovDegree = clampFov(fovDegree);
        this.aspectRatio = Math.max(0.1f, aspectRatio);
        this.zNear = Math.max(0.05f, zNear);
        this.zFar = Math.max(this.zNear + 0.5f, zFar);
    }

    /**
     * Perhitungan eksplisit Matriks Proyeksi Perspektif sesuai persamaan Modul Bagian I.2.2:
     * f = 1.0 / tan(FOV_y / 2)
     */
    public Matrix4f calculateProjectionMatrix() {
        float fovRad = (float) Math.toRadians(fovDegree);
        float f = 1.0f / (float) Math.tan(fovRad / 2.0f);

        Matrix4f proj = new Matrix4f();
        proj.zero();
        proj.m00(f / aspectRatio);
        proj.m11(f);
        proj.m22(-(zFar + zNear) / (zFar - zNear));
        proj.m32(-(2.0f * zFar * zNear) / (zFar - zNear));
        proj.m23(-1.0f);
        proj.m33(0.0f);

        return proj;
    }

    /**
     * Perhitungan eksplisit Matriks Asymmetric Viewing Frustum (Off-Axis Projection)
     * untuk keperluan Stereo Viewing presisi tinggi dan proyeksi oblik/umum.
     */
    public static Matrix4f calculateAsymmetricFrustumMatrix(
            float left, float right, float bottom, float top, float near, float far) {
        Matrix4f frustum = new Matrix4f();
        frustum.zero();
        frustum.m00((2.0f * near) / (right - left));
        frustum.m11((2.0f * near) / (top - bottom));
        frustum.m20((right + left) / (right - left));
        frustum.m21((top + bottom) / (top - bottom));
        frustum.m22(-(far + near) / (far - near));
        frustum.m32(-(2.0f * far * near) / (far - near));
        frustum.m23(-1.0f);
        frustum.m33(0.0f);
        return frustum;
    }

    /**
     * Menghitung jarak kamera baru untuk efek Dolly Zoom (Vertigo Effect)
     * agar ukuran objek pada bidang target tetap konstan saat FOV diubah:
     * radius_baru = tinggi_konstan / tan(FOV / 2).
     */
    public float calculateDollyZoomRadius(float referenceHalfHeight) {
        float fovRad = (float) Math.toRadians(this.fovDegree);
        return referenceHalfHeight / (float) Math.tan(fovRad / 2.0f);
    }

    public float[] getProjectionMatrixFloatArray() {
        float[] arr = new float[16];
        calculateProjectionMatrix().get(arr);
        return arr;
    }

    private float clampFov(float fov) {
        return Math.max(10.0f, Math.min(130.0f, fov));
    }

    public void adjustFov(float deltaDeg) {
        setFovDegree(this.fovDegree + deltaDeg);
    }

    public void adjustZNear(float deltaNear) {
        setZNear(this.zNear + deltaNear);
    }

    public void adjustZFar(float deltaFar) {
        setZFar(this.zFar + deltaFar);
    }

    public void reset(float aspect) {
        this.fovDegree = 45.0f;
        this.aspectRatio = Math.max(0.1f, aspect);
        this.zNear = 0.2f;
        this.zFar = 50.0f;
    }

    // Getter & Setter untuk pengujian interaktif FOV dan Frustum Clipping
    public float getFovDegree() { return fovDegree; }
    public void setFovDegree(float fovDegree) { this.fovDegree = clampFov(fovDegree); }

    public float getAspectRatio() { return aspectRatio; }
    public void setAspectRatio(float aspectRatio) { this.aspectRatio = Math.max(0.1f, aspectRatio); }

    public float getZNear() { return zNear; }
    public void setZNear(float zNear) {
        this.zNear = Math.max(0.05f, Math.min(this.zFar - 0.5f, zNear));
    }

    public float getZFar() { return zFar; }
    public void setZFar(float zFar) {
        this.zFar = Math.max(this.zNear + 0.5f, Math.min(200.0f, zFar));
    }
}
