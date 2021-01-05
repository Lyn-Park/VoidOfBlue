#version 150 core

in vec3 in_Position;
in vec2 in_TexCoord;

in mat4 instance_ProjectionViewModelMatrix;

out vec2 pass_TexCoord;

void main()
{
    gl_Position = instance_ProjectionViewModelMatrix * vec4(in_Position, 1.0);
    pass_TexCoord = in_TexCoord;
}
