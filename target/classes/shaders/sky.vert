#version 330
in vec3 inPosition;
in vec2 inTexCoord;
out vec2 texCoord;
uniform mat4 mat;      // A constant representing the combined model/view/projection matrix.
void main() {
    gl_Position = mat * vec4(inPosition , 1.0);
    texCoord = vec2( inTexCoord.x,  1.0 - inTexCoord.y);
}