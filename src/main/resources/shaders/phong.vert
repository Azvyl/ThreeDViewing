// ==========================================================
// FILE: src/main/resources/shaders/phong.vert (Vertex Shader)
// Modul Minggu 11 - Praktikum 1 & 4: Shading Models, UV Mapping & Shadow Mapping
// ==========================================================
#version 330 core
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec3 aNormal;
layout (location = 2) in vec2 aTexCoords;

out vec3 FragPos;
out vec3 Normal;
out vec2 TexCoords;
out vec4 FragPosLightSpace;
out vec3 GouraudLighting;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;
uniform mat4 lightSpaceMatrix;

// Uniform untuk evaluasi Gouraud Shading (Per-Vertex Lighting) sebagai pembanding Smooth Shading
uniform vec3 lightPos;
uniform vec3 viewPos;
uniform vec3 lightColor;
uniform bool useBlinnPhong;
uniform float uvScale;

void main() {
    vec4 worldPos = model * vec4(aPos, 1.0);
    FragPos = vec3(worldPos);
    Normal = mat3(transpose(inverse(model))) * aNormal; // Normal Matrix
    TexCoords = aTexCoords * max(uvScale, 0.001);
    FragPosLightSpace = lightSpaceMatrix * worldPos;

    // Evaluasi Per-Vertex Lighting (Gouraud Shading - Indikator 2.b.1)
    vec3 norm = normalize(Normal);
    vec3 lightDir = normalize(lightPos - FragPos);
    vec3 viewDir = normalize(viewPos - FragPos);

    float ambientStrength = 0.22;
    vec3 ambient = ambientStrength * lightColor;

    float diff = max(dot(norm, lightDir), 0.0);
    vec3 diffuse = diff * lightColor;

    float specularStrength = 0.85;
    float spec = 0.0;
    if (diff > 0.0) {
        if (useBlinnPhong) {
            vec3 halfwayDir = normalize(lightDir + viewDir);
            spec = pow(max(dot(norm, halfwayDir), 0.0), 32.0);
        } else {
            vec3 reflectDir = reflect(-lightDir, norm);
            spec = pow(max(dot(viewDir, reflectDir), 0.0), 16.0);
        }
    }
    vec3 specular = specularStrength * spec * lightColor;
    GouraudLighting = ambient + diffuse + specular;

    gl_Position = projection * view * worldPos;
}
