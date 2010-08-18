APP_PROJECT_PATH := $(call my-dir)
APP_MODULES      := scsynth PanUGens FilterUGens BinaryOpUGens IOUGens NoiseUGens LFUGens OscUGens MulAddUGens UnaryOpUGens TriggerUGens GendynUGens ReverbUGens \
	MCLDBufferUGens MCLDTreeUGens MCLDTriggeredStatsUGens \
	AY_UGen
# MCLDFFTUGens not yet, pending fft lib availability (not for own use but for fft lib header inclusion)
