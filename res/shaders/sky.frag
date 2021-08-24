#version 330
in vec2 texCoord;
out vec4 outColor;
uniform sampler2D textureID;
void main() {
    outColor = texture(textureID, texCoord);
}