package br.com.unhasdequecor.ui.tryon

import androidx.lifecycle.ViewModel
import br.com.unhasdequecor.data.vision.nail.NailTryOnPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LiveTryOnViewModel @Inject constructor(
    val pipeline: NailTryOnPipeline,
) : ViewModel()
