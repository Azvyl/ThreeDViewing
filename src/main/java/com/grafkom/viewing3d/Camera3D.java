package com.grafkom.viewing3d;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Praktikum 1 (Indikator 1): Kelas Encapsulated Camera3D & LookAt Matrix.
 * Mengenkapsulasi parameter Synthetic Camera Model (eye, target, up),
 * menghitung View Matrix (LookAt) secara manual menggunakan basis ortonormal (u, v, n),
 * serta mendukung navigasi halus untuk mode Orbital (Turntable) dan First-Person (FPS).
 */
public class Camera3D {

    public enum CameraMode {
        ORBITAL,
        FPS
    }

    private final Vector3f eye;
    private final Vector3f target;
    private final Vector3f up;

    // Parameter Kamera Orbital (Turntable)
    private float radius = 8.5f;
    private float azimuthDeg = 35.0f;
    private float elevationDeg = 25.0f;

    // Parameter Kamera First-Person (FPS)
    private float yawDeg = -145.0f;
    private float pitchDeg = -20.0f;
    private float moveSpeed = 4.5f;
    private float mouseSensitivity = 0.22f;

    private CameraMode mode = CameraMode.ORBITAL;

    public Camera3D(Vector3f eye, Vector3f target, Vector3f up) {
        this.eye = new Vector3f(eye);
        this.target = new Vector3f(target);
        this.up = new Vector3f(up);
        syncSphericalFromEye();
    }

    /**
     * Perhitungan manual Matriks LookAt (View Matrix) sesuai persamaan pada Modul Bagian I.1.3:
     * n = (E - At) / ||E - At||
     * u = (Up x n) / ||Up x n||
     * v = n x u
     */
    public Matrix4f calculateLookAtMatrix() {
        return calculateLookAtMatrixForEye(this.eye, this.target, this.up);
    }

    /**
     * Menghitung LookAt Matrix untuk posisi mata spesifik (digunakan juga pada Stereo Off-Axis).
     */
    public Matrix4f calculateLookAtMatrixForEye(Vector3f eyePos, Vector3f targetPos, Vector3f upVec) {
        Vector3f n = new Vector3f();
        eyePos.sub(targetPos, n);
        if (n.lengthSquared() < 1e-8f) {
            n.set(0.0f, 0.0f, 1.0f);
        } else {
            n.normalize(); // View Plane Normal (menunjuk berlawanan arah pandang)
        }

        Vector3f u = new Vector3f();
        upVec.cross(n, u);
        if (u.lengthSquared() < 1e-8f) {
            // Fallback jika arah pandang sejajar dengan vektor Up dunia
            new Vector3f(0.0f, 0.0f, 1.0f).cross(n, u);
        }
        u.normalize(); // Side / Right Vector

        Vector3f v = new Vector3f();
        n.cross(u, v).normalize(); // True Up Vector (ortogonal sempurna terhadap n dan u)

        Matrix4f viewMatrix = new Matrix4f();
        viewMatrix.m00(u.x).m10(u.y).m20(u.z).m30(-u.dot(eyePos));
        viewMatrix.m01(v.x).m11(v.y).m21(v.z).m31(-v.dot(eyePos));
        viewMatrix.m02(n.x).m12(n.y).m22(n.z).m32(-n.dot(eyePos));
        viewMatrix.m03(0.0f).m13(0.0f).m23(0.0f).m33(1.0f);

        return viewMatrix;
    }

    /**
     * Update koordinat sferis untuk kamera orbital sesuai spesifikasi modul Praktikum 1.
     */
    public void updateOrbitalPosition(float radius, float azimuthDeg, float elevationDeg) {
        this.radius = Math.max(1.5f, Math.min(40.0f, radius));
        this.azimuthDeg = azimuthDeg % 360.0f;
        this.elevationDeg = Math.max(-89.0f, Math.min(89.0f, elevationDeg));

        float azRad = (float) Math.toRadians(this.azimuthDeg);
        float elRad = (float) Math.toRadians(this.elevationDeg);

        this.eye.x = this.target.x + this.radius * (float) (Math.cos(elRad) * Math.sin(azRad));
        this.eye.y = this.target.y + this.radius * (float) (Math.sin(elRad));
        this.eye.z = this.target.z + this.radius * (float) (Math.cos(elRad) * Math.cos(azRad));
    }

    /**
     * Rotasi orbital interaktif menggunakan perubahan sudut azimuth dan elevasi.
     */
    public void rotateOrbit(float deltaAzimuthDeg, float deltaElevationDeg) {
        updateOrbitalPosition(this.radius, this.azimuthDeg + deltaAzimuthDeg, this.elevationDeg + deltaElevationDeg);
    }

    /**
     * Zoom jarak radius kamera orbital.
     */
    public void zoomOrbit(float deltaRadius) {
        updateOrbitalPosition(this.radius + deltaRadius, this.azimuthDeg, this.elevationDeg);
    }

    /**
     * Menggeser titik target (Pan) pada bidang kamera untuk navigasi orbital yang mulus.
     */
    public void panTarget(float deltaRight, float deltaUp) {
        Vector3f right = getRightVector();
        Vector3f trueUp = getTrueUpVector();

        Vector3f offset = new Vector3f();
        right.mul(deltaRight, offset);
        offset.fma(deltaUp, trueUp);

        this.target.add(offset);
        this.eye.add(offset);
    }

