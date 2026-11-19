#version 150

uniform sampler2D WeaponColor;
uniform sampler2D WeaponDepth;
uniform sampler2D TerrainColor;
uniform sampler2D TerrainDepth;

out vec4 fragColor;

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 weapon = texelFetch(WeaponColor, pixel, 0);
    vec4 terrain = texelFetch(TerrainColor, pixel, 0);
    // Additive halo pixels can retain RGB even when their alpha rounds to zero.
    if (all(equal(weapon, vec4(0.0))) && all(equal(terrain, vec4(0.0)))) discard;

    float weaponZ = texelFetch(WeaponDepth, pixel, 0).r;
    float terrainZ = texelFetch(TerrainDepth, pixel, 0).r;
    // Each target has accumulated premultiplied colors over transparent black.
    fragColor = weaponZ < terrainZ
        ? weapon + terrain * (1.0 - weapon.a)
        : terrain + weapon * (1.0 - terrain.a);
}
