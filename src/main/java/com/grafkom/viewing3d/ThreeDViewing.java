package com.grafkom.viewing3d;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Main Class Minggu 11: ThreeDViewing - 3D Visual Realism Suite.
 * Mengintegrasikan seluruh fitur Minggu ke-10 (Camera3D, PerspectiveProjection, Taksonomi Proyeksi)
 * dengan 5 Indikator Penilaian Minggu ke-11 (Visual Realism pada Rendering Faces):
 * 1. Shading Models Switcher (Key 'B'): Standard Phong vs Blinn-Phong (Halfway Vector H)
 * 2. Flat vs Smooth Shading Toggle (Key 'F'): Per-Face Normal vs Vertex Normal Averaging (+ Key 'G' Gouraud vs Per-Pixel)
 * 3. Hidden Surface Removal Controls (Key 'Z' & Key 'C'): Z-Buffer Depth Test & Back-Face Culling
 * 4. Texture Mapping to Faces (Key 'T' & Key 'N'): UV Mapping, Nearest vs Bilinear vs Mipmapped Filtering
 * 5. Real-Time Shadow Mapping 2-Pass (Key 'Y', 'U', '[', ']'): Depth FBO 2048x2048 & Slope-Scaled Bias Tuning
 */
public class ThreeDViewing {

    public enum ShadowBiasPreset {
        OPTIMAL_SLOPE_BIAS("OPTIMAL BIAS (Bebas Acne & Panning)", 0.004f, 0.035f),
        ZERO_BIAS_ACNE_DEMO("ZERO BIAS (Demo Artefak Shadow Acne)", 0.000f, 0.000f),
        HIGH_BIAS_PANNING_DEMO("HIGH BIAS (Demo Artefak Peter Panning)", 0.048f, 0.095f);

        private final String label;
        private final float minBias;
        private final float slopeBias;

        ShadowBiasPreset(String label, float minBias, float slopeBias) {
            this.label = label;
            this.minBias = minBias;
            this.slopeBias = slopeBias;
        }

        public String getLabel() { return label; }
        public float getMinBias() { return minBias; }
        public float getSlopeBias() { return slopeBias; }
    }

    private long window;
    private int windowWidth = 1320;
    private int windowHeight = 800;

    // Komponen Kamera & Proyeksi (Minggu 10)
    private Camera3D camera;
    private PerspectiveProjection perspectiveProjection;
    private ProjectionTaxonomyManager.ProjectionType currentProjection =
            ProjectionTaxonomyManager.ProjectionType.PERSPECTIVE_3POINT;
    private boolean lockTaxonomyCanonicalView = false;

    // Komponen Visual Realism (Minggu 11)
    private ShaderProgram phongShader;
    private ShaderProgram shadowDepthShader;
    private ShadowMapFBO shadowMapFBO;
    private Mesh3D mesh3D;

    // Tekstur 2D (Indikator 4)
    private Texture2D brickTexture;
    private Texture2D roofTexture;
    private Texture2D checkerTexture;
    private Texture2D floorTexture;
    private Texture2D.FilterMode currentFilterMode = Texture2D.FilterMode.MIPMAP_TRILINEAR;
    private int activeTexturePreset = 0; // 0 = Material Realistis (Bata/Genteng/Marmer), 1 = Checkerboard UV Uji Filter

    // State Interaktif 5 Indikator Minggu 11
    private boolean useBlinnPhong = true;       // Indikator 1 (Key 'B')
    private boolean smoothShading = true;       // Indikator 2 (Key 'F')
    private boolean useGouraudVertex = false;   // Sub-mode Indikator 2 (Key 'G': Per-Vertex Gouraud vs Per-Pixel Phong)
    private boolean useTextureMapping = true;   // Indikator 4 (Key 'T')
    private boolean enableShadowMapping = true; // Indikator 5 (Key 'Y')
    private boolean useShadowPCF = true;        // Penghalusan tepi bayangan PCF 3x3 (Key 'X')

    // Parameter Sumber Cahaya & Shadow Bias (Indikator 5)
    private final Vector3f lightPos = new Vector3f(4.6f, 6.2f, 4.2f);
    private final Vector3f lightColor = new Vector3f(1.0f, 0.98f, 0.94f);
    private boolean animateLightOrbit = false;
    private float lightOrbitAngleDeg = 42.0f;
    private ShadowBiasPreset currentBiasPreset = ShadowBiasPreset.OPTIMAL_SLOPE_BIAS;
    private float shadowBiasMin = ShadowBiasPreset.OPTIMAL_SLOPE_BIAS.getMinBias();
    private float shadowBiasSlope = ShadowBiasPreset.OPTIMAL_SLOPE_BIAS.getSlopeBias();

