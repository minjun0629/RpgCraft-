#version 150

#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexDistance = fog_distance(ModelViewMat, IViewRotMat * Position, FogShape);
    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;

    // RpgCraft: hide red sidebar score numbers (0xFF5555) drawn at the far right edge of the GUI.
    if (abs(gl_Position.w - 1.0) < 0.0001 && gl_Position.x > 0.88
            && Color.r > 0.99 && abs(Color.g - 0.3333) < 0.01 && abs(Color.b - 0.3333) < 0.01) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    }

    // RpgCraft: 4R-style target panel - text in reserved colors is anchored to the top-right of the screen, its shadow hidden.
    if (abs(gl_Position.w - 1.0) < 0.0001) {
        vec3 c255 = Color.rgb * 255.0;
        if (all(lessThan(abs(c255 - vec3(63.0, 63.0, 62.0)), vec3(0.6))) || all(lessThan(abs(c255 - vec3(63.0, 56.0, 32.0)), vec3(0.6))) || all(lessThan(abs(c255 - vec3(63.0, 24.0, 24.0)), vec3(0.6))) || all(lessThan(abs(c255 - vec3(50.0, 50.0, 49.0)), vec3(0.6))) || all(lessThan(abs(c255 - vec3(62.0, 63.0, 63.0)), vec3(0.6)))) {
            gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        } else if (all(lessThan(abs(c255 - vec3(252.0, 252.0, 248.0)), vec3(0.6))) || all(lessThan(abs(c255 - vec3(252.0, 224.0, 128.0)), vec3(0.6))) || all(lessThan(abs(c255 - vec3(252.0, 96.0, 96.0)), vec3(0.6))) || all(lessThan(abs(c255 - vec3(200.0, 200.0, 196.0)), vec3(0.6))) || all(lessThan(abs(c255 - vec3(248.0, 252.0, 252.0)), vec3(0.6)))) {
            gl_Position.x += 1.0 - (184.0 * 0.5 + -21.0) * ProjMat[0][0];
            gl_Position.y += 22.0 * ProjMat[1][1];
            if (all(lessThan(abs(c255 - vec3(252.0, 224.0, 128.0)), vec3(0.6))) || all(lessThan(abs(c255 - vec3(252.0, 96.0, 96.0)), vec3(0.6))) || all(lessThan(abs(c255 - vec3(200.0, 200.0, 196.0)), vec3(0.6))) || all(lessThan(abs(c255 - vec3(248.0, 252.0, 252.0)), vec3(0.6)))) gl_Position.z -= 0.002;
        }
    }
}
