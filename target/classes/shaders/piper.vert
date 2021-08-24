#version 330
uniform mat4 mat;      // A constant representing the combined model/view/projection matrix.
uniform mat4 mv;       // A constant representing the combined model/view matrix.

in vec3 inPosition;     // Per-vertex position information we will pass in.
in vec3 inNormal;       // Per-vertex normal information we will pass in.
in vec2 inTexCoord;

out vec3 v_Position;       // This will be passed into the fragment shader.
out vec3 v_Normal;         // This will be passed into the fragment shader.
out vec2 texCoord;         // This will be passed into the fragment shader.

//Based on code by https://www.learnopengles.com/tag/per-vertex-lighting/

// The entry point for our vertex shader.
void main()
{
    // Transform the vertex into eye space.
    v_Position = vec3(mv * vec4(inPosition, 1.0));

    // Transform the normal's orientation into eye space.
    v_Normal = vec3(mv * vec4(inNormal, 1.0));

    // gl_Position is a special variable used to store the final position.
    // Multiply the vertex by the matrix to get the final point in normalized screen coordinates.
    gl_Position = mat * vec4(inPosition, 1.0);
    texCoord = vec2(inTexCoord.x,  inTexCoord.y);
}