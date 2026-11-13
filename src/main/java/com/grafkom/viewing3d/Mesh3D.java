package com.grafkom.viewing3d;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Spesifikasi Tugas Mandiri Minggu 11 (Indikator 1, 2, 3, 4, & 5): Kelas Mesh3D.
 * Membangun dan merender model-model 3D bermateri berbasis VAO/VBO Modern OpenGL (GLSL 330 core)
 * lengkap dengan Koordinat Vektor Normal Vertex (Flat Shading vs Smooth Shading Vertex Normal Averaging)
 * dan Koordinat Tekstur UV (u, v):
 * 1. Model Rumah 3D Arsitektural (Dinding Bata, Atap Pelana Genteng, Pintu/Jendela, Cerobong)
 * 2. Model 3D Teapot Kompleks (Badan Kurva Revolusi, Tutup, Cerat/Spout, dan Gagang/Handle)
 * 3. Model 3D UV Sphere Presisi Tinggi (untuk menguji Specular Phong vs Blinn-Phong & Mach Band Effect)
 * 4. Model 3D Textured Cube & Bidang Lantai Luas (Ground Plane penerima bayangan Shadow Mapping)
 */
public class Mesh3D {

    public enum SceneModel {
        VISUAL_REALISM_SUITE("Suite Lengkap: Rumah 3D + Teapot + Sphere + Cube + Lantai"),
        TEAPOT_AND_SPHERE("Fokus Kurva: 3D Teapot & UV Sphere (Uji Shading & Normal)"),
        HOUSE_3D_ARCHITECTURAL("Fokus Arsitektur: Rumah 3D Bertekstur & Ground Plane"),
        CUBES_SHADOW_TEST("Fokus Multi-Kubus & Uji Kedalaman Bayangan");

        private final String title;

        SceneModel(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }
    }

    /**
     * Struktur internal satu segitiga (face) dengan 3 posisi, 3 UV, dan petunjuk arah normal keluar
     * untuk menjamin urutan vertex Counter-Clockwise (GL_CCW) pada Back-Face Culling.
     */
    private static class TriangleFace {
        final Vector3f p0, p1, p2;
        final Vector2f uv0, uv1, uv2;
        final Vector3f faceNormal;
        final Vector3f smoothN0, smoothN1, smoothN2;

        TriangleFace(Vector3f v0, Vector3f v1, Vector3f v2,
                     Vector2f t0, Vector2f t1, Vector2f t2,
                     Vector3f outwardHint,
                     Vector3f customN0, Vector3f customN1, Vector3f customN2) {

            Vector3f edge1 = new Vector3f(v1).sub(v0);
            Vector3f edge2 = new Vector3f(v2).sub(v0);
            Vector3f n = new Vector3f();
            edge1.cross(edge2, n);
            if (n.lengthSquared() < 1e-10f) {
                n.set(outwardHint != null ? outwardHint : new Vector3f(0, 1, 0));
            } else {
                n.normalize();
            }

            // Pastikan urutan vertex selalu Counter-Clockwise (GL_CCW) terhadap arah luar permukaan
            if (outwardHint != null && n.dot(outwardHint) < 0.0f) {
                this.p0 = new Vector3f(v0);
                this.p1 = new Vector3f(v2);
                this.p2 = new Vector3f(v1);
                this.uv0 = new Vector2f(t0);
                this.uv1 = new Vector2f(t2);
                this.uv2 = new Vector2f(t1);
                this.faceNormal = n.negate();
                this.smoothN0 = customN0 != null ? new Vector3f(customN0).normalize() : null;
                this.smoothN1 = customN2 != null ? new Vector3f(customN2).normalize() : null;
                this.smoothN2 = customN1 != null ? new Vector3f(customN1).normalize() : null;
            } else {
                this.p0 = new Vector3f(v0);
                this.p1 = new Vector3f(v1);
                this.p2 = new Vector3f(v2);
                this.uv0 = new Vector2f(t0);
                this.uv1 = new Vector2f(t1);
                this.uv2 = new Vector2f(t2);
                this.faceNormal = n;
                this.smoothN0 = customN0 != null ? new Vector3f(customN0).normalize() : null;
                this.smoothN1 = customN1 != null ? new Vector3f(customN1).normalize() : null;
                this.smoothN2 = customN2 != null ? new Vector3f(customN2).normalize() : null;
            }
        }
    }

    /**
     * Enkapsulasi VAO/VBO untuk satu bagian geometri dengan pasangan buffer Flat Shading dan Smooth Shading.
     */
    private static class GlMeshBuffer {
        final int vaoFlat;
        final int vboFlat;
        final int vaoSmooth;
        final int vboSmooth;
        final int vertexCount;

