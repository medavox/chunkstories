package xyz.chunkstories.graphics.common

import org.joml.Vector4d
import xyz.chunkstories.api.client.IngameClient
import xyz.chunkstories.api.entity.traits.serializable.TraitControllable
import xyz.chunkstories.api.graphics.TextureFormat
import xyz.chunkstories.api.graphics.TextureTilingMode
import xyz.chunkstories.api.graphics.rendergraph.*
import xyz.chunkstories.api.graphics.structs.Camera
import xyz.chunkstories.api.graphics.systems.dispatching.*
import xyz.chunkstories.api.graphics.systems.drawing.FarTerrainDrawer
import xyz.chunkstories.api.graphics.systems.drawing.FullscreenQuadDrawer
import xyz.chunkstories.api.gui.GuiDrawer
import xyz.chunkstories.api.math.random.PrecomputedSimplexSeed
import xyz.chunkstories.graphics.GLFWBasedGraphicsBackend
import xyz.chunkstories.graphics.common.world.doShadowMapping
import xyz.chunkstories.graphics.vulkan.VulkanBackendOptions
import xyz.chunkstories.graphics.vulkan.VulkanGraphicsBackend
import xyz.chunkstories.graphics.vulkan.systems.Vulkan3DVoxelRaytracer
import xyz.chunkstories.world.WorldClientCommon

abstract class WorldRenderer(val world: WorldClientCommon) : Cleanable {
    abstract val backend: GLFWBasedGraphicsBackend

