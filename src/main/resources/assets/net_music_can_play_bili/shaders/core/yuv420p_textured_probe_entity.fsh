#version 330

// 1.20.1 的 include 是**无命名空间**写法（原版 `#moj_import <fog.glsl>`），
// 而且它只有 `linear_fog / linear_fog_fade / fog_distance` 三个函数 ——
// 1.21 的 `apply_fog(...)` 与 `FogEnvironmental*` 在这里**不存在**（client.jar 实证）。
#moj_import <fog.glsl>
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    float y = texture(Sampler0, texCoord0).r;
    vec4 color = vec4(y, 0.0, 0.0, 1.0) * vertexColor * ColorModulator;
    fragColor = linear_fog(color, sphericalVertexDistance, FogStart, FogEnd, FogColor);
}