    // HUD & Pengukur Kinerja Rendering (FPS & Frame Time untuk Bab 5 Laporan)
    private boolean showHudOverlay = true;
    private double lastFrameTime = 0.0;
    private double fpsTimer = 0.0;
    private int frameCounter = 0;
    private float currentFps = 60.0f;
    private float currentFrameTimeMs = 16.67f;

    // State Mouse untuk Navigasi Kamera
    private boolean leftMouseDown = false;
    private boolean rightMouseDown = false;
    private double lastMouseX = 0.0;
    private double lastMouseY = 0.0;

    public static void main(String[] args) {
        new ThreeDViewing().run();
    }

    public void run() {
        initWindowAndOpenGL();
        initComponents();
        printControlsBanner();
        loop();
        cleanup();
    }

    private void initWindowAndOpenGL() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Gagal menginisialisasi GLFW!");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_DEPTH_BITS, 24); // Presisi Z-Buffer 24-bit untuk mencegah Z-Fighting (Indikator 3)

        window = glfwCreateWindow(
                windowWidth,
                windowHeight,
                "Grafika Komputer Minggu 11 - 3D Visual Realism Suite (Java OOP & Modern GLSL)",
                NULL,
                NULL
        );
        if (window == NULL) {
            throw new RuntimeException("Gagal membuat window GLFW!");
        }

        GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vidmode != null) {
            glfwSetWindowPos(
                    window,
                    (vidmode.width() - windowWidth) / 2,
                    (vidmode.height() - windowHeight) / 2
            );
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // V-Sync aktif
        glfwShowWindow(window);

        GL.createCapabilities();

        // Konfigurasi awal Hidden Surface Removal (Indikator 3)
        HSRManager.setupDepthTest(true);
        HSRManager.setupBackFaceCulling(true);

        glClearColor(0.08f, 0.10f, 0.14f, 1.0f);
    }

    private void initComponents() {
        float aspect = (float) windowWidth / (float) windowHeight;

        camera = new Camera3D(
                new Vector3f(5.2f, 4.0f, 7.2f),
                new Vector3f(0.0f, 0.9f, 0.0f),
                new Vector3f(0.0f, 1.0f, 0.0f)
        );
        camera.updateOrbitalPosition(8.8f, 34.0f, 26.0f);

        perspectiveProjection = new PerspectiveProjection(45.0f, aspect, 0.25f, 45.0f);

        // Inisialisasi Shader Program GLSL 330 Core (Praktikum 1 & 4)
        phongShader = new ShaderProgram(
                "src/main/resources/shaders/phong.vert",
                "src/main/resources/shaders/phong.frag"
        );
        shadowDepthShader = new ShaderProgram(
                "src/main/resources/shaders/shadow_depth.vert",
                "src/main/resources/shaders/shadow_depth.frag"
        );

        // Inisialisasi Shadow Map Depth FBO 2048x2048 (Praktikum 4)
        shadowMapFBO = new ShadowMapFBO();

        // Inisialisasi Tekstur 2D dengan STBImage & Mipmapping (Praktikum 3)
        brickTexture = new Texture2D("src/main/resources/textures/brick_wall.png", "Bata Merah Arsitektural");
        roofTexture = new Texture2D("src/main/resources/textures/roof_tiles.png", "Genteng Terracotta");
        checkerTexture = new Texture2D("src/main/resources/textures/checker_uv.png", "Checkerboard UV Grid");
        floorTexture = new Texture2D("src/main/resources/textures/marble_floor.png", "Marmer Lantai");
        applyTextureFilterToAll(currentFilterMode);

        // Inisialisasi Geometri Mesh 3D Berbasis VAO/VBO (Spesifikasi Tugas Mandiri a)
        mesh3D = new Mesh3D();

        setupInputCallbacks();
        updateWindowTitle();
    }

    private void applyTextureFilterToAll(Texture2D.FilterMode mode) {
        currentFilterMode = mode;
        brickTexture.applyFilterMode(mode);
        roofTexture.applyFilterMode(mode);
        checkerTexture.applyFilterMode(mode);
        floorTexture.applyFilterMode(mode);
    }

    private void cycleTextureFilterMode() {
        Texture2D.FilterMode[] modes = Texture2D.FilterMode.values();
        Texture2D.FilterMode next = modes[(currentFilterMode.ordinal() + 1) % modes.length];
        applyTextureFilterToAll(next);
    }

    private void cycleShadowBiasPreset() {
        ShadowBiasPreset[] presets = ShadowBiasPreset.values();
        currentBiasPreset = presets[(currentBiasPreset.ordinal() + 1) % presets.length];
        shadowBiasMin = currentBiasPreset.getMinBias();
        shadowBiasSlope = currentBiasPreset.getSlopeBias();
    }

    private void setupInputCallbacks() {
        glfwSetFramebufferSizeCallback(window, (win, width, height) -> {
            if (width > 0 && height > 0) {
                windowWidth = width;
                windowHeight = height;
                glViewport(0, 0, width, height);
                perspectiveProjection.setAspectRatio((float) width / (float) height);
            }
        });

        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (action != GLFW_PRESS && action != GLFW_REPEAT) {
                return;
            }

            switch (key) {
                case GLFW_KEY_ESCAPE:
                    glfwSetWindowShouldClose(win, true);
                    break;

                // =================================================================
                // KONTROL WAJIB SPESIFIKASI TUGAS MANDIRI MINGGU 11 (INDIKATOR 1-5)
                // =================================================================

                // Indikator 1 (Key 'B'): Toggle Phong Shading vs Blinn-Phong Shading
                case GLFW_KEY_B:
                    useBlinnPhong = !useBlinnPhong;
                    break;

                // Indikator 2 (Key 'F'): Toggle Flat Shading vs Smooth Shading
                case GLFW_KEY_F:
                    smoothShading = !smoothShading;
                    break;

                // Sub-Indikator 2 (Key 'G'): Toggle Per-Pixel Phong vs Per-Vertex Gouraud Shading
                case GLFW_KEY_G:
                    useGouraudVertex = !useGouraudVertex;
                    break;

                // Indikator 3 (Key 'Z'): Toggle Z-Buffer Depth Test On/Off
                case GLFW_KEY_Z:
                    HSRManager.toggleDepthTest();
                    break;

                // Indikator 3 (Key 'C'): Toggle Back-Face Culling On/Off
                case GLFW_KEY_C:
                    HSRManager.toggleBackFaceCulling();
                    break;

                // Indikator 4 (Key 'T'): Toggle Texture Mapping On/Off & Preset Material/Checker UV
                case GLFW_KEY_T:
                    if (!useTextureMapping) {
                        useTextureMapping = true;
                        activeTexturePreset = 0;
                    } else if (activeTexturePreset == 0) {
                        activeTexturePreset = 1; // Ganti ke Checkerboard UV Grid untuk uji ketajaman filter
                    } else {
                        useTextureMapping = false;
                    }
                    break;

                // Indikator 4 (Key 'N'): Ganti Mode Texture Filtering (Mipmap Trilinear vs Nearest vs Bilinear)
                case GLFW_KEY_N:
                    cycleTextureFilterMode();
                    break;

                // Indikator 5 (Key 'Y'): Toggle Real-Time Shadow Mapping 2-Pass On/Off
                case GLFW_KEY_Y:
                    enableShadowMapping = !enableShadowMapping;
                    break;

                // Indikator 5 (Key 'U'): Ganti Preset Shadow Bias (Optimal vs Zero Bias Acne vs High Bias Panning)
                case GLFW_KEY_U:
                    cycleShadowBiasPreset();
                    break;

                // Indikator 5 (Key '[' & ']'): Fine-Tuning Nilai Shadow Bias secara Manual
                case GLFW_KEY_LEFT_BRACKET:
                    shadowBiasMin = Math.max(0.0f, shadowBiasMin - 0.001f);
                    shadowBiasSlope = Math.max(0.0f, shadowBiasSlope - 0.005f);
                    break;
                case GLFW_KEY_RIGHT_BRACKET:
                    shadowBiasMin = Math.min(0.08f, shadowBiasMin + 0.001f);
                    shadowBiasSlope = Math.min(0.15f, shadowBiasSlope + 0.005f);
                    break;

                // Indikator 5 (Key 'X'): Toggle PCF 3x3 Soft Shadow vs Hard Shadow
                case GLFW_KEY_X:
                    useShadowPCF = !useShadowPCF;
                    break;

                // Indikator 5 (Key 'O'): Toggle Rotasi Orbit Otomatis Sumber Cahaya
                case GLFW_KEY_O:
                    animateLightOrbit = !animateLightOrbit;
                    break;

                // =================================================================
                // KONTROL MODEL, KAMERA, DAN PROYEKSI (KOMPATIBILITAS MINGGU 10)
                // =================================================================
                case GLFW_KEY_M:
                    mesh3D.cycleSceneModel();
                    break;
                case GLFW_KEY_P:
                    mesh3D.toggleAnimateObjects();
                    break;
                case GLFW_KEY_V:
                    camera.toggleCameraMode();
                    lockTaxonomyCanonicalView = false;
                    break;
                case GLFW_KEY_H:
                    showHudOverlay = !showHudOverlay;
                    break;
                case GLFW_KEY_R:
                    resetAll();
                    break;

                // Taksonomi Proyeksi Minggu 10 (Tombol 1 s/d 7)
                case GLFW_KEY_1:
                    currentProjection = ProjectionTaxonomyManager.ProjectionType.ORTHOGRAPHIC;
                    lockTaxonomyCanonicalView = true;
                    break;
                case GLFW_KEY_2:
                    currentProjection = ProjectionTaxonomyManager.ProjectionType.ISOMETRIC;
                    lockTaxonomyCanonicalView = true;
                    break;
                case GLFW_KEY_3:
                    currentProjection = ProjectionTaxonomyManager.ProjectionType.OBLIQUE_CAVALIER;
                    lockTaxonomyCanonicalView = true;
                    break;
                case GLFW_KEY_4:
                    currentProjection = ProjectionTaxonomyManager.ProjectionType.PERSPECTIVE_1POINT;
                    lockTaxonomyCanonicalView = true;
                    break;
                case GLFW_KEY_5:
                    currentProjection = ProjectionTaxonomyManager.ProjectionType.PERSPECTIVE_2POINT;
                    lockTaxonomyCanonicalView = true;
                    break;
                case GLFW_KEY_6:
                    currentProjection = ProjectionTaxonomyManager.ProjectionType.PERSPECTIVE_3POINT;
                    lockTaxonomyCanonicalView = false;
                    break;
                case GLFW_KEY_7:
                    currentProjection = ProjectionTaxonomyManager.ProjectionType.OBLIQUE_CABINET;
                    lockTaxonomyCanonicalView = true;
                    break;

                // Zoom FOV (+ / -)
                case GLFW_KEY_EQUAL:
                case GLFW_KEY_KP_ADD:
                    perspectiveProjection.adjustFov(-2.0f);
                    break;
                case GLFW_KEY_MINUS:
                case GLFW_KEY_KP_SUBTRACT:
                    perspectiveProjection.adjustFov(2.0f);
                    break;
            }
            updateWindowTitle();
        });

        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                leftMouseDown = (action == GLFW_PRESS);
            } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                rightMouseDown = (action == GLFW_PRESS);
            }
            double[] mx = new double[1];
            double[] my = new double[1];
            glfwGetCursorPos(win, mx, my);
            lastMouseX = mx[0];
            lastMouseY = my[0];
        });

        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            float dx = (float) (xpos - lastMouseX);
            float dy = (float) (ypos - lastMouseY);
            lastMouseX = xpos;
            lastMouseY = ypos;

            if (leftMouseDown) {
                lockTaxonomyCanonicalView = false;
                if (camera.getMode() == Camera3D.CameraMode.ORBITAL) {
                    camera.rotateOrbit(-dx * camera.getMouseSensitivity(), dy * camera.getMouseSensitivity());
                } else {
                    camera.rotateFPS(dx * camera.getMouseSensitivity(), -dy * camera.getMouseSensitivity());
                }
                updateWindowTitle();
            } else if (rightMouseDown) {
                lockTaxonomyCanonicalView = false;
                camera.panTarget(-dx * 0.015f, dy * 0.015f);
                updateWindowTitle();
            }
        });

        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            camera.zoomOrbit((float) -yoffset * 0.55f);
            updateWindowTitle();
        });
    }

    private void processContinuousInput(float dt) {
        float rotSpeed = 65.0f * dt;
        float moveStep = camera.getMoveSpeed() * dt;
        float lightStep = 3.5f * dt;

        if (camera.getMode() == Camera3D.CameraMode.ORBITAL) {
            if (glfwGetKey(window, GLFW_KEY_LEFT) == GLFW_PRESS || glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
                camera.rotateOrbit(-rotSpeed, 0.0f);
            }
            if (glfwGetKey(window, GLFW_KEY_RIGHT) == GLFW_PRESS || glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
                camera.rotateOrbit(rotSpeed, 0.0f);
            }
            if (glfwGetKey(window, GLFW_KEY_UP) == GLFW_PRESS || glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
                camera.rotateOrbit(0.0f, rotSpeed);
            }
            if (glfwGetKey(window, GLFW_KEY_DOWN) == GLFW_PRESS || glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
                camera.rotateOrbit(0.0f, -rotSpeed);
            }
            if (glfwGetKey(window, GLFW_KEY_Q) == GLFW_PRESS) {
                camera.zoomOrbit(-moveStep);
            }
            if (glfwGetKey(window, GLFW_KEY_E) == GLFW_PRESS) {
                camera.zoomOrbit(moveStep);
            }
        } else {
            float forward = 0.0f, right = 0.0f, up = 0.0f;
            if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) forward += moveStep;
            if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) forward -= moveStep;
            if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) right += moveStep;
            if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) right -= moveStep;
            if (glfwGetKey(window, GLFW_KEY_E) == GLFW_PRESS) up += moveStep;
            if (glfwGetKey(window, GLFW_KEY_Q) == GLFW_PRESS) up -= moveStep;
            if (forward != 0.0f || right != 0.0f || up != 0.0f) {
                camera.moveFPS(forward, right, up);
            }
        }

        // Kontrol Posisi Sumber Cahaya (Tombol I/K/J/L) untuk menguji pergeseran bayangan Real-Time
        if (glfwGetKey(window, GLFW_KEY_J) == GLFW_PRESS) {
            lightPos.x -= lightStep;
            animateLightOrbit = false;
        }
        if (glfwGetKey(window, GLFW_KEY_L) == GLFW_PRESS) {
            lightPos.x += lightStep;
            animateLightOrbit = false;
        }
        if (glfwGetKey(window, GLFW_KEY_I) == GLFW_PRESS) {
            lightPos.z -= lightStep;
            animateLightOrbit = false;
        }
        if (glfwGetKey(window, GLFW_KEY_K) == GLFW_PRESS) {
            lightPos.z += lightStep;
            animateLightOrbit = false;
        }

        if (animateLightOrbit) {
            lightOrbitAngleDeg = (lightOrbitAngleDeg + 28.0f * dt) % 360.0f;
            float rad = (float) Math.toRadians(lightOrbitAngleDeg);
            lightPos.set(6.2f * (float) Math.cos(rad), 6.2f, 6.2f * (float) Math.sin(rad));
        }
    }

    /**
     * Menghitung matriks Light-Space (P_light * V_light) untuk Render Pass 1 & Pass 2 Shadow Mapping.
     */
    private Matrix4f calculateLightSpaceMatrix() {
        float orthoSize = 7.5f;
        Matrix4f lightProjection = new Matrix4f().ortho(
                -orthoSize, orthoSize,
                -orthoSize, orthoSize,
                1.0f, 22.0f
        );
        Matrix4f lightView = new Matrix4f().lookAt(
                lightPos,
                new Vector3f(0.0f, 0.5f, 0.0f),
                new Vector3f(0.0f, 1.0f, 0.0f)
        );
        return lightProjection.mul(lightView);
    }

    private void loop() {
        lastFrameTime = glfwGetTime();
        fpsTimer = lastFrameTime;

        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            float dt = (float) (now - lastFrameTime);
            lastFrameTime = now;

            // Hitung FPS & Frame Time (ms) untuk analisis kinerja di Laporan Praktikum
            frameCounter++;
            if (now - fpsTimer >= 0.4) {
                currentFps = (float) (frameCounter / (now - fpsTimer));
                currentFrameTimeMs = 1000.0f / Math.max(1.0f, currentFps);
                frameCounter = 0;
                fpsTimer = now;
                updateWindowTitle();
            }

            processContinuousInput(dt);
            mesh3D.updateAnimation(dt);

            Matrix4f lightSpaceMatrix = calculateLightSpaceMatrix();

            // =====================================================================
            // RENDER PASS 1 (DEPTH PASS): Render dari sudut pandang Sumber Cahaya ke ShadowMapFBO
            // =====================================================================
            if (enableShadowMapping) {
                glEnable(GL_DEPTH_TEST);
                glDepthFunc(GL_LESS);

                shadowMapFBO.bindForWriting();
                shadowDepthShader.use();
                shadowDepthShader.setUniform("lightSpaceMatrix", lightSpaceMatrix);

                mesh3D.renderShaderScene(
                        shadowDepthShader,
                        smoothShading,
                        false,
                        activeTexturePreset,
                        brickTexture, roofTexture, checkerTexture, floorTexture,
                        lightPos,
                        true
                );

                ShaderProgram.unbind();
                shadowMapFBO.unbind(windowWidth, windowHeight);
            }

            // =====================================================================
            // RENDER PASS 2 (LIGHTING PASS): Render dari sudut pandang Kamera Pengamat
            // =====================================================================
            HSRManager.applyCurrentState(); // Terapkan state Z-Buffer (Key Z) & Back-Face Culling (Key C)
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            float aspect = (float) windowWidth / (float) Math.max(1, windowHeight);
            Matrix4f projMatrix = ProjectionTaxonomyManager.calculateProjectionMatrix(
                    currentProjection, aspect, perspectiveProjection
            );
            Matrix4f viewMatrix = ProjectionTaxonomyManager.calculateTaxonomyViewMatrix(
                    currentProjection, camera, lockTaxonomyCanonicalView
            );

            phongShader.use();
            phongShader.setUniform("projection", projMatrix);
            phongShader.setUniform("view", viewMatrix);
            phongShader.setUniform("lightSpaceMatrix", lightSpaceMatrix);

            phongShader.setUniform("lightPos", lightPos);
            phongShader.setUniform("viewPos", camera.getEye());
            phongShader.setUniform("lightColor", lightColor);

            phongShader.setUniform("useBlinnPhong", useBlinnPhong);
            phongShader.setUniform("useGouraud", useGouraudVertex);
            phongShader.setUniform("enableShadows", enableShadowMapping);
            phongShader.setUniform("usePCF", useShadowPCF);
            phongShader.setUniform("shadowBiasMin", shadowBiasMin);
            phongShader.setUniform("shadowBiasSlope", shadowBiasSlope);

            // Unit 0 = Texture Sampler 2D, Unit 1 = Shadow Depth Map
            phongShader.setUniform("textureSampler", 0);
            phongShader.setUniform("shadowMap", 1);
            shadowMapFBO.bindDepthTexture(1);

            mesh3D.renderShaderScene(
                    phongShader,
                    smoothShading,
                    useTextureMapping,
                    activeTexturePreset,
                    brickTexture, roofTexture, checkerTexture, floorTexture,
                    lightPos,
                    false
            );

            ShaderProgram.unbind();

            if (showHudOverlay) {
                renderHudOverlay();
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    /**
     * Merender panel HUD status interaktif untuk mempermudah dokumentasi screenshot 5 Indikator Penilaian.
     */
    private void renderHudOverlay() {
        glBindVertexArray(0);
        glUseProgram(0);

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, windowWidth, windowHeight, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Panel latar belakang HUD
        glColor4f(0.04f, 0.06f, 0.10f, 0.82f);
        glBegin(GL_QUADS);
        glVertex2f(14.0f, 14.0f);
        glVertex2f(650.0f, 14.0f);
        glVertex2f(650.0f, 252.0f);
        glVertex2f(14.0f, 252.0f);
        glEnd();

        // Bingkai panel HUD
        glColor4f(0.28f, 0.68f, 1.0f, 0.92f);
        glLineWidth(1.6f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(14.0f, 14.0f);
        glVertex2f(650.0f, 14.0f);
        glVertex2f(650.0f, 252.0f);
        glVertex2f(14.0f, 252.0f);
        glEnd();

        // Judul & Kinerja FPS
        glColor3f(0.96f, 0.98f, 1.0f);
        VectorFontRenderer.drawString(
                String.format("MINGGU 11: 3D VISUAL REALISM SUITE | FPS: %.1f (%.2f MS)", currentFps, currentFrameTimeMs),
                26, 28, 1.45f
        );

        // Indikator 1: Shading Models
        glColor3f(0.45f, 0.88f, 1.0f);
        VectorFontRenderer.drawString(
                "1. SHADING MODEL [B] : " + (useBlinnPhong ? "BLINN-PHONG (HALFWAY H, N=32)" : "STANDARD PHONG (REFLECT R, N=16)"),
                26, 52, 1.3f
        );

        // Indikator 2: Flat vs Smooth Shading
        glColor3f(0.55f, 0.96f, 0.65f);
        VectorFontRenderer.drawString(
                "2. INTERPOLASI   [F] : " + (smoothShading ? "SMOOTH (VERTEX NORMAL AVG)" : "FLAT SHADING (PER-FACE NORMAL)")
                        + " | [G]: " + (useGouraudVertex ? "GOURAUD" : "PER-PIXEL"),
                26, 74, 1.3f
        );

        // Indikator 3: Hidden Surface Removal (Z-Buffer & Back-Face Culling)
        glColor3f(1.0f, 0.86f, 0.42f);
        VectorFontRenderer.drawString(
                "3. HSR CONTROLS [Z/C]: Z-BUFFER=" + (HSRManager.isDepthTestEnabled() ? "ON (GL_LESS)" : "OFF (OVERLAP)")
                        + " | BACK-FACE CULLING=" + (HSRManager.isBackFaceCullingEnabled() ? "ON (GL_CCW)" : "OFF"),
                26, 96, 1.3f
        );

        // Indikator 4: Texture Mapping & Filtering
        glColor3f(0.96f, 0.68f, 0.92f);
        String texStatus = !useTextureMapping
                ? "OFF (OBJECT COLOR)"
                : (activeTexturePreset == 0 ? "ON (MATERIAL REALISTIS)" : "ON (CHECKER UV GRID)");
        VectorFontRenderer.drawString(
                "4. TEXTURE [T] / [N] : " + texStatus + " | FILTER: " + currentFilterMode.name(),
                26, 118, 1.3f
        );

        // Indikator 5: Shadow Mapping 2-Pass & Bias
        glColor3f(1.0f, 0.62f, 0.48f);
        VectorFontRenderer.drawString(
                String.format("5. SHADOWS [Y/U/X]   : %s | BIAS=%.3f/%.3f (%s) | PCF=%s",
                        enableShadowMapping ? "ON (2048 FBO)" : "OFF",
                        shadowBiasMin, shadowBiasSlope,
                        currentBiasPreset.name().replace("_DEMO", ""),
                        useShadowPCF ? "ON" : "OFF"),
                26, 140, 1.28f
        );

        // Info Scene, Kamera, dan Sumber Cahaya
        glColor3f(0.82f, 0.88f, 0.95f);
        VectorFontRenderer.drawString(
                String.format("SCENE [M]: %s | CAM [V]: %s | LIGHT [IJKL/O]: (%.1f, %.1f, %.1f)",
                        mesh3D.getCurrentModel().name(),
                        camera.getMode().name(),
                        lightPos.x, lightPos.y, lightPos.z),
                26, 166, 1.25f
        );

        glColor3f(0.68f, 0.75f, 0.84f);
        VectorFontRenderer.drawString(
                "KEYS WAJIB: B=PHONG/BLINN  F=FLAT/SMOOTH  Z=Z-BUFFER  C=CULLING  T=TEX  N=FILTER",
                26, 192, 1.2f
        );
        VectorFontRenderer.drawString(
                "KEYS EXTRA: Y=SHADOW  U=BIAS PRESET  [/]=TUNE BIAS  O=LIGHT ORBIT  M=MODEL  R=RESET",
                26, 212, 1.2f
        );
        VectorFontRenderer.drawString(
                "NAVIGASI  : WASD/ARROWS/MOUSE DRAG=ROTATE & PAN  SCROLL=ZOOM  1-7=PROYEKSI  H=HUD",
                26, 232, 1.2f
        );

        glDisable(GL_BLEND);
        HSRManager.applyCurrentState();

        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    private void resetAll() {
        float aspect = (float) windowWidth / (float) Math.max(1, windowHeight);
        camera.reset();
        camera.updateOrbitalPosition(8.8f, 34.0f, 26.0f);
        perspectiveProjection.reset(aspect);
        currentProjection = ProjectionTaxonomyManager.ProjectionType.PERSPECTIVE_3POINT;
        lockTaxonomyCanonicalView = false;

        useBlinnPhong = true;
        smoothShading = true;
        useGouraudVertex = false;
        HSRManager.setupDepthTest(true);
        HSRManager.setupBackFaceCulling(true);
        useTextureMapping = true;
        activeTexturePreset = 0;
        applyTextureFilterToAll(Texture2D.FilterMode.MIPMAP_TRILINEAR);

        enableShadowMapping = true;
        useShadowPCF = true;
        currentBiasPreset = ShadowBiasPreset.OPTIMAL_SLOPE_BIAS;
        shadowBiasMin = currentBiasPreset.getMinBias();
        shadowBiasSlope = currentBiasPreset.getSlopeBias();
        lightPos.set(4.6f, 6.2f, 4.2f);
        animateLightOrbit = false;
    }

    private void updateWindowTitle() {
        String title = String.format(
                "3D Visual Realism Suite | [B] %s | [F] %s | [Z] ZBuf:%s [C] Cull:%s | [T/N] Tex:%s (%s) | [Y] Shadow:%s | %.0f FPS",
                useBlinnPhong ? "Blinn-Phong" : "Phong",
                smoothShading ? "Smooth" : "Flat",
                HSRManager.isDepthTestEnabled() ? "ON" : "OFF",
                HSRManager.isBackFaceCullingEnabled() ? "ON" : "OFF",
                useTextureMapping ? "ON" : "OFF",
                currentFilterMode.name(),
                enableShadowMapping ? "ON" : "OFF",
                currentFps
        );
        glfwSetWindowTitle(window, title);
    }

    private void printControlsBanner() {
        System.out.println("=====================================================================================");
        System.out.println("  MODUL 11 GRAFIKA KOMPUTER: 3D VISUAL REALISM SUITE (JAVA OOP & MODERN GLSL 330)    ");
        System.out.println("=====================================================================================");
        System.out.println("  [B] Indikator 1 : Switch Phong Shading vs Blinn-Phong Shading (Halfway Vector H)   ");
        System.out.println("  [F] Indikator 2 : Toggle Flat Shading (Facet) vs Smooth Shading (Vertex Normal Avg)");
        System.out.println("  [G] Sub-Ind 2   : Toggle Per-Pixel Lighting (Phong) vs Per-Vertex (Gouraud)        ");
        System.out.println("  [Z] Indikator 3 : Toggle Z-Buffer Depth Test (GL_DEPTH_TEST) On / Off              ");
        System.out.println("  [C] Indikator 3 : Toggle Back-Face Culling (GL_CULL_FACE / GL_CCW) On / Off        ");
        System.out.println("  [T] Indikator 4 : Toggle Texture Mapping (Material Realistis / Checker UV / Off)   ");
        System.out.println("  [N] Indikator 4 : Switch Texture Filtering (Mipmap Trilinear / Nearest / Bilinear) ");
        System.out.println("  [Y] Indikator 5 : Toggle Real-Time Shadow Mapping 2-Pass (Depth FBO 2048x2048)     ");
        System.out.println("  [U] Indikator 5 : Ganti Preset Shadow Bias (Optimal / Zero Bias Acne / High Bias)  ");
        System.out.println("  [[ / ]] Ind 5   : Fine-Tuning Nilai Shadow Bias Manual (- / +)                     ");
        System.out.println("  [X] Indikator 5 : Toggle Percentage-Closer Filtering (PCF Soft Shadow) On / Off    ");
        System.out.println("  [I/J/K/L & O]   : Geser Posisi Sumber Cahaya Manual / Toggle Orbit Cahaya Otomatis ");
        System.out.println("  [M] Ganti Scene : Suite Lengkap / Teapot & Sphere / Rumah 3D / Multi-Kubus         ");
        System.out.println("  [V] Mode Kamera : Orbital vs FPS  | [P] Pause Animasi | [H] HUD | [R] Reset Semua  ");
        System.out.println("=====================================================================================");
    }

    private void cleanup() {
        if (mesh3D != null) mesh3D.cleanup();
        if (phongShader != null) phongShader.cleanup();
        if (shadowDepthShader != null) shadowDepthShader.cleanup();
        if (shadowMapFBO != null) shadowMapFBO.cleanup();
        if (brickTexture != null) brickTexture.cleanup();
        if (roofTexture != null) roofTexture.cleanup();
        if (checkerTexture != null) checkerTexture.cleanup();
        if (floorTexture != null) floorTexture.cleanup();

        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        GLFWErrorCallback cb = glfwSetErrorCallback(null);
        if (cb != null) {
            cb.free();
        }
    }
}