        GlMeshBuffer(List<TriangleFace> faces) {
            this.vertexCount = faces.size() * 3;

            // Hitung Vertex Normal Averaging: N_vertex = sum(N_face_i) / ||sum(N_face_i)||
            // untuk seluruh vertex yang berbagi koordinat posisi 3D yang sama (Indikator 2.b)
            Map<String, Vector3f> averagedNormals = new HashMap<>();
            for (TriangleFace f : faces) {
                accumulateNormal(averagedNormals, f.p0, f.faceNormal);
                accumulateNormal(averagedNormals, f.p1, f.faceNormal);
                accumulateNormal(averagedNormals, f.p2, f.faceNormal);
            }
            for (Vector3f sumNorm : averagedNormals.values()) {
                if (sumNorm.lengthSquared() > 1e-8f) {
                    sumNorm.normalize();
                } else {
                    sumNorm.set(0.0f, 1.0f, 0.0f);
                }
            }

            float[] flatData = new float[vertexCount * 8];
            float[] smoothData = new float[vertexCount * 8];

            int idx = 0;
            for (TriangleFace f : faces) {
                Vector3f sn0 = f.smoothN0 != null ? f.smoothN0 : averagedNormals.getOrDefault(posKey(f.p0), f.faceNormal);
                Vector3f sn1 = f.smoothN1 != null ? f.smoothN1 : averagedNormals.getOrDefault(posKey(f.p1), f.faceNormal);
                Vector3f sn2 = f.smoothN2 != null ? f.smoothN2 : averagedNormals.getOrDefault(posKey(f.p2), f.faceNormal);

                putVertex(flatData, idx, f.p0, f.faceNormal, f.uv0);
                putVertex(smoothData, idx, f.p0, sn0, f.uv0);
                idx += 8;

                putVertex(flatData, idx, f.p1, f.faceNormal, f.uv1);
                putVertex(smoothData, idx, f.p1, sn1, f.uv1);
                idx += 8;

                putVertex(flatData, idx, f.p2, f.faceNormal, f.uv2);
                putVertex(smoothData, idx, f.p2, sn2, f.uv2);
                idx += 8;
            }

            int[] flatIds = createVaoVbo(flatData);
            this.vaoFlat = flatIds[0];
            this.vboFlat = flatIds[1];

            int[] smoothIds = createVaoVbo(smoothData);
            this.vaoSmooth = smoothIds[0];
            this.vboSmooth = smoothIds[1];
        }

        private static String posKey(Vector3f p) {
            int kx = Math.round(p.x * 1000.0f);
            int ky = Math.round(p.y * 1000.0f);
            int kz = Math.round(p.z * 1000.0f);
            return kx + "_" + ky + "_" + kz;
        }

        private static void accumulateNormal(Map<String, Vector3f> map, Vector3f pos, Vector3f faceNorm) {
            String key = posKey(pos);
            Vector3f acc = map.computeIfAbsent(key, k -> new Vector3f());
            acc.add(faceNorm);
        }

        private static void putVertex(float[] arr, int offset, Vector3f p, Vector3f n, Vector2f uv) {
            arr[offset]     = p.x;
            arr[offset + 1] = p.y;
            arr[offset + 2] = p.z;
            arr[offset + 3] = n.x;
            arr[offset + 4] = n.y;
            arr[offset + 5] = n.z;
            arr[offset + 6] = uv.x;
            arr[offset + 7] = uv.y;
        }

        private static int[] createVaoVbo(float[] vertexData) {
            int vao = glGenVertexArrays();
            int vbo = glGenBuffers();

            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, vbo);

            FloatBuffer fb = MemoryUtil.memAllocFloat(vertexData.length);
            fb.put(vertexData).flip();
            glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
            MemoryUtil.memFree(fb);

