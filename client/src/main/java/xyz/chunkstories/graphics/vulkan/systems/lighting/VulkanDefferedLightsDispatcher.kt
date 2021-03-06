package xyz.chunkstories.graphics.vulkan.systems.lighting

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkCommandBuffer
import xyz.chunkstories.api.graphics.rendergraph.SystemExecutionContext
import xyz.chunkstories.api.graphics.representation.PointLight
import xyz.chunkstories.api.graphics.systems.dispatching.DefferedLightsRenderer
import xyz.chunkstories.graphics.common.FaceCullingMode
import xyz.chunkstories.graphics.common.Primitive
import xyz.chunkstories.graphics.common.shaders.compiler.ShaderCompilationParameters
import xyz.chunkstories.graphics.vulkan.Pipeline
import xyz.chunkstories.graphics.vulkan.VulkanGraphicsBackend
import xyz.chunkstories.graphics.vulkan.buffers.VulkanVertexBuffer
import xyz.chunkstories.graphics.vulkan.graph.VulkanPass
import xyz.chunkstories.graphics.vulkan.memory.MemoryUsagePattern
import xyz.chunkstories.graphics.vulkan.resources.DescriptorSetsMegapool
import xyz.chunkstories.graphics.vulkan.shaders.bindShaderResources
import xyz.chunkstories.graphics.vulkan.swapchain.VulkanFrame
import xyz.chunkstories.graphics.vulkan.systems.VulkanDispatchingSystem
import xyz.chunkstories.graphics.vulkan.vertexInputConfiguration

private typealias VkDefferedLightIR = MutableList<PointLight>

class VulkanDefferedLightsDispatcher(backend: VulkanGraphicsBackend) : VulkanDispatchingSystem<PointLight, VkDefferedLightIR>(backend) {

    override val representationName: String
        get() = PointLight::class.java.canonicalName

    inner class Drawer(pass: VulkanPass, drawerInitCode: VulkanDispatchingSystem.Drawer<VkDefferedLightIR>.() -> Unit) : VulkanDispatchingSystem.Drawer<VkDefferedLightIR>(pass), DefferedLightsRenderer {
        override val system: VulkanDispatchingSystem<*, VkDefferedLightIR>
            get() = this@VulkanDefferedLightsDispatcher

        private val vertexBuffer: VulkanVertexBuffer

        init {
            drawerInitCode()

            val vertices = floatArrayOf(-1.0F, -3.0F, 3.0F, 1.0F, -1.0F, 1.0F)
            vertexBuffer = VulkanVertexBuffer(backend, vertices.size * 4L, MemoryUsagePattern.STATIC)

            MemoryStack.stackPush().use {
                val byteBuffer = MemoryStack.stackMalloc(vertices.size * 4)
                vertices.forEach { f -> byteBuffer.putFloat(f) }
                byteBuffer.flip()

                vertexBuffer.upload(byteBuffer)
            }
        }

        val vertexInputConfiguration = vertexInputConfiguration {
            binding {
                binding(0)
                stride(2 * 4)
                inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX)
            }

            attribute {
                binding(0)
                location(program.vertexInputs.find { it.name == "vertexIn" }?.location!!)
                format(VK10.VK_FORMAT_R32G32_SFLOAT)
                offset(0)
            }
        }

        private val program = backend.shaderFactory.createProgram("pointLight", ShaderCompilationParameters(outputs = pass.declaration.outputs))
        private val pipeline = Pipeline(backend, program, pass, vertexInputConfiguration, Primitive.TRIANGLES, FaceCullingMode.DISABLED)

        override fun registerDrawingCommands(frame: VulkanFrame, context: SystemExecutionContext, commandBuffer: VkCommandBuffer, work: VkDefferedLightIR) {
            val bindingContexts = mutableListOf<DescriptorSetsMegapool.ShaderBindingContext>()

            for (light in work) {
                val bindingContext = backend.descriptorMegapool.getBindingContext(pipeline)
                bindingContexts.add(bindingContext)
                VK10.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.handle)
                context.shaderResources.supplyUniformBlock("light", light)
                context.bindShaderResources(bindingContext)

                VK10.vkCmdBindVertexBuffers(commandBuffer, 0, MemoryStack.stackLongs(vertexBuffer.handle), MemoryStack.stackLongs(0))
                bindingContext.preDraw(commandBuffer)

                VK10.vkCmdDraw(commandBuffer, 3, 1, 0, 0)
            }

            frame.recyclingTasks.add {
                bindingContexts.forEach { it.recycle() }
            }
        }

        override fun cleanup() {
            pipeline.cleanup()
            program.cleanup()

            vertexBuffer.cleanup()
        }

    }

    override fun createDrawerForPass(pass: VulkanPass, drawerInitCode: VulkanDispatchingSystem.Drawer<VkDefferedLightIR>.() -> Unit) = Drawer(pass, drawerInitCode)

    override fun sort(representations: Sequence<PointLight>, drawers: List<VulkanDispatchingSystem.Drawer<VkDefferedLightIR>>, workForDrawers: MutableMap<VulkanDispatchingSystem.Drawer<VkDefferedLightIR>, VkDefferedLightIR>) {
        val lists = drawers.associateWith { mutableListOf<PointLight>() }

        for (representation in representations) {
            for (drawer in drawers) {
                lists[drawer]!!.add(representation)
            }
        }

        for (entry in lists) {
            if (entry.value.isNotEmpty()) {
                workForDrawers[entry.key] = entry.value
            }
        }
    }

    override fun cleanup() {

    }
}