    fun createInstructions(client: IngameClient): RenderGraphDeclarationScript = {
        val precomputedSimplesSeed = PrecomputedSimplexSeed(world.worldInfo.seed)

        renderTask {
            name = "main"

            finalPassName = "gui"

            renderBuffers {
                renderBuffer {
                    name = "depthBuffer"

                    format = TextureFormat.DEPTH_32
                    size = viewportSize
                }

                renderBuffer {
                    name = "colorBuffer"

                    format = TextureFormat.RGBA_8
                    size = viewportSize
                }

                renderBuffer {
                    name = "normalBuffer"

                    format = TextureFormat.RGBA_8
                    size = viewportSize
                }

                renderBuffer {
                    name = "shadedBuffer"

                    format = TextureFormat.RGB_HDR
                    size = viewportSize
                }

                renderBuffer {
                    name = "finalBuffer"

                    format = TextureFormat.RGBA_8
                    size = viewportSize
                }

                renderBuffer {
                    name = "bloom_temp"

                    format = TextureFormat.RGB_HDR
                    size = viewportSize * 0.5
                }

                renderBuffer {
                    name = "bloom"

                    format = TextureFormat.RGB_HDR
                    size = viewportSize * 0.5
                }

                renderBuffer {
                    name = "bloom_temp2"

                    format = TextureFormat.RGB_HDR
                    size = viewportSize * 0.0625
                }

                renderBuffer {
                    name = "bloom2"

                    format = TextureFormat.RGB_HDR
                    size = viewportSize * 0.0625
                }

                val shadowCascades = client.configuration.getIntValue(CommonGraphicsOptions.shadowCascades)
                val shadowResolution = client.configuration.getIntValue(CommonGraphicsOptions.shadowMapSize)
                for (i in 0 until shadowCascades) {
                    renderBuffer {
                        name = "shadowBuffer$i"

                        format = TextureFormat.DEPTH_32
                        size = shadowResolution by shadowResolution
                    }
                }
            }

            passes {
                pass {
                    name = "sky"

                    draws {
                        system(FullscreenQuadDrawer::class) {
                            setup {
                                val entity = client.player.controlledEntity
                                val world = client.world

                                shaderResources.supplyUniformBlock("world", world.getConditions())
                            }
                        }
                    }

                    outputs {
                        output {
                            name = "shadedBuffer"
                            clear = true
                            clearColor = Vector4d(0.0, 0.5, 1.0, 1.0)
                        }
                    }
                }

                pass {
                    name = "opaque"

                    dependsOn("sky")

                    draws {
                        system(ChunksRenderer::class) {
                            shader = "blockMeshes"
                            materialTag = "opaque"
                        }
                        system(ModelsRenderer::class) {
                            shader = "modelsDeferred"
                            materialTag = "opaque"
                            supportsAnimations = true
                        }
                        if (backend is VulkanGraphicsBackend)
                            system(SpritesRenderer::class) {
                                shader = "sprites"
                                materialTag = "opaque"
                            }
                            /*system(FarTerrainDrawer::class) {

                            }*/
                    }

                    setup {
                        shaderResources.supplyUniformBlock("simplexSeed", precomputedSimplesSeed)
                    }

                    outputs {
                        output {
                            name = "colorBuffer"
                            clear = true
                            clearColor = Vector4d(0.0, 0.0, 0.0, 0.0)
                            blending = PassOutput.BlendMode.OVERWRITE
                        }

                        output {
                            name = "normalBuffer"
                            clear = true
                            clearColor = Vector4d(0.0, 0.0, 0.0, 0.0)
                            blending = PassOutput.BlendMode.OVERWRITE
                        }
                    }

                    depth {
                        enabled = true
                        depthBuffer = renderBuffer("depthBuffer")
                        clear = true
                    }
                }

                pass {
                    name = "deferredSun"

                    dependsOn("opaque")

                    setup {
                        shaderResources.supplyImage("colorBuffer") {
                            source = renderBuffer("colorBuffer")
                        }

                        shaderResources.supplyImage("normalBuffer") {
                            source = renderBuffer("normalBuffer")
                            scalingMode = ImageInput.ScalingMode.LINEAR
                        }

                        shaderResources.supplyImage("depthBuffer") {
                            source = renderBuffer("depthBuffer")
                            scalingMode = ImageInput.ScalingMode.LINEAR
                        }
                    }

                    draws {
                        if (backend is VulkanGraphicsBackend && client.configuration.getBooleanValue(VulkanBackendOptions.raytracedGI)) {
                            system(Vulkan3DVoxelRaytracer::class)
                        } else {
                            system(FullscreenQuadDrawer::class) {
                                shader = "deferredShading"

                                setup {
                                    shaderResources.supplyUniformBlock("world", world.getConditions())

                                    doShadowMapping(this, world)
                                }
                            }
                        }
                    }

                    outputs {
                        output {
                            name = "colorOut"
                            target = renderBuffer("shadedBuffer")
                            blending = PassOutput.BlendMode.OVERWRITE
                        }
                    }
                }

                pass {
                    name = "deferredLights"

                    dependsOn("deferredSun")

                    setup {
                        shaderResources.supplyImage("colorBuffer") {
                            source = renderBuffer("colorBuffer")
                        }

                        shaderResources.supplyImage("normalBuffer") {
                            source = renderBuffer("normalBuffer")
                            scalingMode = ImageInput.ScalingMode.LINEAR
                        }

                        shaderResources.supplyImage("depthBuffer") {
                            source = renderBuffer("depthBuffer")
                            scalingMode = ImageInput.ScalingMode.LINEAR
                        }
                    }

                    draws {
                        system(DefferedLightsRenderer::class) {
                            setup {
                                val camera = client.player.controlledEntity?.traits?.get(TraitControllable::class)?.camera ?: Camera()
                                shaderResources.supplyUniformBlock("camera", camera)
                            }
                        }
                    }

                    outputs {
                        output {
                            name = "colorOut"
                            target = renderBuffer("shadedBuffer")
                            blending = PassOutput.BlendMode.ADD
                        }
                    }
                }

                pass {
                    name = "forward"

                    dependsOn("deferredLights")

                    draws {
                        system(ChunksRenderer::class) {
                            shader = "water"
                            materialTag = "water"

                            setup {
                                shaderResources.supplyImage("waterNormalDeep") {
                                    source = asset("textures/water/deep.png")
                                    tilingMode = TextureTilingMode.REPEAT
                                    scalingMode = ImageInput.ScalingMode.LINEAR
                                }
                                shaderResources.supplyImage("waterNormalShallow") {
                                    source = asset("textures/water/shallow.png")
                                    tilingMode = TextureTilingMode.REPEAT
                                    scalingMode = ImageInput.ScalingMode.LINEAR
                                }
                            }
                        }

                        system(LinesRenderer::class) {
                            setup {
                            }
                        }

                        system(ModelsRenderer::class) {
                            materialTag = "forward"
                            shader = "modelsForward"
                        }
                    }

                    outputs {
                        output {
                            name = "shadedBuffer"
                            blending = PassOutput.BlendMode.MIX
                        }
                    }

                    depth {
                        enabled = true
                        depthBuffer = renderBuffer("depthBuffer")
                    }
                }

                pass {
                    name = "bloom_blurH"

                    dependsOn("forward")

                    draws {
                        system(FullscreenQuadDrawer::class) {
                            shader = "blur_horizontal"
                        }
                    }

                    setup {
                        shaderResources.supplyImage("inputTexture") {
                            source = renderBuffer("shadedBuffer")
                            scalingMode = ImageInput.ScalingMode.LINEAR
                        }
                    }

                    outputs {
                        output {
                            name = "fragColor"
                            target = renderBuffer("bloom_temp")
                            blending = PassOutput.BlendMode.OVERWRITE
                        }
                    }
                }

                pass {
                    name = "bloom_blurV"

                    dependsOn("bloom_blurH")

                    draws {
                        system(FullscreenQuadDrawer::class) {
                            shader = "blur_vertical"
                        }
                    }

                    setup {
                        shaderResources.supplyImage("inputTexture") {
                            source = renderBuffer("bloom_temp")
                            scalingMode = ImageInput.ScalingMode.LINEAR
                        }
                    }

                    outputs {
                        output {
                            name = "fragColor"
                            target = renderBuffer("bloom")
                            blending = PassOutput.BlendMode.OVERWRITE
                        }
                    }
                }

                pass {
                    name = "bloom_blurH2"

                    dependsOn("bloom_blurV")

                    draws {
                        system(FullscreenQuadDrawer::class) {
                            shader = "blur_horizontal"
                        }
                    }

                    setup {
                        shaderResources.supplyImage("inputTexture") {
                            source = renderBuffer("bloom")
                            scalingMode = ImageInput.ScalingMode.LINEAR
                        }
                    }

                    outputs {
                        output {
                            name = "fragColor"
                            target = renderBuffer("bloom_temp2")
                            blending = PassOutput.BlendMode.OVERWRITE
                        }
                    }
                }

                pass {
                    name = "bloom_blurV2"

                    dependsOn("bloom_blurH2")

                    draws {
                        system(FullscreenQuadDrawer::class) {
                            shader = "blur_vertical"
                        }
                    }

                    setup {
                        shaderResources.supplyImage("inputTexture") {
                            source = renderBuffer("bloom_temp2")
                            scalingMode = ImageInput.ScalingMode.LINEAR
                        }
                    }

                    outputs {
                        output {
                            name = "fragColor"
                            target = renderBuffer("bloom2")
                            blending = PassOutput.BlendMode.OVERWRITE
                        }
                    }
                }

                pass {
                    name = "postprocess"

                    dependsOn("forward")
                    dependsOn("bloom_blurV2")

                    setup {
                        shaderResources.supplyImage("shadedBuffer") {
                            source = renderBuffer("shadedBuffer")
                        }

                        shaderResources.supplyImage("bloomBuffer") {
                            source = renderBuffer("bloom")
                            scalingMode = ImageInput.ScalingMode.LINEAR
                        }
                        shaderResources.supplyImage("bloomBuffer2") {
                            source = renderBuffer("bloom2")
                            scalingMode = ImageInput.ScalingMode.LINEAR
                        }
                    }

                    draws {
                        fullscreenQuad()
                    }

                    outputs {
                        output {
                            name = "finalBuffer"
                            blending = PassOutput.BlendMode.OVERWRITE
                        }
                    }
                }

                pass {
                    name = "gui"

                    dependsOn("postprocess")

                    draws {
                        system(GuiDrawer::class)
                    }

                    outputs {
                        output {
                            name = "finalBuffer"

                            //clear = true
                            blending = PassOutput.BlendMode.MIX
                        }
                    }
                }
            }
        }

        renderTask {
            name = "sunShadow"

            finalPassName = "opaque"

            renderBuffers {
                /*renderBuffer {
                    name = "shadowBuffer"

                    format = DEPTH_32
                    size = 1024 by 1024
                }*/
            }

            taskInputs {
                input {
                    name = "shadowBuffer"
                    format = TextureFormat.DEPTH_32
                }
            }

            passes {
                pass {
                    name = "opaque"

                    draws {
                        system(ChunksRenderer::class) {
                            shader = "blockMeshes"
                            materialTag = "opaque"
                        }
                        system(ModelsRenderer::class) {
                            shader = "modelsDeferred"
                            materialTag = "opaque"
                            supportsAnimations = true
                        }
                    }

                    outputs {

                    }

                    depth {
                        enabled = true
                        depthBuffer = taskInput("shadowBuffer")
                        clear = true
                    }
                }
            }
        }
    }
}