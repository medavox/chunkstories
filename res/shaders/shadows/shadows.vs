#version 130
attribute vec4 vertexIn;
attribute vec4 normalIn;
attribute vec2 texCoordIn;

varying vec4 texcoord;

uniform mat4 depthMVP;

uniform float time;

uniform float vegetation;

const float distScale = 0.9;

uniform vec3 objectPosition;

uniform float entity;

uniform mat4 localTransform;
uniform mat4 boneTransform;

vec4 accuratizeShadow(vec4 shadowMap)
{
	//shadowMap *= 2.0 - 1.0;
	
	shadowMap.xy *= 1.0 /( (1.0f - distScale) + sqrt(shadowMap.x * shadowMap.x + shadowMap.y * shadowMap.y) * distScale );
	
	//shadowMap *= 0.5 + 0.5;
	
	return shadowMap;
}

void main(){
	texcoord = vec4(texCoordIn/32768.0,0,0);
	//gl_Position = ftransform();
	vec4 v = localTransform * boneTransform * vec4(vertexIn.xyz, 1);
	
	float movingness = normalIn.w * (1-entity);
	<ifdef dynamicGrass>
	if(movingness > 0)
	{
		v.x += sin(time + v.z + v.y / 2.0) * 0.1;
		v.z += cos(time + v.x*1.5 + 0.3) * 0.1;
	}
	<endif dynamicGrass>
	
	v.xyz += objectPosition;
	gl_Position = accuratizeShadow(depthMVP * v);
}
