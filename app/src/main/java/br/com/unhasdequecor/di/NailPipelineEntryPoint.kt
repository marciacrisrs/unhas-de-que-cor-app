package br.com.unhasdequecor.di

import br.com.unhasdequecor.data.vision.nail.NailTryOnPipeline
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
fun interface NailPipelineEntryPoint {
    fun nailTryOnPipeline(): NailTryOnPipeline
}
