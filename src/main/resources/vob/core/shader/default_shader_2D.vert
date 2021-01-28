#version 430

layout(std430) buffer weightSSBO
{
    uint weight_rows;
    uint weight_columns;
    float[] weights;
};

layout(std430) buffer skeletonSSBO
{
    mat4[] bones;
};

in mat4 instance_ProjectionViewModelMatrix;

in vec3 in_Position;
in vec2 in_TexCoord;

out vec2 pass_TexCoord;

void main()
{
    vec4 posVec = vec4(in_Position, 1.0);

    if (weight_rows > 0) {
        uint offset = gl_VertexID * weight_columns;

        for(uint i = 0; i < weight_columns; ++i)
        {
            posVec += bones[i] * weights[offset + i] * posVec;
        }
    }

    gl_Position = instance_ProjectionViewModelMatrix * posVec;
    pass_TexCoord = in_TexCoord;
}
