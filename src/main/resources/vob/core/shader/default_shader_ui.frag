#version 150 core

uniform sampler2D diffuseTexture;

in vec2 pass_TexCoord;

out vec4 out_FragColor;

void main()
{
    out_FragColor = texture(diffuseTexture, pass_TexCoord);
}
