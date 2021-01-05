#version 150 core

uniform uvec3 windowSize;

in mat4 instance_ModelMatrix;

in vec3 in_Position;
in vec2 in_TexCoord;

out vec2 pass_TexCoord;

void main()
{
    gl_Position = instance_ModelMatrix * vec4(in_Position, 1.0);
    gl_Position.xyz = (2.0 * gl_Position.xyz / windowSize) - 1.0;
    pass_TexCoord = in_TexCoord;
}
