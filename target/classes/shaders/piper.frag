#version 330
uniform vec3 lightPosition;
uniform sampler2D textureID;

in vec2 texCoord;
in vec3 v_Position;
in vec3 v_Normal;

out vec4 outColor;

//Based on code by https://www.learnopengles.com/tag/per-vertex-lighting/

void main()
{
    // Will be used for attenuation.
    float distance = length(lightPosition - v_Position);

    // Get a lighting direction vector from the light to the vertex.
    vec3 lightVector = normalize(lightPosition - v_Position);

    // Calculate the dot product of the light vector and vertex normal. If the normal and light vector are
    // pointing in the same direction then it will get max illumination.
    float diffuse = max(dot(v_Normal, lightVector), 0.25);

    // Add attenuation.
    diffuse = diffuse * (1.1 / (0.7 + (0.01 * distance * distance)));

    // Multiply the color by the diffuse illumination level and the texture color data to get final output color.
    vec4 interColor = texture(textureID, texCoord);
    outColor = vec4(interColor.r * diffuse * 1.35, interColor.gba * diffuse);
}