            int stride = 8 * Float.BYTES;
            // location = 0: aPos (vec3)
            glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
            glEnableVertexAttribArray(0);
            // location = 1: aNormal (vec3)
            glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
            glEnableVertexAttribArray(1);
            // location = 2: aTexCoords (vec2)
            glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 6L * Float.BYTES);
            glEnableVertexAttribArray(2);

            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
            return new int[]{vao, vbo};
        }

        void draw(boolean smoothShading) {
            glBindVertexArray(smoothShading ? vaoSmooth : vaoFlat);
            glDrawArrays(GL_TRIANGLES, 0, vertexCount);
            glBindVertexArray(0);
        }

        void cleanup() {
            glDeleteVertexArrays(vaoFlat);
            glDeleteBuffers(vboFlat);
            glDeleteVertexArrays(vaoSmooth);
            glDeleteBuffers(vboSmooth);
        }
    }

    private SceneModel currentModel = SceneModel.VISUAL_REALISM_SUITE;
    private boolean animateObjects = true;
    private boolean showGridAndAxes = false;
    private float animationAngle = 0.0f;

    // Buffer VAO/VBO untuk masing-masing komponen mesh
    private GlMeshBuffer groundPlaneMesh;
    private GlMeshBuffer cubeMesh;
    private GlMeshBuffer sphereMesh;
    private GlMeshBuffer teapotMesh;
    private GlMeshBuffer houseWallsMesh;
    private GlMeshBuffer houseRoofMesh;
    private GlMeshBuffer houseDetailsMesh;

    public Mesh3D() {
        initAllMeshes();
    }

    private void initAllMeshes() {
        this.groundPlaneMesh = new GlMeshBuffer(buildGroundPlaneFaces(11.0f, -0.002f));
        this.cubeMesh = new GlMeshBuffer(buildUnitCubeFaces(1.0f));
        this.sphereMesh = new GlMeshBuffer(buildUvSphereFaces(1.0f, 28, 18));
        this.teapotMesh = new GlMeshBuffer(buildProceduralTeapotFaces());
        this.houseWallsMesh = new GlMeshBuffer(buildHouseWallsFaces());
        this.houseRoofMesh = new GlMeshBuffer(buildHouseRoofFaces());
        this.houseDetailsMesh = new GlMeshBuffer(buildHouseDetailsFaces());
    }

    public void updateAnimation(float deltaTimeSec) {
        if (animateObjects) {
            animationAngle = (animationAngle + deltaTimeSec * 26.0f) % 360.0f;
        }
    }

    /**
     * Merender seluruh objek 3D menggunakan GLSL Shader (baik untuk Pass 1 Shadow Depth maupun Pass 2 Lighting).
     */
    public void renderShaderScene(
            ShaderProgram shader,
            boolean smoothShading,
            boolean useTexture,
            int activeTexturePreset,
            Texture2D brickTex,
            Texture2D roofTex,
            Texture2D checkerTex,
            Texture2D floorTex,
            Vector3f lightPos,
            boolean isDepthPass) {

        Matrix4f model = new Matrix4f();

        // 1. Render Bidang Lantai (Ground Plane) penerima bayangan (Y = 0)
        model.identity();
        shader.setUniform("model", model);
        if (!isDepthPass) {
            Texture2D chosenFloor = (activeTexturePreset == 1) ? checkerTex : floorTex;
            chosenFloor.bind(0);
            shader.setUniform("useTexture", useTexture);
            shader.setUniform("uvScale", 6.0f);
            shader.setUniform("objectColor", new Vector3f(0.92f, 0.94f, 0.96f));
        }
        groundPlaneMesh.draw(smoothShading);

        // 2. Render Objek 3D Berdasarkan Skenario Scene Aktif
        switch (currentModel) {
            case VISUAL_REALISM_SUITE:
                // (a) Rumah 3D di tengah-belakang
                renderHouse3D(shader, smoothShading, useTexture, activeTexturePreset,
                        brickTex, roofTex, checkerTex, isDepthPass,
                        new Matrix4f().translation(0.0f, 0.0f, -0.8f));

                // (b) Model 3D Teapot Berputar di Kiri Depan (sangat jelas untuk uji Flat/Smooth & Specular)
                model.identity()
                        .translate(-2.45f, 0.0f, 1.85f)
                        .rotateY((float) Math.toRadians(animationAngle))
                        .scale(0.95f);
                drawSingleMesh(shader, teapotMesh, model, smoothShading, useTexture,
                        (activeTexturePreset == 1) ? checkerTex : brickTex,
                        1.0f, new Vector3f(0.92f, 0.68f, 0.28f), isDepthPass);

                // (c) Model 3D UV Sphere di Kanan Depan (sangat jelas untuk uji Phong vs Blinn-Phong & Mach Band)
                model.identity()
                        .translate(2.45f, 1.05f, 1.85f)
                        .rotateY((float) Math.toRadians(-animationAngle * 0.8f));
                drawSingleMesh(shader, sphereMesh, model, smoothShading, useTexture,
                        checkerTex,
                        1.0f, new Vector3f(0.32f, 0.72f, 0.96f), isDepthPass);

                // (d) Kubus Bertekstur Melayang di Kiri Atas (menghasilkan bayangan jelas di lantai)
                model.identity()
                        .translate(-2.2f, 2.35f, -1.6f)
                        .rotateY((float) Math.toRadians(animationAngle * 1.1f))
                        .rotateX((float) Math.toRadians(22.0f))
                        .scale(0.62f);
                drawSingleMesh(shader, cubeMesh, model, smoothShading, useTexture,
                        (activeTexturePreset == 1) ? checkerTex : brickTex,
                        1.0f, new Vector3f(0.95f, 0.85f, 0.80f), isDepthPass);
                break;

            case TEAPOT_AND_SPHERE:
                // Teapot Utama Besar di Kiri Pusat
                model.identity()
                        .translate(-1.65f, 0.0f, 0.4f)
                        .rotateY((float) Math.toRadians(animationAngle * 0.7f))
                        .scale(1.35f);
                drawSingleMesh(shader, teapotMesh, model, smoothShading, useTexture,
                        checkerTex,
                        1.0f, new Vector3f(0.92f, 0.56f, 0.24f), isDepthPass);

                // Sphere Utama Besar di Kanan Pusat
                model.identity()
                        .translate(1.75f, 1.35f, 0.4f)
                        .rotateY((float) Math.toRadians(-animationAngle * 0.6f))
                        .scale(1.30f);
                drawSingleMesh(shader, sphereMesh, model, smoothShading, useTexture,
                        checkerTex,
                        1.0f, new Vector3f(0.28f, 0.72f, 0.96f), isDepthPass);
                break;

            case HOUSE_3D_ARCHITECTURAL:
                renderHouse3D(shader, smoothShading, useTexture, activeTexturePreset,
                        brickTex, roofTex, checkerTex, isDepthPass,
                        new Matrix4f().identity().scale(1.25f));
                break;

            case CUBES_SHADOW_TEST:
                model.identity()
                        .translate(0.0f, 1.15f, 0.0f)
                        .rotateY((float) Math.toRadians(animationAngle * 0.6f))
                        .scale(1.1f);
                drawSingleMesh(shader, cubeMesh, model, smoothShading, useTexture,
                        brickTex, 1.0f, new Vector3f(0.95f, 0.92f, 0.90f), isDepthPass);

                model.identity()
                        .translate(-2.4f, 1.6f, 1.6f)
                        .rotateY((float) Math.toRadians(-animationAngle))
                        .rotateZ((float) Math.toRadians(25.0f))
                        .scale(0.68f);
                drawSingleMesh(shader, cubeMesh, model, smoothShading, useTexture,
                        checkerTex, 1.0f, new Vector3f(0.88f, 0.94f, 1.0f), isDepthPass);

                model.identity()
                        .translate(2.3f, 0.85f, -1.5f)
                        .rotateY((float) Math.toRadians(animationAngle * 0.9f))
                        .scale(0.82f);
                drawSingleMesh(shader, cubeMesh, model, smoothShading, useTexture,
                        roofTex, 1.0f, new Vector3f(1.0f, 0.86f, 0.80f), isDepthPass);
                break;
        }

        // 3. Render Penanda Visual Posisi Sumber Cahaya (Hanya pada Pass 2 Kamera)
        if (!isDepthPass && lightPos != null) {
            model.identity().translate(lightPos).scale(0.18f);
            shader.setUniform("model", model);
            shader.setUniform("useTexture", false);
            shader.setUniform("uvScale", 1.0f);
            shader.setUniform("objectColor", new Vector3f(1.0f, 0.96f, 0.65f));
            sphereMesh.draw(true);
        }
    }

    private void renderHouse3D(
            ShaderProgram shader,
            boolean smoothShading,
            boolean useTexture,
            int activeTexturePreset,
            Texture2D brickTex,
            Texture2D roofTex,
            Texture2D checkerTex,
            boolean isDepthPass,
            Matrix4f baseTransform) {

        // Dinding Utama Rumah
        drawSingleMesh(shader, houseWallsMesh, baseTransform, smoothShading, useTexture,
                (activeTexturePreset == 1) ? checkerTex : brickTex,
                2.0f, new Vector3f(0.96f, 0.93f, 0.88f), isDepthPass);

        // Atap Pelana Rumah & Cerobong
        drawSingleMesh(shader, houseRoofMesh, baseTransform, smoothShading, useTexture,
                (activeTexturePreset == 1) ? checkerTex : roofTex,
                2.0f, new Vector3f(0.92f, 0.52f, 0.42f), isDepthPass);

        // Pintu & Jendela Rumah
        drawSingleMesh(shader, houseDetailsMesh, baseTransform, smoothShading, false,
                brickTex,
                1.0f, new Vector3f(0.36f, 0.64f, 0.86f), isDepthPass);
    }

    private void drawSingleMesh(
            ShaderProgram shader,
            GlMeshBuffer mesh,
            Matrix4f modelMatrix,
            boolean smoothShading,
            boolean useTexture,
            Texture2D texture,
            float uvScale,
            Vector3f color,
            boolean isDepthPass) {

        shader.setUniform("model", modelMatrix);
        if (!isDepthPass) {
            if (texture != null) {
                texture.bind(0);
            }
            shader.setUniform("useTexture", useTexture);
            shader.setUniform("uvScale", uvScale);
            shader.setUniform("objectColor", color);
        }
        mesh.draw(smoothShading);
    }

    // =========================================================================
    // PEMBANGUN GEOMETRI 3D DENGAN NORMAL & KOORDINAT UV (COUNTER-CLOCKWISE)
    // =========================================================================

    private static void addQuad(
            List<TriangleFace> list,
            Vector3f v0, Vector3f v1, Vector3f v2, Vector3f v3,
            Vector2f uv0, Vector2f uv1, Vector2f uv2, Vector2f uv3,
            Vector3f outwardHint) {
        list.add(new TriangleFace(v0, v1, v2, uv0, uv1, uv2, outwardHint, null, null, null));
        list.add(new TriangleFace(v0, v2, v3, uv0, uv2, uv3, outwardHint, null, null, null));
    }

    private static List<TriangleFace> buildGroundPlaneFaces(float halfExtent, float yLevel) {
        List<TriangleFace> faces = new ArrayList<>();
        // Subdivisi lantai 8x8 agar interpolasi pencahayaan Gouraud & bayangan di atas lantai sangat presisi
        int divs = 8;
        float step = (halfExtent * 2.0f) / divs;
        Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f);

        for (int r = 0; r < divs; r++) {
            for (int c = 0; c < divs; c++) {
                float x0 = -halfExtent + c * step;
                float x1 = x0 + step;
                float z0 = -halfExtent + r * step;
                float z1 = z0 + step;

                float u0 = (float) c / divs;
                float u1 = (float) (c + 1) / divs;
                float v0 = (float) r / divs;
                float v1 = (float) (r + 1) / divs;

                addQuad(faces,
                        new Vector3f(x0, yLevel, z1),
                        new Vector3f(x1, yLevel, z1),
                        new Vector3f(x1, yLevel, z0),
                        new Vector3f(x0, yLevel, z0),
                        new Vector2f(u0, v0),
                        new Vector2f(u1, v0),
                        new Vector2f(u1, v1),
                        new Vector2f(u0, v1),
                        up);
            }
        }
        return faces;
    }

    private static List<TriangleFace> buildUnitCubeFaces(float s) {
        List<TriangleFace> faces = new ArrayList<>();
        Vector2f uv00 = new Vector2f(0.0f, 0.0f);
        Vector2f uv10 = new Vector2f(1.0f, 0.0f);
        Vector2f uv11 = new Vector2f(1.0f, 1.0f);
        Vector2f uv01 = new Vector2f(0.0f, 1.0f);

        // Depan (+Z)
        addQuad(faces,
                new Vector3f(-s, -s, s), new Vector3f(s, -s, s),
                new Vector3f(s, s, s), new Vector3f(-s, s, s),
                uv00, uv10, uv11, uv01, new Vector3f(0, 0, 1));
        // Belakang (-Z)
        addQuad(faces,
                new Vector3f(s, -s, -s), new Vector3f(-s, -s, -s),
                new Vector3f(-s, s, -s), new Vector3f(s, s, -s),
                uv00, uv10, uv11, uv01, new Vector3f(0, 0, -1));
        // Kanan (+X)
        addQuad(faces,
                new Vector3f(s, -s, s), new Vector3f(s, -s, -s),
                new Vector3f(s, s, -s), new Vector3f(s, s, s),
                uv00, uv10, uv11, uv01, new Vector3f(1, 0, 0));
        // Kiri (-X)
        addQuad(faces,
                new Vector3f(-s, -s, -s), new Vector3f(-s, -s, s),
                new Vector3f(-s, s, s), new Vector3f(-s, s, -s),
                uv00, uv10, uv11, uv01, new Vector3f(-1, 0, 0));
        // Atas (+Y)
        addQuad(faces,
                new Vector3f(-s, s, s), new Vector3f(s, s, s),
                new Vector3f(s, s, -s), new Vector3f(-s, s, -s),
                uv00, uv10, uv11, uv01, new Vector3f(0, 1, 0));
        // Bawah (-Y)
        addQuad(faces,
                new Vector3f(-s, -s, -s), new Vector3f(s, -s, -s),
                new Vector3f(s, -s, s), new Vector3f(-s, -s, s),
                uv00, uv10, uv11, uv01, new Vector3f(0, -1, 0));

        return faces;
    }

    /**
     * Membangun UV Sphere 3D dengan normal analitis halus untuk Smooth Shading
     * dan face normal diskrit untuk Flat Shading.
     */
    private static List<TriangleFace> buildUvSphereFaces(float radius, int sectors, int stacks) {
        List<TriangleFace> faces = new ArrayList<>();

        for (int i = 0; i < stacks; ++i) {
            float v0 = (float) i / stacks;
            float v1 = (float) (i + 1) / stacks;
            float phi0 = (float) (Math.PI * (v0 - 0.5f));
            float phi1 = (float) (Math.PI * (v1 - 0.5f));

            for (int j = 0; j < sectors; ++j) {
                float u0 = (float) j / sectors;
                float u1 = (float) (j + 1) / sectors;
                float theta0 = (float) (2.0 * Math.PI * u0);
                float theta1 = (float) (2.0 * Math.PI * u1);

                Vector3f n00 = sphereNormal(phi0, theta0);
                Vector3f n10 = sphereNormal(phi0, theta1);
                Vector3f n11 = sphereNormal(phi1, theta1);
                Vector3f n01 = sphereNormal(phi1, theta0);

                Vector3f p00 = new Vector3f(n00).mul(radius);
                Vector3f p10 = new Vector3f(n10).mul(radius);
                Vector3f p11 = new Vector3f(n11).mul(radius);
                Vector3f p01 = new Vector3f(n01).mul(radius);

                Vector2f uv00 = new Vector2f(u0, v0);
                Vector2f uv10 = new Vector2f(u1, v0);
                Vector2f uv11 = new Vector2f(u1, v1);
                Vector2f uv01 = new Vector2f(u0, v1);

                Vector3f hint = new Vector3f(n00).add(n10).add(n11).add(n01).normalize();

                if (i > 0) {
                    faces.add(new TriangleFace(p00, p10, p11, uv00, uv10, uv11, hint, n00, n10, n11));
                }
                if (i < stacks - 1) {
                    faces.add(new TriangleFace(p00, p11, p01, uv00, uv11, uv01, hint, n00, n11, n01));
                }
            }
        }
        return faces;
    }

    private static Vector3f sphereNormal(float phi, float theta) {
        float cosPhi = (float) Math.cos(phi);
        float x = cosPhi * (float) Math.cos(theta);
        float y = (float) Math.sin(phi);
        float z = -cosPhi * (float) Math.sin(theta);
        return new Vector3f(x, y, z).normalize();
    }

    /**
     * Membangun Model 3D Teapot Kompleks (Badan Revolusi + Tutup + Kenop + Cerat + Gagang)
     * sesuai spesifikasi Tugas Mandiri III.1.a.
     */
    private static List<TriangleFace> buildProceduralTeapotFaces() {
        List<TriangleFace> faces = new ArrayList<>();

        // 1. Profil Kurva Revolusi Badan Teapot + Tutup Atas + Kenop (r, y)
        float[][] profile = {
                {0.00f, 0.02f},
                {0.58f, 0.02f},
                {0.76f, 0.18f},
                {0.92f, 0.45f},
                {0.95f, 0.72f},
                {0.82f, 0.98f},
                {0.62f, 1.15f},
                {0.54f, 1.20f},
                {0.44f, 1.26f},
                {0.24f, 1.32f},
                {0.10f, 1.36f},
                {0.16f, 1.44f},
                {0.12f, 1.50f},
                {0.00f, 1.52f}
        };

        int slices = 32;
        for (int i = 0; i < profile.length - 1; i++) {
            float r0 = profile[i][0];
            float y0 = profile[i][1];
            float r1 = profile[i + 1][0];
            float y1 = profile[i + 1][1];

            float v0 = (float) i / (profile.length - 1);
            float v1 = (float) (i + 1) / (profile.length - 1);

            for (int j = 0; j < slices; j++) {
                float u0 = (float) j / slices;
                float u1 = (float) (j + 1) / slices;
                float a0 = (float) (2.0 * Math.PI * u0);
                float a1 = (float) (2.0 * Math.PI * u1);

                Vector3f p00 = new Vector3f(r0 * (float) Math.cos(a0), y0, -r0 * (float) Math.sin(a0));
                Vector3f p10 = new Vector3f(r0 * (float) Math.cos(a1), y0, -r0 * (float) Math.sin(a1));
                Vector3f p11 = new Vector3f(r1 * (float) Math.cos(a1), y1, -r1 * (float) Math.sin(a1));
                Vector3f p01 = new Vector3f(r1 * (float) Math.cos(a0), y1, -r1 * (float) Math.sin(a0));

                float midA = 0.5f * (a0 + a1);
                Vector3f hint = new Vector3f((float) Math.cos(midA), (r0 - r1), -(float) Math.sin(midA));
                if (i == 0) {
                    hint.set(0, -1, 0);
                } else if (i == profile.length - 2) {
                    hint.set(0, 1, 0);
                }
                hint.normalize();

                Vector2f uv00 = new Vector2f(u0 * 2.0f, v0 * 2.0f);
                Vector2f uv10 = new Vector2f(u1 * 2.0f, v0 * 2.0f);
                Vector2f uv11 = new Vector2f(u1 * 2.0f, v1 * 2.0f);
                Vector2f uv01 = new Vector2f(u0 * 2.0f, v1 * 2.0f);

                if (r0 > 1e-4f) {
                    faces.add(new TriangleFace(p00, p10, p11, uv00, uv10, uv11, hint, null, null, null));
                }
                if (r1 > 1e-4f) {
                    faces.add(new TriangleFace(p00, p11, p01, uv00, uv11, uv01, hint, null, null, null));
                }
            }
        }

        // 2. Cerat Teapot (Spout) di sisi +X
        int spoutSteps = 10;
        int tubeSides = 12;
        for (int i = 0; i < spoutSteps; i++) {
            float t0 = (float) i / spoutSteps;
            float t1 = (float) (i + 1) / spoutSteps;
            Vector3f c0 = teapotSpoutCenter(t0);
            Vector3f c1 = teapotSpoutCenter(t1);
            float rad0 = 0.20f * (1.0f - 0.45f * t0);
            float rad1 = 0.20f * (1.0f - 0.45f * t1);
            addTubeSegment(faces, c0, c1, rad0, rad1, tubeSides, t0, t1);
        }

        // 3. Gagang Teapot (Handle) di sisi -X
        int handleSteps = 14;
        for (int i = 0; i < handleSteps; i++) {
            float t0 = (float) i / handleSteps;
            float t1 = (float) (i + 1) / handleSteps;
            Vector3f c0 = teapotHandleCenter(t0);
            Vector3f c1 = teapotHandleCenter(t1);
            addTubeSegment(faces, c0, c1, 0.095f, 0.095f, tubeSides, t0, t1);
        }

        return faces;
    }

    private static Vector3f teapotSpoutCenter(float t) {
        float x = 0.78f + 0.62f * t;
        float y = 0.52f + 0.68f * t + 0.12f * (float) Math.sin(t * Math.PI);
        return new Vector3f(x, y, 0.0f);
    }

    private static Vector3f teapotHandleCenter(float t) {
        float angle = (float) (Math.PI * (t - 0.5));
        float x = -0.76f - 0.46f * (float) Math.cos(angle);
        float y = 0.76f + 0.36f * (float) Math.sin(angle);
        return new Vector3f(x, y, 0.0f);
    }

    private static void addTubeSegment(
            List<TriangleFace> faces,
            Vector3f c0, Vector3f c1,
            float r0, float r1,
            int sides, float v0, float v1) {

        Vector3f tangent = new Vector3f(c1).sub(c0).normalize();
        Vector3f ref = Math.abs(tangent.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f binormal = new Vector3f(tangent).cross(ref).normalize();
        Vector3f normal = new Vector3f(binormal).cross(tangent).normalize();

        for (int s = 0; s < sides; s++) {
            float u0 = (float) s / sides;
            float u1 = (float) (s + 1) / sides;
            float a0 = (float) (2.0 * Math.PI * u0);
            float a1 = (float) (2.0 * Math.PI * u1);

            Vector3f dir0 = new Vector3f(normal).mul((float) Math.cos(a0))
                    .fma((float) Math.sin(a0), binormal).normalize();
            Vector3f dir1 = new Vector3f(normal).mul((float) Math.cos(a1))
                    .fma((float) Math.sin(a1), binormal).normalize();

            Vector3f p00 = new Vector3f(c0).fma(r0, dir0);
            Vector3f p10 = new Vector3f(c0).fma(r0, dir1);
            Vector3f p11 = new Vector3f(c1).fma(r1, dir1);
            Vector3f p01 = new Vector3f(c1).fma(r1, dir0);

            Vector3f hint = new Vector3f(dir0).add(dir1).normalize();
            addQuad(faces, p00, p10, p11, p01,
                    new Vector2f(u0, v0), new Vector2f(u1, v0),
                    new Vector2f(u1, v1), new Vector2f(u0, v1),
                    hint);
        }
    }

    /**
     * Membangun Dinding Utama & Gable Rumah 3D Arsitektural.
     */
    private static List<TriangleFace> buildHouseWallsFaces() {
        List<TriangleFace> faces = new ArrayList<>();
        float w = 1.4f;
        float d = 1.15f;
        float yBase = 0.004f; // Offset kecil di atas Y=0 agar bebas Z-Fighting terhadap Ground Plane
        float hWall = 1.5f;
        float hRoof = 2.5f;

        Vector2f uv00 = new Vector2f(0.0f, 0.0f);
        Vector2f uv10 = new Vector2f(1.0f, 0.0f);
        Vector2f uv11 = new Vector2f(1.0f, 1.0f);
        Vector2f uv01 = new Vector2f(0.0f, 1.0f);

        // Dinding Depan (+Z)
        addQuad(faces,
                new Vector3f(-w, yBase, d), new Vector3f(w, yBase, d),
                new Vector3f(w, hWall, d), new Vector3f(-w, hWall, d),
                uv00, uv10, uv11, uv01, new Vector3f(0, 0, 1));
        // Dinding Belakang (-Z)
        addQuad(faces,
                new Vector3f(w, yBase, -d), new Vector3f(-w, yBase, -d),
                new Vector3f(-w, hWall, -d), new Vector3f(w, hWall, -d),
                uv00, uv10, uv11, uv01, new Vector3f(0, 0, -1));
        // Dinding Kanan (+X)
        addQuad(faces,
                new Vector3f(w, yBase, d), new Vector3f(w, yBase, -d),
                new Vector3f(w, hWall, -d), new Vector3f(w, hWall, d),
                uv00, uv10, uv11, uv01, new Vector3f(1, 0, 0));
        // Dinding Kiri (-X)
        addQuad(faces,
                new Vector3f(-w, yBase, -d), new Vector3f(-w, yBase, d),
                new Vector3f(-w, hWall, d), new Vector3f(-w, hWall, -d),
                uv00, uv10, uv11, uv01, new Vector3f(-1, 0, 0));
        // Alas Bawah Rumah (-Y)
        addQuad(faces,
                new Vector3f(-w, yBase, -d), new Vector3f(w, yBase, -d),
                new Vector3f(w, yBase, d), new Vector3f(-w, yBase, d),
                uv00, uv10, uv11, uv01, new Vector3f(0, -1, 0));

        // Gable Segitiga Kanan (+X)
        faces.add(new TriangleFace(
                new Vector3f(w, hWall, d),
                new Vector3f(w, hWall, -d),
                new Vector3f(w, hRoof, 0.0f),
                new Vector2f(0.0f, 0.0f), new Vector2f(1.0f, 0.0f), new Vector2f(0.5f, 0.8f),
                new Vector3f(1, 0, 0), null, null, null));

        // Gable Segitiga Kiri (-X)
        faces.add(new TriangleFace(
                new Vector3f(-w, hWall, -d),
                new Vector3f(-w, hWall, d),
                new Vector3f(-w, hRoof, 0.0f),
                new Vector2f(0.0f, 0.0f), new Vector2f(1.0f, 0.0f), new Vector2f(0.5f, 0.8f),
                new Vector3f(-1, 0, 0), null, null, null));

        return faces;
    }

    /**
     * Membangun Atap Pelana Rumah 3D + Cerobong Asap (Chimney).
     */
    private static List<TriangleFace> buildHouseRoofFaces() {
        List<TriangleFace> faces = new ArrayList<>();
        float w = 1.4f;
        float d = 1.15f;
        float hWall = 1.5f;
        float hRoof = 2.5f;
        float rw = w + 0.22f;
        float rd = d + 0.22f;
        float roofThick = 0.08f;

        Vector2f uv00 = new Vector2f(0.0f, 0.0f);
        Vector2f uv10 = new Vector2f(1.0f, 0.0f);
        Vector2f uv11 = new Vector2f(1.0f, 1.0f);
        Vector2f uv01 = new Vector2f(0.0f, 1.0f);

        // Lereng Atap Depan Atas (+Y, +Z)
        addQuad(faces,
                new Vector3f(-rw, hWall, rd), new Vector3f(rw, hWall, rd),
                new Vector3f(rw, hRoof, 0.0f), new Vector3f(-rw, hRoof, 0.0f),
                uv00, uv10, uv11, uv01, new Vector3f(0.0f, 1.0f, 0.8f));

        // Lereng Atap Belakang Atas (+Y, -Z)
        addQuad(faces,
                new Vector3f(rw, hWall, -rd), new Vector3f(-rw, hWall, -rd),
                new Vector3f(-rw, hRoof, 0.0f), new Vector3f(rw, hRoof, 0.0f),
                uv00, uv10, uv11, uv01, new Vector3f(0.0f, 1.0f, -0.8f));

        // Penutup Bawah Atap Depan & Belakang (agar atap tertutup penuh untuk Back-Face Culling)
        addQuad(faces,
                new Vector3f(-rw, hWall - roofThick, rd), new Vector3f(-rw, hRoof - roofThick, 0.0f),
                new Vector3f(rw, hRoof - roofThick, 0.0f), new Vector3f(rw, hWall - roofThick, rd),
                uv00, uv01, uv11, uv10, new Vector3f(0.0f, -1.0f, -0.8f));
        addQuad(faces,
                new Vector3f(rw, hWall - roofThick, -rd), new Vector3f(rw, hRoof - roofThick, 0.0f),
                new Vector3f(-rw, hRoof - roofThick, 0.0f), new Vector3f(-rw, hWall - roofThick, -rd),
                uv00, uv01, uv11, uv10, new Vector3f(0.0f, -1.0f, 0.8f));

        // Cerobong Asap (Chimney Box)
        List<TriangleFace> chimneyCube = buildUnitCubeFaces(1.0f);
        for (TriangleFace cf : chimneyCube) {
            Vector3f p0 = new Vector3f(cf.p0).mul(0.20f, 0.45f, 0.20f).add(0.75f, 2.15f, -0.45f);
            Vector3f p1 = new Vector3f(cf.p1).mul(0.20f, 0.45f, 0.20f).add(0.75f, 2.15f, -0.45f);
            Vector3f p2 = new Vector3f(cf.p2).mul(0.20f, 0.45f, 0.20f).add(0.75f, 2.15f, -0.45f);
            faces.add(new TriangleFace(p0, p1, p2, cf.uv0, cf.uv1, cf.uv2, cf.faceNormal, null, null, null));
        }

        return faces;
    }

    /**
     * Membangun Pintu & Jendela Rumah 3D dengan z-offset terukur agar bebas Z-Fighting.
     */
    private static List<TriangleFace> buildHouseDetailsFaces() {
        List<TriangleFace> faces = new ArrayList<>();
        float zFront = 1.15f + 0.015f; // Offset 1.5 cm di depan dinding untuk mengeliminasi Z-Fighting (Indikator 3)
        Vector2f uv00 = new Vector2f(0.0f, 0.0f);
        Vector2f uv10 = new Vector2f(1.0f, 0.0f);
        Vector2f uv11 = new Vector2f(1.0f, 1.0f);
        Vector2f uv01 = new Vector2f(0.0f, 1.0f);
        Vector3f frontNorm = new Vector3f(0, 0, 1);

        // Pintu Depan
        addQuad(faces,
                new Vector3f(-0.28f, 0.01f, zFront), new Vector3f(0.28f, 0.01f, zFront),
                new Vector3f(0.28f, 0.95f, zFront), new Vector3f(-0.28f, 0.95f, zFront),
                uv00, uv10, uv11, uv01, frontNorm);

        // Jendela Kiri Depan
        addQuad(faces,
                new Vector3f(-1.05f, 0.50f, zFront), new Vector3f(-0.55f, 0.50f, zFront),
                new Vector3f(-0.55f, 1.05f, zFront), new Vector3f(-1.05f, 1.05f, zFront),
                uv00, uv10, uv11, uv01, frontNorm);

        // Jendela Kanan Depan
        addQuad(faces,
                new Vector3f(0.55f, 0.50f, zFront), new Vector3f(1.05f, 0.50f, zFront),
                new Vector3f(1.05f, 1.05f, zFront), new Vector3f(0.55f, 1.05f, zFront),
                uv00, uv10, uv11, uv01, frontNorm);

        return faces;
    }

    public void cycleSceneModel() {
        SceneModel[] values = SceneModel.values();
        currentModel = values[(currentModel.ordinal() + 1) % values.length];
    }

    public SceneModel getCurrentModel() { return currentModel; }
    public boolean isAnimateObjects() { return animateObjects; }
    public void toggleAnimateObjects() { this.animateObjects = !this.animateObjects; }
    public boolean isShowGridAndAxes() { return showGridAndAxes; }
    public void toggleGridAndAxes() { this.showGridAndAxes = !this.showGridAndAxes; }

    public void cleanup() {
        groundPlaneMesh.cleanup();
        cubeMesh.cleanup();
        sphereMesh.cleanup();
        teapotMesh.cleanup();
        houseWallsMesh.cleanup();
        houseRoofMesh.cleanup();
        houseDetailsMesh.cleanup();
    }
}
