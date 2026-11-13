package com.grafkom.viewing3d;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

/**
 * Kelas enkapsulasi OOP untuk pemuatan, kompilasi, linking, dan pengaturan variabel Uniform
 * pada program GLSL Vertex Shader & Fragment Shader (#version 330 core).
 */
public class ShaderProgram {

    private final int programId;
    private final Map<String, Integer> uniformLocationCache = new HashMap<>();

    public ShaderProgram(String vertexShaderPath, String fragmentShaderPath) {
        String vertexCode = loadShaderSource(vertexShaderPath);
        String fragmentCode = loadShaderSource(fragmentShaderPath);

        int vertexShaderId = compileShader(vertexCode, GL_VERTEX_SHADER, vertexShaderPath);
        int fragmentShaderId = compileShader(fragmentCode, GL_FRAGMENT_SHADER, fragmentShaderPath);

        programId = glCreateProgram();
        glAttachShader(programId, vertexShaderId);
        glAttachShader(programId, fragmentShaderId);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(programId);
            throw new RuntimeException("Gagal me-link GLSL Shader Program: " + log);
        }

        glDetachShader(programId, vertexShaderId);
        glDetachShader(programId, fragmentShaderId);
        glDeleteShader(vertexShaderId);
        glDeleteShader(fragmentShaderId);
    }

    private int compileShader(String source, int type, String pathLabel) {
        int shaderId = glCreateShader(type);
        glShaderSource(shaderId, source);
        glCompileShader(shaderId);

        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shaderId);
            throw new RuntimeException("Gagal mengompilasi shader [" + pathLabel + "]:\n" + log);
        }
        return shaderId;
    }

    private String loadShaderSource(String path) {
        // 1. Cek path langsung di filesystem (misal src/main/resources/shaders/phong.vert)
        File directFile = new File(path);
        if (directFile.exists() && directFile.isFile()) {
            try {
                return Files.readString(directFile.toPath(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Gagal membaca file shader: " + path, e);
            }
        }

        // 2. Cek relatif terhadap src/main/resources/
        File resFile = new File("src/main/resources/" + (path.startsWith("/") ? path.substring(1) : path));
        if (resFile.exists() && resFile.isFile()) {
            try {
                return Files.readString(resFile.toPath(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Gagal membaca file shader: " + resFile.getPath(), e);
            }
        }

        // 3. Cek melalui ClassLoader Resource Stream
        String cpPath = path.replace("src/main/resources/", "");
        if (!cpPath.startsWith("/")) {
            cpPath = "/" + cpPath;
        }
        try (InputStream in = ShaderProgram.class.getResourceAsStream(cpPath)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Gagal membaca resource shader: " + cpPath, e);
        }

        throw new RuntimeException("File shader tidak ditemukan: " + path);
    }

    public void use() {
        glUseProgram(programId);
    }

    public static void unbind() {
        glUseProgram(0);
    }

    private int getUniformLocation(String name) {
        return uniformLocationCache.computeIfAbsent(name, n -> glGetUniformLocation(programId, n));
    }

    public void setUniform(String name, boolean value) {
        int loc = getUniformLocation(name);
        if (loc >= 0) {
            glUniform1i(loc, value ? 1 : 0);
        }
    }

    public void setUniform(String name, int value) {
        int loc = getUniformLocation(name);
        if (loc >= 0) {
            glUniform1i(loc, value);
        }
    }

    public void setUniform(String name, float value) {
        int loc = getUniformLocation(name);
        if (loc >= 0) {
            glUniform1f(loc, value);
        }
    }

    public void setUniform(String name, Vector3f vec) {
        int loc = getUniformLocation(name);
        if (loc >= 0) {
            glUniform3f(loc, vec.x, vec.y, vec.z);
        }
    }

    public void setUniform(String name, float x, float y, float z) {
        int loc = getUniformLocation(name);
        if (loc >= 0) {
            glUniform3f(loc, x, y, z);
        }
    }

    public void setUniform(String name, Matrix4f matrix) {
        int loc = getUniformLocation(name);
        if (loc >= 0) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer fb = stack.mallocFloat(16);
                matrix.get(fb);
                glUniformMatrix4fv(loc, false, fb);
            }
        }
    }

    public int getProgramId() {
        return programId;
    }

    public void cleanup() {
        glDeleteProgram(programId);
    }
}
