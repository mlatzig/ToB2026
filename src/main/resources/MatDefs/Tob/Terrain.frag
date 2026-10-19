// ToB 2026 Phase 4b-4: the terrain's fragment shader. jME 3.9's Common/MatDefs/Light/Lighting.frag (per-pixel
// Phong, no normal map, multi-pass lights, linear fog) with the diffuse colour of ToB 2005's Ground pixel shader
// (Ground-14.psh): ground texture x ground detail texture x ground weight + mountain texture x mountain weight. The
// weights come from Ground-14.vsh (constants c13) through TexCoord2; the lerp to ground colour 2 is in the vertex
// colours (Terrain.java).
#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/BlinnPhongLighting.glsllib"
#import "Common/ShaderLib/Lighting.glsllib"

#ifdef USE_FOG
    #import "Common/ShaderLib/MaterialFog.glsllib"
#endif

varying vec2 texCoord;
// x: the ground weight, y: the mountain weight
varying vec2 texCoord2;

varying vec3 AmbientSum;
varying vec4 DiffuseSum;
varying vec3 SpecularSum;

uniform vec4 g_LightDirection;
varying vec3 vViewDir;
varying vec4 vLightDir;
varying vec3 lightVec;
varying vec3 vNormal;

uniform sampler2D m_DiffuseMap;
uniform sampler2D m_DetailMap;
uniform sampler2D m_MountainMap;
uniform float m_DetailScale;
uniform float m_Shininess;

void main(){
    vec4 ground = texture2D(m_DiffuseMap, texCoord) * texture2D(m_DetailMap, texCoord * m_DetailScale);
    vec4 diffuseColor = ground * texCoord2.x + texture2D(m_MountainMap, texCoord) * texCoord2.y;
    float alpha = DiffuseSum.a;

    vec3 normal = normalize(vNormal);
    vec4 lightDir = vLightDir;
    lightDir.xyz = normalize(lightDir.xyz);
    vec3 viewDir = normalize(vViewDir);
    float spotFallOff = 1.0;

    #if __VERSION__ >= 110
    // allow use of control flow
    if(g_LightDirection.w != 0.0){
    #endif
        spotFallOff = computeSpotFalloff(g_LightDirection, lightVec);
    #if __VERSION__ >= 110
        if(spotFallOff <= 0.0){
            gl_FragColor.rgb = AmbientSum * diffuseColor.rgb;
            gl_FragColor.a = alpha;
            return;
        }
    }
    #endif

    vec2 light = computeLighting(normal, viewDir, lightDir.xyz, lightDir.w * spotFallOff, m_Shininess);
    gl_FragColor.rgb = AmbientSum * diffuseColor.rgb
            + DiffuseSum.rgb * diffuseColor.rgb * vec3(light.x)
            + SpecularSum * vec3(light.y);

    // add fog after the lighting, as Lighting.frag does
    #ifdef USE_FOG
        gl_FragColor = MaterialFog_calculateFogColor(vec4(gl_FragColor.rgb, alpha));
    #endif

    gl_FragColor.a = alpha;
}
