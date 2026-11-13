package com.grafkom.viewing3d;

import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.stb.STBImage.*;

/**
 * Praktikum 3 (Indikator 4): Kelas Texture2D (Java Texture Loader & UV Mapping).
 * Memuat gambar tekstur 2D (PNG/JPG) menggunakan STBImage ke dalam GPU Memory,
 * membangkitkan Mipmap bertingkat, serta menyediakan kontrol peralihan mode
 * Texture Filtering (Nearest Neighbor vs Bilinear vs Trilinear Mipmapped).
 */
public class Texture2D {

    public enum FilterMode {
        MIPMAP_TRILINEAR("Bilinear Mipmapped (GL_LINEAR_MIPMAP_LINEAR)"),
        NEAREST("Nearest Neighbor (GL_NEAREST - Blocky)"),
        BILINEAR("Bilinear (GL_LINEAR - Tanpa Mipmap)");

        private final String label;

        FilterMode(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final int textureID;
    private final String name;
    private FilterMode currentFilterMode = FilterMode.MIPMAP_TRILINEAR;

    /**
     * Konstruktor utama sesuai spesifikasi Modul Praktikum 3.
     */
    public Texture2D(String filePath) {
        this(filePath, filePath);
    }

    public Texture2D(String filePath, String displayName) {
        this.name = displayName;
        this.textureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureID);

        // Parameter Wrapping & Filtering Default sesuai Modul Praktikum 3
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        stbi_set_flip_vertically_on_load(true);

        boolean loaded = false;
        String resolvedPath = resolveTexturePath(filePath);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            ByteBuffer data = (resolvedPath != null)
                    ? stbi_load(resolvedPath, width, height, channels, 4)
                    : null;

            if (data != null) {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width.get(0), height.get(0),
                        0, GL_RGBA, GL_UNSIGNED_BYTE, data);
                glGenerateMipmap(GL_TEXTURE_2D);
                stbi_image_free(data);
                loaded = true;
            } else {
                System.err.println("Peringatan: Gagal memuat tekstur dari path '" + filePath
                        + "', menggunakan tekstur prosedural cadangan.");
            }
        }

        if (!loaded) {
            generateProceduralFallbackTexture(256, 256);
        }

        applyFilterMode(currentFilterMode);
    }

    /**
     * Mencari file tekstur pada path langsung, direktori modul, atau mengekstraknya dari classpath resource.
     */
    private String resolveTexturePath(String filePath) {
        File directFile = new File(filePath);
        if (directFile.exists() && directFile.isFile()) {
            return directFile.getAbsolutePath();
        }

        // Coba cari relatif di dalam src/main/resources/
        String resourceRel = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        if (!resourceRel.startsWith("src/")) {
            File fromResourceDir = new File("src/main/resources/" + resourceRel);
            if (fromResourceDir.exists() && fromResourceDir.isFile()) {
                return fromResourceDir.getAbsolutePath();
            }
        }

        // Fallback: ekstrak dari ClassLoader resource ke temporary file agar stbi_load(String) tetap bekerja
        String cpPath = filePath.replace("src/main/resources/", "");
        if (!cpPath.startsWith("/")) {
            cpPath = "/" + cpPath;
        }
        try (InputStream in = Texture2D.class.getResourceAsStream(cpPath)) {
            if (in != null) {
                File temp = File.createTempFile("grafkom_tex_", ".png");
                temp.deleteOnExit();
                Files.copy(in, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return temp.getAbsolutePath();
            }
        } catch (IOException ignored) {
        }

        return filePath;
    }

    /**
     * Cadangan tekstur checkerboard jika file eksternal tidak ditemukan.
     */
    private void generateProceduralFallbackTexture(int w, int h) {
        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean even = ((x / 16) + (y / 16)) % 2 == 0;
                byte r = (byte) (even ? 230 : 50);
                byte g = (byte) (even ? 235 : 65);
                byte b = (byte) (even ? 242 : 90);
                buf.put(r).put(g).put(b).put((byte) 255);
            }
        }
        buf.flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        glGenerateMipmap(GL_TEXTURE_2D);
    }

    /**
     * Mengatur mode Texture Filtering (Nearest Neighbor vs Bilinear vs Mipmap Trilinear).
     */
    public void applyFilterMode(FilterMode mode) {
        this.currentFilterMode = mode;
        glBindTexture(GL_TEXTURE_2D, textureID);
        switch (mode) {
            case NEAREST:
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                break;
            case BILINEAR:
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                break;
            case MIPMAP_TRILINEAR:
            default:
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                break;
        }
    }

    /**
     * Metode sesuai spesifikasi Modul Praktikum 3:
     * Mengaktifkan texture unit dan mengikat textureID.
     */
    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureID);
    }

    public int getTextureID() {
        return textureID;
    }

    public String getName() {
        return name;
    }

    public FilterMode getCurrentFilterMode() {
        return currentFilterMode;
    }

    public void cleanup() {
        glDeleteTextures(textureID);
    }
}
