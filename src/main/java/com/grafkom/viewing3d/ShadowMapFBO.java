package com.grafkom.viewing3d;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Praktikum 4 (Indikator 5): Kelas ShadowMapFBO (Shadow Mapping Depth FBO Manager).
 * Mengelola Framebuffer Object (FBO) khusus beresolusi 2048x2048 untuk menyimpan
 * nilai kedalaman (Depth Map) dari sudut pandang sumber cahaya pada Render Pass 1.
 */
public class ShadowMapFBO {

    private int fboID;
    private int depthTextureID;
    private final int SHADOW_WIDTH = 2048, SHADOW_HEIGHT = 2048;

    public ShadowMapFBO() {
        fboID = glGenFramebuffers();
        depthTextureID = glGenTextures();

        glBindTexture(GL_TEXTURE_2D, depthTextureID);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT, SHADOW_WIDTH, SHADOW_HEIGHT,
                0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        float[] borderColor = { 1.0f, 1.0f, 1.0f, 1.0f };
        glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, borderColor);

        glBindFramebuffer(GL_FRAMEBUFFER, fboID);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTextureID, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /**
     * Metode sesuai spesifikasi Modul Praktikum 4:
     * Mengikat FBO untuk penulisan Depth Pass (Pass 1).
     */
    public void bindForWriting() {
        glViewport(0, 0, SHADOW_WIDTH, SHADOW_HEIGHT);
        glBindFramebuffer(GL_FRAMEBUFFER, fboID);
        glClear(GL_DEPTH_BUFFER_BIT);
    }

    /**
     * Metode sesuai spesifikasi Modul Praktikum 4:
     * Mengembalikan Framebuffer ke layar utama dan memulihkan ukuran Viewport.
     */
    public void unbind(int screenWidth, int screenHeight) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, screenWidth, screenHeight);
    }

    /**
     * Mengikat tekstur Shadow Map ke unit tekstur tertentu untuk dibaca pada Render Pass 2.
     */
    public void bindDepthTexture(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, depthTextureID);
    }

    public int getDepthTextureID() {
        return depthTextureID;
    }

    public int getShadowWidth() {
        return SHADOW_WIDTH;
    }

    public int getShadowHeight() {
        return SHADOW_HEIGHT;
    }

    public void cleanup() {
        glDeleteFramebuffers(fboID);
        glDeleteTextures(depthTextureID);
    }
}
