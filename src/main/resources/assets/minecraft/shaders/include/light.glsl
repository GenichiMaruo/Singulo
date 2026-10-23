#version 150

#define MINECRAFT_LIGHT_POWER   (0.6)
#define MINECRAFT_AMBIENT_LIGHT (0.4)

vec4 minecraft_mix_light(vec3 lightDir0, vec3 lightDir1, vec3 normal, vec4 color) {
    float light0 = max(0.0, dot(lightDir0, normal));
    float light1 = max(0.0, dot(lightDir1, normal));
    float lightAccum = min(1.0, (light0 + light1) * MINECRAFT_LIGHT_POWER + MINECRAFT_AMBIENT_LIGHT);
    return vec4(color.rgb * lightAccum, color.a);
}

vec4 minecraft_sample_lightmap(sampler2D lightMap, ivec2 uv) {
    // Natural and artificial sky keep separate coordinates. Sample their contiguous ramps
    // with vanilla's exact half-texel interpolation, then combine the resulting colors.
    float artificial = floor(float(uv.y) / 256.0);
    vec2 pixel = clamp(vec2(uv.x, float(uv.y) - artificial * 256.0) / 16.0, vec2(0.5), vec2(15.5));
    vec2 size = vec2(textureSize(lightMap, 0));
    vec4 naturalColor = texture(lightMap, pixel / size);
    if (artificial == 0.0) return naturalColor;
    vec2 noonPixel = vec2(pixel.x, 256.0 + clamp(artificial, 0.5, 15.5));
    return max(naturalColor, texture(lightMap, noonPixel / size));
}
