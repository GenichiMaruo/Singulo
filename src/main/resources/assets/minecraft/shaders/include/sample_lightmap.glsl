#version 330
vec4 sample_lightmap(sampler2D lightMap, ivec2 uv) {
    vec2 size = vec2(textureSize(lightMap, 0));
    if (size.y != 512.0) return texture(lightMap, clamp((uv / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
    float artificial = floor(float(uv.y) / 256.0);
    vec2 pixel = clamp(vec2(uv.x, float(uv.y) - artificial * 256.0) / 16.0 + 0.5, vec2(0.5), vec2(15.5));
    vec4 naturalColor = texture(lightMap, pixel / size);
    if (artificial == 0.0) return naturalColor;
    vec2 noonPixel = vec2(pixel.x, 256.0 + clamp(artificial + 0.5, 0.5, 15.5));
    return max(naturalColor, texture(lightMap, noonPixel / size));
}
