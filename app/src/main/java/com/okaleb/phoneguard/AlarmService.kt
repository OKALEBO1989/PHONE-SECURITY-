    private fun startDrum() {
        val id = resources.getIdentifier("african_drum", "raw", packageName)
        if (id == 0) { DrumSynth.start(); return }
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(alarmAttrs())
                setDataSource(
                    this@AlarmService,
                    Uri.parse("android.resource://$packageName/$id")
                )
                isLooping = true
                setVolume(0.7f, 0.7f)
                prepare()
                start()
            }
        } catch (e: Exception) { DrumSynth.start() }
    }
