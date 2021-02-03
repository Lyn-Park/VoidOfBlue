#version 430

in mat4 instance_ProjectionViewModelMatrix;

in vec3 in_Position;
in vec3 in_TexCoord;

out vec3 pass_TexCoord;

void main()
{
    gl_Position = instance_ProjectionViewModelMatrix * vec4(in_Position, 1.0);
    pass_TexCoord = in_TexCoord;
}
