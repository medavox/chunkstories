#version 120
// Copyright 2015 XolioWare Interactive

uniform float currentTiming;

varying float height;
varying float pos;

void main()
{
	//Diffuse G-Buffer
	float alpha = 1.0 - (mod(currentTiming - pos + 1000, 1000))/1000.0f;
	
	float r = max(height - 0.6f, 0.0) / 33.3f;
	float g = 1-max(height - 16.6f, 0.0) / 33.3f;
	
	gl_FragColor = vec4(r, g, 0.0, alpha );
}