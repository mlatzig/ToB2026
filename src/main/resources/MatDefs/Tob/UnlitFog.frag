// ToB 2026 Phase 4b-4: jME 3.9's Common/MatDefs/Misc/Unshaded.frag (colour map, vertex colour, colour) plus
// Lighting.frag's linear fog, applied to the colour only (the alpha stays).
#import "Common/ShaderLib/GLSLCompat.glsllib"

#ifdef USE_FOG
    #import "Common/ShaderLib/MaterialFog.glsllib"
#endif

#if defined(DISCARD_ALPHA)
    uniform float m_AlphaDiscardThreshold;
#endif

uniform vec4 m_Color;
uniform sampler2D m_ColorMap;

varying vec2 texCoord1;
varying vec4 vertColor;

void main(){
    vec4 color = vec4(1.0);

    #ifdef HAS_COLORMAP
        color *= texture2D(m_ColorMap, texCoord1);
    #endif

    #ifdef HAS_VERTEXCOLOR
        color *= vertColor;
    #endif

    #ifdef HAS_COLOR
        color *= m_Color;
    #endif

    #if defined(DISCARD_ALPHA)
        if(color.a < m_AlphaDiscardThreshold){
           discard;
        }
    #endif

    #ifdef USE_FOG
        // the colour only: a blended sprite keeps its alpha
        color.rgb = MaterialFog_calculateFogColor(color).rgb;
    #endif

    gl_FragColor = color;
}
