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
}
