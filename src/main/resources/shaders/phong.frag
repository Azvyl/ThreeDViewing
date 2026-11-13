// ==========================================================
// FILE: src/main/resources/shaders/phong.frag (Fragment Shader)
// Modul Minggu 11 - Praktikum 1, 3 & 4: Phong vs Blinn-Phong, UV Texture & Shadow Mapping
// ==========================================================
#version 330 core
out vec4 FragColor;

in vec3 FragPos;
in vec3 Normal;
in vec2 TexCoords;
in vec4 FragPosLightSpace;
in vec3 GouraudLighting;

uniform vec3 lightPos;
uniform vec3 viewPos;
uniform vec3 lightColor;
uniform vec3 objectColor;

uniform sampler2D textureSampler;
uniform sampler2D shadowMap;

uniform bool useTexture;
uniform bool useBlinnPhong;
uniform bool enableShadows;
uniform bool usePCF;
uniform bool useGouraud;

uniform float shadowBiasMin;
uniform float shadowBiasSlope;

// Fungsi Perhitungan Bayangan 2-Pass Shadow Mapping (Indikator 5)
float calculateShadowFactor(vec4 fragPosLightSpace, vec3 norm, vec3 lightDir) {
    if (!enableShadows) {
        return 1.0; // Penuh terkena cahaya
    }

    // Transformasi ke koordinat NDC [-1, 1] lalu ke rentang UV Depth Map [0, 1]
    vec3 projCoords = fragPosLightSpace.xyz / fragPosLightSpace.w;
    projCoords = projCoords * 0.5 + 0.5;

    // Jika berada di luar jangkauan far plane frustum cahaya, anggap tidak berbayang
    if (projCoords.z > 1.0) {
        return 1.0;
    }

    float currentDepth = projCoords.z;

    // Rumus Slope-Scaled Shadow Bias sesuai Modul Bagian I.5.b:
    // bias = max(0.05 * (1.0 - N . L), 0.005) (atau sesuai parameter tuning interaktif)
    float cosTheta = max(dot(norm, lightDir), 0.0);
    float bias = max(shadowBiasSlope * (1.0 - cosTheta), shadowBiasMin);

    float shadowVisibility = 1.0;

    if (usePCF) {
        // Percentage-Closer Filtering (PCF 3x3) untuk menghaluskan gerigi tepi bayangan
        float shadowSum = 0.0;
        vec2 texelSize = 1.0 / vec2(textureSize(shadowMap, 0));
        for (int x = -1; x <= 1; ++x) {
            for (int y = -1; y <= 1; ++y) {
                float pcfDepth = texture(shadowMap, projCoords.xy + vec2(x, y) * texelSize).r;
                // Sesuai Modul Bagian I.5.a.3: Shadow Factor = 0.2 jika d_current > d_shadow + bias
                shadowSum += (currentDepth > pcfDepth + bias) ? 0.2 : 1.0;
            }
        }
        shadowVisibility = shadowSum / 9.0;
    } else {
        // Pengujian kedalaman langsung 1-sampel sesuai rumus dasar modul
        float closestDepth = texture(shadowMap, projCoords.xy).r;
        shadowVisibility = (currentDepth > closestDepth + bias) ? 0.2 : 1.0;
    }

    return shadowVisibility;
}

void main() {
    // 1. Ambient Lighting
    float ambientStrength = 0.22;
    vec3 ambient = ambientStrength * lightColor;

    // 2. Diffuse Lighting (Lambertian Reflectance: I_diff = k_d * I_d * max(N . L, 0))
    vec3 norm = normalize(Normal);
    vec3 lightDir = normalize(lightPos - FragPos);
    float diff = max(dot(norm, lightDir), 0.0);
    vec3 diffuse = diff * lightColor;

    // 3. Specular Lighting (Standard Phong vs Blinn-Phong Halfway Vector H)
    float specularStrength = 0.85;
    vec3 viewDir = normalize(viewPos - FragPos);
    float spec = 0.0;

    if (diff > 0.0) {
        if (useBlinnPhong) {
            vec3 halfwayDir = normalize(lightDir + viewDir);
            spec = pow(max(dot(norm, halfwayDir), 0.0), 32.0); // Blinn-Phong (n = 32.0)
        } else {
            vec3 reflectDir = reflect(-lightDir, norm);
            spec = pow(max(dot(viewDir, reflectDir), 0.0), 16.0); // Standard Phong (n = 16.0)
        }
    }
    vec3 specular = specularStrength * spec * lightColor;

    // 4. Evaluasi Shadow Mapping (Pass 2)
    float shadowFactor = calculateShadowFactor(FragPosLightSpace, norm, lightDir);

    // 5. Kombinasi Warna Dasar / Tekstur UV dengan Pencahayaan & Bayangan
    vec3 baseColor = useTexture
            ? texture(textureSampler, TexCoords).rgb * objectColor
            : objectColor;

    vec3 lighting;
    if (useGouraud) {
        lighting = GouraudLighting * shadowFactor;
    } else {
        // Komponen ambient tetap terjaga, sedangkan diffuse & specular dipengaruhi faktor bayangan
        // sekaligus memastikan di area bayangan pekat intensitas mengikuti proporsi Shadow Factor
        float directWeight = (shadowFactor - 0.2) / 0.8;
        lighting = ambient + directWeight * (diffuse + specular);
    }

    vec3 result = lighting * baseColor;
    FragColor = vec4(result, 1.0);
}
