// ToB 2026 Phase 4b-4: jME 3.9's Common/MatDefs/Misc/Unshaded.vert (colour map, vertex colour) plus the fog
// distance of Common/MatDefs/Light/Lighting.vert.
#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/Instancing.glsllib"

attribute vec3 inPosition;
attribute vec2 inTexCoord;
attribute vec4 inColor;

varying vec2 texCoord1;
varying vec4 vertColor;

#ifdef USE_FOG
    varying float fogDistance;
    uniform vec3 g_CameraPosition;
#endif

void main(){
    #ifdef HAS_COLORMAP
        texCoord1 = inTexCoord;
    #endif

    #ifdef HAS_VERTEXCOLOR
        vertColor = inColor;
    #endif

    vec4 modelSpacePos = vec4(inPosition, 1.0);
    gl_Position = TransformWorldViewProjection(modelSpacePos);

    #ifdef USE_FOG
        fogDistance = distance(g_CameraPosition, TransformWorld(modelSpacePos).xyz);
    #endif
}
