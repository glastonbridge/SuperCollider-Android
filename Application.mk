APP_PROJECT_PATH := $(call my-dir)
#APP_OPTIM := debug
APP_OPTIM := release
APP_MODULES      := scsynth PanUGens FilterUGens BinaryOpUGens IOUGens NoiseUGens LFUGens OscUGens MulAddUGens UnaryOpUGens TriggerUGens DelayUGens GendynUGens ReverbUGens \
	MCLDBufferUGens MCLDTreeUGens MCLDTriggeredStatsUgens \
	DynNoiseUGens ChaosUGens GrainUGens PhysicalModelingUGens \
	AY_UGen ML_UGens \
	FFT_UGens MCLDFFTUGens \
        libsndfile 
 
