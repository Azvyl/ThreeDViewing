// ==========================================================
// FILE: src/main/resources/shaders/shadow_depth.vert
// Modul Minggu 11 - Praktikum 4 (Indikator 5): Render Pass 1 (Depth Pass)
// ==========================================================
#version 330 core
layout (location = 0) in vec3 aPos;

uniform mat4 lightSpaceMatrix;
uniform mat4 model;

void main() {
    gl_Position = lightSpaceMatrix * model * vec4(aPos, 1.0);
}