    /**
     * Rotasi sudut Euler (Yaw & Pitch) untuk mode First-Person Camera (FPS).
     */
    public void rotateFPS(float deltaYawDeg, float deltaPitchDeg) {
        this.yawDeg = (this.yawDeg + deltaYawDeg) % 360.0f;
        this.pitchDeg = Math.max(-89.0f, Math.min(89.0f, this.pitchDeg + deltaPitchDeg));
        updateFPSTarget();
    }

    /**
     * Pergerakan bebas posisi kamera dalam mode First-Person Camera (FPS).
     */
    public void moveFPS(float forwardAmount, float rightAmount, float upAmount) {
        Vector3f front = getForwardVector();
        Vector3f right = getRightVector();
        Vector3f worldUp = new Vector3f(0.0f, 1.0f, 0.0f);

        Vector3f displacement = new Vector3f();
        displacement.fma(forwardAmount, front);
        displacement.fma(rightAmount, right);
        displacement.fma(upAmount, worldUp);

        this.eye.add(displacement);
        updateFPSTarget();
    }

    private void updateFPSTarget() {
        float yawRad = (float) Math.toRadians(this.yawDeg);
        float pitchRad = (float) Math.toRadians(this.pitchDeg);

        Vector3f front = new Vector3f(
                (float) (Math.cos(pitchRad) * Math.cos(yawRad)),
                (float) Math.sin(pitchRad),
                (float) (Math.cos(pitchRad) * Math.sin(yawRad))
        ).normalize();

        this.eye.add(front, this.target);
    }

    /**
     * Beralih antara mode kamera ORBITAL dan FPS tanpa lompatan orientasi.
     */
    public void toggleCameraMode() {
        if (this.mode == CameraMode.ORBITAL) {
            this.mode = CameraMode.FPS;
            Vector3f dir = getForwardVector();
            this.pitchDeg = (float) Math.toDegrees(Math.asin(Math.max(-1.0f, Math.min(1.0f, dir.y))));
            this.yawDeg = (float) Math.toDegrees(Math.atan2(dir.z, dir.x));
            updateFPSTarget();
        } else {
            this.mode = CameraMode.ORBITAL;
            this.target.set(0.0f, 0.0f, 0.0f);
            syncSphericalFromEye();
            updateOrbitalPosition(this.radius, this.azimuthDeg, this.elevationDeg);
        }
    }

    /**
     * Reset posisi dan orientasi kamera ke pengaturan awal.
     */
    public void reset() {
        this.mode = CameraMode.ORBITAL;
        this.target.set(0.0f, 0.0f, 0.0f);
        this.up.set(0.0f, 1.0f, 0.0f);
        this.radius = 8.5f;
        this.azimuthDeg = 35.0f;
        this.elevationDeg = 25.0f;
        updateOrbitalPosition(this.radius, this.azimuthDeg, this.elevationDeg);
    }

    private void syncSphericalFromEye() {
        Vector3f rel = new Vector3f();
        this.eye.sub(this.target, rel);
        this.radius = Math.max(1.5f, rel.length());
        this.elevationDeg = (float) Math.toDegrees(Math.asin(rel.y / this.radius));
        this.azimuthDeg = (float) Math.toDegrees(Math.atan2(rel.x, rel.z));
    }

    public Vector3f getForwardVector() {
        Vector3f forward = new Vector3f();
        this.target.sub(this.eye, forward);
        if (forward.lengthSquared() < 1e-8f) {
            return new Vector3f(0.0f, 0.0f, -1.0f);
        }
        return forward.normalize();
    }

    public Vector3f getRightVector() {
        Vector3f n = new Vector3f();
        this.eye.sub(this.target, n).normalize();
        Vector3f u = new Vector3f();
        this.up.cross(n, u);
        if (u.lengthSquared() < 1e-8f) {
            return new Vector3f(1.0f, 0.0f, 0.0f);
        }
        return u.normalize();
    }

    public Vector3f getTrueUpVector() {
        Vector3f n = new Vector3f();
        this.eye.sub(this.target, n).normalize();
        Vector3f u = getRightVector();
        Vector3f v = new Vector3f();
        n.cross(u, v).normalize();
        return v;
    }

    public float[] getLookAtMatrixFloatArray() {
        float[] arr = new float[16];
        calculateLookAtMatrix().get(arr);
        return arr;
    }

    // Getter & Setter terenkapsulasi
    public Vector3f getEye() { return new Vector3f(eye); }
    public void setEye(Vector3f eye) { this.eye.set(eye); }

    public Vector3f getTarget() { return new Vector3f(target); }
    public void setTarget(Vector3f target) { this.target.set(target); }

    public Vector3f getUp() { return new Vector3f(up); }
    public void setUp(Vector3f up) { this.up.set(up); }

    public float getRadius() { return radius; }
    public void setRadius(float radius) { updateOrbitalPosition(radius, this.azimuthDeg, this.elevationDeg); }

    public float getAzimuthDeg() { return azimuthDeg; }
    public float getElevationDeg() { return elevationDeg; }
    public float getYawDeg() { return yawDeg; }
    public float getPitchDeg() { return pitchDeg; }
    public float getMoveSpeed() { return moveSpeed; }
    public float getMouseSensitivity() { return mouseSensitivity; }
    public CameraMode getMode() { return mode; }
}
