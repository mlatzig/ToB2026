// ToB 2026 Phase 5e shadow fix: the post-shadow fragment shader of MatDefs/Tob/Lighting.j3md and Terrain.j3md.
// jME 3.9's Common/MatDefs/Shadow/PostShadow.frag, changed in one place: a face turned away from the sun (nDotL > 0) is
// always in full shadow instead of being compared with the shadow maps (it gets no sunlight anyway; the comparison
// gave its acne and the light leaks at its upper edge). Faces towards the sun are looked up as in jME.
#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/Shadows.glsllib"
#if defined(PSSM) || defined(FADE)
varying float shadowPosition;
#endif

varying vec4 projCoord0;
varying vec4 projCoord1;
varying vec4 projCoord2;
varying vec4 projCoord3;
varying float nDotL;

#ifdef POINTLIGHT
    varying vec4 projCoord4;
    varying vec4 projCoord5;
    uniform vec3 m_LightPos;
    varying vec4 worldPos;
#else
    #ifndef PSSM        
        varying float lightDot;
    #endif
#endif

#ifdef DISCARD_ALPHA
    #ifdef COLOR_MAP
        uniform sampler2D m_ColorMap;
    #else    
        uniform sampler2D m_DiffuseMap;
    #endif
    uniform float m_AlphaDiscardThreshold;
    varying vec2 texCoord;
#endif

#ifdef FADE
uniform vec2 m_FadeInfo;
#endif

void main(){   
 
    #ifdef DISCARD_ALPHA
        #ifdef COLOR_MAP
            float alpha = texture2D(m_ColorMap,texCoord).a;
        #else    
            float alpha = texture2D(m_DiffuseMap,texCoord).a;
        #endif
        if(alpha<=m_AlphaDiscardThreshold){
            discard;
        }
    #endif

    float shadow = 1.0;
    if(nDotL > 0.0){
        shadow = 0.0;
    } else {

    #ifdef POINTLIGHT         
            shadow = getPointLightShadows(worldPos, m_LightPos,
                           m_ShadowMap0,m_ShadowMap1,m_ShadowMap2,m_ShadowMap3,m_ShadowMap4,m_ShadowMap5,
                           projCoord0, projCoord1, projCoord2, projCoord3, projCoord4, projCoord5);
    #else
       #ifdef PSSM
            shadow = getDirectionalLightShadows(m_Splits, shadowPosition,
                           m_ShadowMap0,m_ShadowMap1,m_ShadowMap2,m_ShadowMap3,
                           projCoord0, projCoord1, projCoord2, projCoord3);
       #else 
            //spotlight
            if(lightDot < 0.0){
                gl_FragColor = vec4(1.0);
                return;
            }
            shadow = getSpotLightShadows(m_ShadowMap0,projCoord0);
       #endif
    #endif
    }

    #ifdef FADE
        shadow = max(0.0, mix(shadow, 1.0, max(0.0, (shadowPosition - m_FadeInfo.x) * m_FadeInfo.y)));
    #endif

    shadow = shadow * m_ShadowIntensity + (1.0 - m_ShadowIntensity);
    gl_FragColor = vec4(shadow, shadow, shadow, 1.0);

}

