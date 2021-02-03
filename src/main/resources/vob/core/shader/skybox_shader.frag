#version 150 core

uniform samplerCube diffuseTexture;

in vec3 pass_TexCoord;

out vec4 out_FragColor;

void main()
{
    out_FragColor = texture(diffuseTexture, pass_TexCoord);
}
