#version 150

// 明るい所だけ深度に残す。ぼんやりした光の縁は捨てて、後ろの景色を隠さない
in float alpha;

out vec4 fragColor;

void main() {
    if (alpha < 0.6) {
        discard;
    }
    fragColor = vec4(0.0);
}
