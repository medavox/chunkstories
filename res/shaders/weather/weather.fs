#version 120
// Copyright 2015 XolioWare Interactive

varying vec4 interpolatedColor;

uniform mat4 projectionMatrix;
uniform mat4 projectionMatrixInv;

uniform mat4 modelViewMatrix;
uniform mat4 modelViewMatrixInv;

uniform mat3 normalMatrix;
uniform mat3 normalMatrixInv;

void main()
{
	//Diffuse G-Buffer
	gl_FragColor = interpolatedColor;
}